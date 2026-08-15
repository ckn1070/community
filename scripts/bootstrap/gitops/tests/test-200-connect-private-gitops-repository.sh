#!/usr/bin/env bash

# 현재 private repository 연결 script의 secret·권한 경계·Application 계약을 검증합니다.
set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly TARGET_SCRIPT="${TEST_DIRECTORY}/../200-connect-private-gitops-repository.sh"

# shellcheck source=../200-connect-private-gitops-repository.sh
source "${TARGET_SCRIPT}"

assert_equals() {
  local expected="$1"
  local actual="$2"
  local message="$3"

  if [[ "${actual}" != "${expected}" ]]; then
    printf 'FAIL: %s\nexpected: %s\nactual: %s\n' "${message}" "${expected}" "${actual}" >&2
    exit 1
  fi
}

assert_file_contains() {
  local path="$1"
  local expected="$2"
  local message="$3"

  if ! grep -Fqx -- "${expected}" "${path}"; then
    printf 'FAIL: %s\nmissing: %s\n' "${message}" "${expected}" >&2
    exit 1
  fi
}

assert_file_excludes() {
  local path="$1"
  local unexpected="$2"
  local message="$3"

  if grep -Fq -- "${unexpected}" "${path}"; then
    printf 'FAIL: %s\nunexpected: %s\n' "${message}" "${unexpected}" >&2
    exit 1
  fi
}

test_tracked_manifests_define_restricted_project_and_application() {
  local project_path="${APP_PROJECT_MANIFEST}"
  local application_path="${APPLICATION_MANIFEST}"

  assert_file_contains "${project_path}" \
    '    - https://github.com/ckn1070/community.git' \
    "AppProject source를 현재 private application repository로 제한해야 합니다."
  assert_file_contains "${project_path}" '  clusterResourceWhitelist: []' \
    "Application bootstrap 소유 Namespace를 Argo CD가 생성하면 안 됩니다."
  assert_file_contains "${project_path}" '      kind: ConfigMap' \
    "Application 공개 설정 ConfigMap을 허용해야 합니다."
  assert_file_contains "${project_path}" '      kind: Service' \
    "Runtime Service를 허용해야 합니다."
  assert_file_contains "${project_path}" '      kind: Deployment' \
    "Runtime Deployment를 허용해야 합니다."
  assert_file_contains "${project_path}" '      kind: Job' \
    "PreSync Job을 허용해야 합니다."
  assert_file_contains "${application_path}" '    targetRevision: main' \
    "승인·merge된 main branch를 목표 상태로 사용해야 합니다."
  assert_file_contains "${application_path}" '    path: deploy/overlays/stg' \
    "현재 repository 내부의 staging overlay 경로를 직접 읽어야 합니다."
  assert_file_contains "${application_path}" '      enabled: true' \
    "자동 동기화를 명시적으로 활성화해야 합니다."
  assert_file_contains "${application_path}" '      prune: true' \
    "Git에서 제거된 resource를 자동 정리해야 합니다."
  assert_file_contains "${application_path}" '      selfHeal: true' \
    "Live drift를 Git 목표 상태로 복구해야 합니다."
  assert_file_contains "${application_path}" '      allowEmpty: false' \
    "빈 목표 상태의 전체 삭제를 차단해야 합니다."
  assert_file_excludes "${application_path}" '/spec/replicas' \
    "현재 HPA가 없으므로 replica field 예외를 선반영하면 안 됩니다."
  assert_file_contains "${application_path}" \
    '        - /spec/template/metadata/annotations/car-community.io~1notification-secret-resource-version' \
    "Notification operation의 rollout annotation을 Argo CD diff에서 제외해야 합니다."
  assert_file_contains "${application_path}" '      - RespectIgnoreDifferences=true' \
    "동기화에도 notification rollout annotation 제외를 적용해야 합니다."
  assert_file_excludes "${application_path}" 'CreateNamespace=true' \
    "Argo CD가 application Namespace를 생성하면 안 됩니다."
}

test_applies_tracked_gitops_manifests() (
  local test_root="$1"
  local action_log="${test_root}/manifest-apply-actions"

  kubectl_admin() { printf '%s\n' "$*" >>"${action_log}"; }

  apply_gitops_resources

  assert_equals \
    "apply --server-side --field-manager=car-community-bootstrap -f ${APP_PROJECT_MANIFEST} -f ${APPLICATION_MANIFEST}" \
    "$(<"${action_log}")" \
    "GitOps bootstrap은 Shell에서 YAML을 만들지 않고 Git에 추적된 manifest를 적용해야 합니다."
)

test_requires_application_namespace() (
  kubectl_admin() {
    printf '%s\n' car-community-application-bootstrap
  }

  require_application_namespace
)

test_rejects_foreign_application_namespace() (
  local execution_status

  kubectl_admin() {
    printf '%s\n' another-owner
  }

  set +e
  (require_application_namespace >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 owner의 application Namespace를 target으로 사용하면 안 됩니다." >&2
    exit 1
  fi
)

test_rejects_foreign_resource_owner() (
  local execution_status

  kubectl_admin() {
    if [[ "$*" == *'-o name'* ]]; then
      printf '%s\n' 'secret/car-community-gitops-repository'
    else
      printf '%s\n' 'another-owner'
    fi
  }

  set +e
  (require_managed_or_absent \
    secret/car-community-gitops-repository >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 owner의 Argo CD resource를 덮어쓰면 안 됩니다." >&2
    exit 1
  fi
)

test_rejects_foreign_core_namespace_owner() (
  local execution_status

  kubectl_admin() { printf '%s\n' another-owner; }

  set +e
  (require_core_namespace_owner >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 owner의 argocd namespace를 Core bootstrap 결과로 인정하면 안 됩니다." >&2
    exit 1
  fi
)

test_rejects_other_application_controller_image() (
  local execution_status

  kubectl_admin() { printf '%s\n' 'quay.io/argoproj/argocd@sha256:wrong'; }

  set +e
  (require_argocd_component_image \
    statefulset/argocd-application-controller >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 image의 application-controller를 credential reader로 재시작하면 안 됩니다." >&2
    exit 1
  fi
)

test_resource_owner_lookup_failure_is_rejected() (
  local execution_status

  kubectl_admin() { return 78; }

  set +e
  (require_managed_or_absent secret/car-community-gitops-repository >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 기존 GitOps resource 조회 실패를 absent로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_rejects_completed_initial_connection() (
  local execution_status

  kubectl_admin() {
    if [[ "$*" == *'-o name'* ]]; then
      printf '%s\n' "${1#*/}/existing"
      return
    fi
    printf '%s\n' 'Synced|Healthy'
  }

  set +e
  (require_initial_connection_incomplete >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 완료된 최초 연결을 bootstrap에서 credential rotation으로 재사용하면 안 됩니다." >&2
    exit 1
  fi
)

test_repository_secret_does_not_log_private_key() (
  local test_root="$1"
  local private_key_path="${test_root}/private-key.pem"
  local action_log="${test_root}/kubectl-arguments"
  local private_key_content='PRIVATE-KEY-MUST-NOT-APPEAR-IN-ARGV'

  printf '%s\n' "${private_key_content}" >"${private_key_path}"
  kubectl_admin() {
    printf '%s\n' "$*" >>"${action_log}"
    if [[ "$1" == "create" ]]; then
      printf '%s\n' 'apiVersion: v1' 'kind: Secret'
    else
      cat >/dev/null
    fi
  }

  apply_repository_secret 123 456 "${private_key_path}"

  grep -Fq -- '--from-file=githubAppPrivateKey=' "${action_log}"
  grep -Fq -- '--from-literal=url=https://github.com/ckn1070/community.git' "${action_log}"
  grep -Fq -- '--from-literal=project=car-community' "${action_log}"
  if grep -Fq -- "${private_key_content}" "${action_log}"; then
    echo "FAIL: GitHub App private key가 command argument나 log에 나타나면 안 됩니다." >&2
    exit 1
  fi
)

test_repository_secret_manifest_uses_supported_kubectl_client() (
  local test_root="$1"
  local private_key_path="${test_root}/kubectl-client-private-key.pem"
  local applied_manifest="${test_root}/kubectl-client-secret.yaml"

  printf '%s\n' 'test-private-key' >"${private_key_path}"
  kubectl_admin() {
    if [[ "$1" == "apply" ]]; then
      cat >"${applied_manifest}"
      return
    fi
    KUBECONFIG=/does/not-exist command kubectl --request-timeout=30s "$@"
  }

  apply_repository_secret 123 456 "${private_key_path}"

  assert_file_contains "${applied_manifest}" \
    '    argocd.argoproj.io/secret-type: repository' \
    "실제 kubectl client가 Repository Secret type label을 포함한 manifest를 만들어야 합니다."
  assert_file_contains "${applied_manifest}" \
    '    app.kubernetes.io/managed-by: car-community-gitops-bootstrap' \
    "실제 kubectl client가 bootstrap owner label을 포함한 manifest를 만들어야 합니다."
)

test_restarts_application_controller_after_credential_apply() (
  local test_root="$1"
  local action_log="${test_root}/restart-actions"
  local patch_path="${test_root}/application-controller-secret-patch.yaml"

  kubectl_admin() {
    if [[ "$*" == *'secret/car-community-gitops-repository'* ]]; then
      printf '%s\n' '12345'
    else
      printf 'short:%s\n' "$*" >>"${action_log}"
    fi
  }
  kubectl_admin_long() { printf 'long:%s\n' "$*" >>"${action_log}"; }

  restart_application_controller_for_current_secret "${test_root}"

  grep -Fq -- \
    "short:patch -n argocd statefulset/argocd-application-controller --type=merge --patch-file=${patch_path}" \
    "${action_log}"
  grep -Fq -- \
    'long:rollout status -n argocd statefulset/argocd-application-controller --timeout=300s' \
    "${action_log}"
  grep -Fq -- 'car-community-repository-secret-resource-version: "MTIzNDU="' "${patch_path}"
)

test_secret_resource_version_changes_controller_revision() {
  local test_root="$1"
  local first_patch="${test_root}/first-controller-patch.yaml"
  local second_patch="${test_root}/second-controller-patch.yaml"

  render_application_controller_secret_patch '101' "${first_patch}"
  render_application_controller_secret_patch '102' "${second_patch}"

  if [[ "$(<"${first_patch}")" == "$(<"${second_patch}")" ]]; then
    echo "FAIL: 다른 Secret resourceVersion은 같은 초에도 다른 Pod template revision을 만들어야 합니다." >&2
    exit 1
  fi
}

test_invalid_private_key_is_rejected() (
  local test_root="$1"
  local destination_path="${test_root}/invalid-private-key.pem"
  local execution_status

  openssl() { return 1; }

  set +e
  (printf '%s\n' 'not a private key' | read_and_verify_private_key \
    "${destination_path}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 유효하지 않은 GitHub App private key를 Secret에 넣으면 안 됩니다." >&2
    exit 1
  fi
)

test_rejects_stale_application_status() {
  if application_reconciliation_is_fresh \
      '2026-08-08T08:00:00Z' \
      '' \
      '2026-08-08T08:00:00Z'; then
    echo "FAIL: 이전 credential로 만든 reconcile 시각을 새 성공으로 인정하면 안 됩니다." >&2
    exit 1
  fi

  if application_reconciliation_is_fresh \
      '2026-08-08T08:00:00Z' \
      'hard' \
      '2026-08-08T08:00:01Z'; then
    echo "FAIL: hard refresh annotation을 아직 처리하지 않은 상태를 성공으로 인정하면 안 됩니다." >&2
    exit 1
  fi

  application_reconciliation_is_fresh \
    '2026-08-08T08:00:00Z' \
    '' \
    '2026-08-08T08:00:01Z'
}

test_retries_transient_application_state_query_failure() (
  local test_root="$1"
  local query_count_path="${test_root}/reconcile-query-count"
  local output_path="${test_root}/transient-reconcile-output"

  printf '%s\n' 0 >"${query_count_path}"
  kubectl_admin() { :; }
  application_reconcile_state() {
    local query_count
    query_count="$(<"${query_count_path}")"
    query_count=$((query_count + 1))
    printf '%s\n' "${query_count}" >"${query_count_path}"
    if [[ "${query_count}" -eq 1 ]]; then
      return 75
    fi
    printf '%s\n' '|2026-08-08T08:00:01Z'
  }
  sleep() { :; }

  force_and_wait_for_fresh_reconciliation \
    '2026-08-08T08:00:00Z' >"${output_path}" 2>&1

  assert_equals 2 "$(<"${query_count_path}")" \
    "일시적인 Application 상태 조회 실패 뒤에는 대기 시간 안에서 다시 조회해야 합니다."
  assert_file_contains "${output_path}" \
    "경고: Argo CD Application의 새 reconcile 상태를 조회하지 못해 대기 시간 안에서 재시도합니다." \
    "일시적인 Application 상태 조회 실패의 재시도를 운영자에게 알려야 합니다."
)

test_fails_after_persistent_application_state_query_failure() (
  local test_root="$1"
  local query_count_path="${test_root}/persistent-reconcile-query-count"
  local elapsed_seconds_path="${test_root}/persistent-reconcile-elapsed-seconds"
  local output_path="${test_root}/persistent-reconcile-output"
  local execution_status

  printf '%s\n' 0 >"${query_count_path}"
  printf '%s\n' 0 >"${elapsed_seconds_path}"
  kubectl_admin() { :; }
  application_reconcile_state() {
    local query_count
    query_count="$(<"${query_count_path}")"
    printf '%s\n' "$((query_count + 1))" >"${query_count_path}"
    return 75
  }
  sleep() {
    SECONDS=$((SECONDS + $1))
    printf '%s\n' "${SECONDS}" >"${elapsed_seconds_path}"
  }

  SECONDS=0
  set +e
  (force_and_wait_for_fresh_reconciliation '' >"${output_path}" 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: Application 상태 조회가 계속 실패하면 제한 시간 뒤 실패해야 합니다." >&2
    exit 1
  fi
  if (( $(<"${query_count_path}") <= 1 )); then
    echo "FAIL: Application 상태 조회가 계속 실패해도 한 번으로 중단하지 않고 재시도해야 합니다." >&2
    exit 1
  fi
  if (( $(<"${elapsed_seconds_path}") < 300 )); then
    echo "FAIL: Application 상태 조회는 기존 300초 deadline까지 재시도해야 합니다." >&2
    exit 1
  fi
  assert_file_contains "${output_path}" \
    "경고: Argo CD Application의 새 reconcile 상태를 조회하지 못해 대기 시간 안에서 재시도합니다." \
    "지속적인 Application 상태 조회 실패 중에도 재시도를 알려야 합니다."
  assert_file_contains "${output_path}" \
    "오류: 새 repository credential 적용 뒤 Application reconcile이 300초 안에 완료되지 않았습니다." \
    "지속적인 Application 상태 조회 실패는 기존 timeout 오류로 종료해야 합니다."
)

test_main_preserves_connection_order() (
  local test_root="$1"
  local action_log="${test_root}/actions"
  local fixture_work_directory="${test_root}/work"

  parse_arguments() { :; }
  run_preflight_checks() { printf '%s\n' preflight >>"${action_log}"; }
  current_application_reconciled_at() {
    printf '%s\n' baseline >>"${action_log}"
    printf '%s\n' '2026-08-08T08:00:00Z'
  }
  create_secret_work_directory() {
    mkdir -p "${fixture_work_directory}"
    printf '%s\n' "${fixture_work_directory}"
  }
  read_and_verify_private_key() { printf '%s\n' read-key >>"${action_log}"; }
  apply_repository_secret() { printf '%s\n' apply-secret >>"${action_log}"; }
  restart_application_controller_for_current_secret() {
    printf '%s\n' restart-application-controller >>"${action_log}"
  }
  apply_gitops_resources() { printf '%s\n' apply-resources >>"${action_log}"; }
  force_and_wait_for_fresh_reconciliation() {
    printf 'fresh-reconcile:%s\n' "$1" >>"${action_log}"
  }
  wait_for_application() { printf '%s\n' wait >>"${action_log}"; }
  verify_postconditions() { printf '%s\n' verify >>"${action_log}"; }

  main 123 456 </dev/null >/dev/null

  assert_equals \
    $'preflight\nbaseline\nread-key\napply-secret\nrestart-application-controller\napply-resources\nfresh-reconcile:2026-08-08T08:00:00Z\nwait\nverify' \
    "$(<"${action_log}")" \
    "Preflight 뒤 secret, project/application, reconcile postcondition 순서로 진행해야 합니다."
)

run_tests() {
  local test_root
  test_root="$(mktemp -d)"
  trap '[[ -z "${test_root:-}" ]] || rm -rf -- "${test_root}"' EXIT

  test_tracked_manifests_define_restricted_project_and_application
  test_applies_tracked_gitops_manifests "${test_root}"
  test_requires_application_namespace
  test_rejects_foreign_application_namespace
  test_rejects_foreign_resource_owner
  test_rejects_foreign_core_namespace_owner
  test_rejects_other_application_controller_image
  test_resource_owner_lookup_failure_is_rejected
  test_rejects_completed_initial_connection
  test_repository_secret_does_not_log_private_key "${test_root}"
  test_repository_secret_manifest_uses_supported_kubectl_client "${test_root}"
  test_restarts_application_controller_after_credential_apply "${test_root}"
  test_secret_resource_version_changes_controller_revision "${test_root}"
  test_invalid_private_key_is_rejected "${test_root}"
  test_rejects_stale_application_status
  test_retries_transient_application_state_query_failure "${test_root}"
  test_fails_after_persistent_application_state_query_failure "${test_root}"
  test_main_preserves_connection_order "${test_root}"

  echo "PASS: 200-connect-private-gitops-repository.sh contract tests"
}

run_tests
