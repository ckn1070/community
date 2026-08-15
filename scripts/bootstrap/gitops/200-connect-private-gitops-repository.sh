#!/usr/bin/env bash

# 현재 private application repository를 GitHub App으로 연결하고 Git에 추적된 최초 Application을 적용합니다.
set -euo pipefail

GITOPS_BOOTSTRAP_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly GITOPS_BOOTSTRAP_DIRECTORY
readonly ARGOCD_VERSION="3.5.0"
readonly ARGOCD_IMAGE_DIGEST="sha256:8a14921f7e5dc3408111714f05bff68e337d459653c45f0aea738330d156079b"
readonly ARGOCD_IMAGE="quay.io/argoproj/argocd@${ARGOCD_IMAGE_DIGEST}"
readonly ARGOCD_NAMESPACE="argocd"
readonly ADMIN_KUBECONFIG="/etc/kubernetes/admin.conf"
readonly GITOPS_REPOSITORY_URL="https://github.com/ckn1070/community.git"
readonly TARGET_NAMESPACE="car-community"
readonly PROJECT_NAME="car-community"
readonly APPLICATION_NAME="car-community-stg"
readonly REPOSITORY_SECRET_NAME="car-community-gitops-repository"
readonly MANAGED_BY_LABEL="car-community-gitops-bootstrap"
readonly CORE_MANAGED_BY_LABEL="car-community-argocd-core-bootstrap"
readonly LOCK_PATH="/run/lock/car-community-argocd-gitops.lock"
readonly APPLICATION_PREREQUISITE_OWNER="car-community-application-bootstrap"
readonly APP_PROJECT_MANIFEST="${GITOPS_BOOTSTRAP_DIRECTORY}/manifests/car-community-appproject.yaml"
readonly APPLICATION_MANIFEST="${GITOPS_BOOTSTRAP_DIRECTORY}/manifests/car-community-stg-application.yaml"

print_usage() {
  cat <<'EOF'
사용법:
  env -i HOME="${HOME}" PATH="${PATH}" ssh ubuntu@<EC2> \
    'sudo -n /home/ubuntu/car-community-scripts/bootstrap/gitops/200-connect-private-gitops-repository.sh <GITHUB_APP_ID> <GITHUB_APP_INSTALLATION_ID>' \
    < /local/path/to/github-app-private-key.pem

GitHub App private key는 positional argument나 environment variable로 받지 않고 stdin으로만
읽습니다. GitHub App은 현재 private car-community repository 한 개에 Contents: read-only
권한으로 설치되어 있어야 하며 repository의 main branch에는
deploy/overlays/stg/kustomization.yaml이 있어야 합니다.
EOF
}

fail() {
  echo "오류: $*" >&2
  exit 1
}

parse_arguments() {
  if [[ "$#" -eq 1 && ( "$1" == "--help" || "$1" == "-h" ) ]]; then
    print_usage
    exit 0
  fi
  if [[ "$#" -ne 2 ]]; then
    print_usage >&2
    fail "GitHub App ID와 Installation ID 두 값을 입력해야 합니다."
  fi
  if [[ ! "$1" =~ ^[1-9][0-9]*$ ]]; then
    fail "GitHub App ID는 0으로 시작하지 않는 숫자여야 합니다."
  fi
  if [[ ! "$2" =~ ^[1-9][0-9]*$ ]]; then
    fail "GitHub App Installation ID는 0으로 시작하지 않는 숫자여야 합니다."
  fi
  if [[ -t 0 ]]; then
    fail "GitHub App private key를 terminal에 붙여 넣지 말고 stdin redirect로 전달해야 합니다."
  fi
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    fail "sudo로 실행해야 합니다."
  fi
}

require_commands() {
  local command_name
  local -a required_commands=(base64 cat chmod flock kubectl mktemp openssl rm sleep tr)

  for command_name in "${required_commands[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      fail "필수 command를 찾을 수 없습니다: ${command_name}"
    fi
  done
}

require_gitops_manifests() {
  local manifest_path

  for manifest_path in "${APP_PROJECT_MANIFEST}" "${APPLICATION_MANIFEST}"; do
    if [[ ! -f "${manifest_path}" || ! -r "${manifest_path}" ]]; then
      fail "Git에 추적된 GitOps manifest를 읽을 수 없습니다: ${manifest_path}"
    fi
  done
}

acquire_execution_lock() {
  exec 9>"${LOCK_PATH}"
  if ! flock --nonblock 9; then
    fail "다른 Argo CD GitOps 연결 작업이 진행 중입니다: ${LOCK_PATH}"
  fi
}

kubectl_admin() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=30s "$@"
}

kubectl_admin_long() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=330s "$@"
}

require_core_namespace_owner() {
  local namespace_managed_by

  if ! namespace_managed_by="$(kubectl_admin get "namespace/${ARGOCD_NAMESPACE}" \
      -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')"; then
    fail "argocd namespace owner를 조회하지 못했습니다."
  fi
  if [[ "${namespace_managed_by}" != "${CORE_MANAGED_BY_LABEL}" ]]; then
    fail "Core bootstrap이 소유하지 않은 argocd namespace에는 GitOps resource를 적용하지 않습니다."
  fi
}

require_argocd_component_image() {
  local resource="$1"
  local image
  local image_list

  if ! image_list="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" "${resource}" \
      -o jsonpath='{range .spec.template.spec.containers[*]}{.image}{"\n"}{end}{range .spec.template.spec.initContainers[*]}{.image}{"\n"}{end}')"; then
    fail "Argo CD component image 구성을 조회하지 못했습니다: ${resource}"
  fi
  if [[ -z "${image_list}" ]]; then
    fail "Argo CD component image 구성을 확인하지 못했습니다: ${resource}"
  fi
  while IFS= read -r image; do
    if [[ "${image}" != "${ARGOCD_IMAGE}" ]]; then
      fail "Argo CD Core v${ARGOCD_VERSION} 설치 뒤에만 repository를 연결할 수 있습니다: ${resource} ${image}"
    fi
  done <<<"${image_list}"
}

require_ready_argocd_core() {
  if [[ ! -r "${ADMIN_KUBECONFIG}" ]]; then
    fail "Kubernetes admin kubeconfig를 읽을 수 없습니다: ${ADMIN_KUBECONFIG}"
  fi
  if [[ "$(kubectl_admin get --raw=/readyz 2>/dev/null)" != "ok" ]]; then
    fail "Kubernetes API server readyz를 확인하지 못했습니다."
  fi
  require_core_namespace_owner
  kubectl_admin get customresourcedefinition/applications.argoproj.io >/dev/null
  kubectl_admin get customresourcedefinition/appprojects.argoproj.io >/dev/null
  kubectl_admin get -n "${ARGOCD_NAMESPACE}" statefulset/argocd-application-controller >/dev/null
  kubectl_admin get -n "${ARGOCD_NAMESPACE}" deployment/argocd-repo-server >/dev/null
  require_argocd_component_image statefulset/argocd-application-controller
  require_argocd_component_image deployment/argocd-repo-server
}

require_application_namespace() {
  local namespace_owner

  if ! namespace_owner="$(kubectl_admin get "namespace/${TARGET_NAMESPACE}" \
      -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')"; then
    fail "Application namespace prerequisite를 조회하지 못했습니다."
  fi
  if [[ "${namespace_owner}" != "${APPLICATION_PREREQUISITE_OWNER}" ]]; then
    fail "Application namespace는 application bootstrap이 먼저 생성해야 합니다."
  fi
}

require_managed_or_absent() {
  local resource="$1"
  local resource_name
  local managed_by

  if ! resource_name="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" \
      "${resource}" --ignore-not-found -o name)"; then
    fail "기존 GitOps resource 상태를 조회하지 못했습니다: ${resource}"
  fi
  if [[ -z "${resource_name}" ]]; then
    return
  fi

  if ! managed_by="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" "${resource}" \
      -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')"; then
    fail "기존 resource owner를 조회하지 못했습니다: ${resource}"
  fi
  if [[ "${managed_by}" != "${MANAGED_BY_LABEL}" ]]; then
    fail "다른 owner의 resource를 덮어쓰지 않습니다: ${resource}"
  fi
}

require_initial_connection_incomplete() {
  local application_state
  local resource
  local resource_name

  for resource in \
      "secret/${REPOSITORY_SECRET_NAME}" \
      "appproject/${PROJECT_NAME}" \
      "application/${APPLICATION_NAME}"; do
    if ! resource_name="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" \
        "${resource}" --ignore-not-found -o name)"; then
      fail "초기 GitOps resource 상태를 조회하지 못했습니다: ${resource}"
    fi
    if [[ -z "${resource_name}" ]]; then
      return
    fi
  done

  if ! application_state="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" \
      "application/${APPLICATION_NAME}" \
      -o jsonpath='{.status.sync.status}{"|"}{.status.health.status}')"; then
    fail "기존 Application 완료 상태를 조회하지 못했습니다."
  fi
  if [[ "${application_state}" == "Synced|Healthy" ]]; then
    fail "최초 repository 연결이 이미 완료됐습니다. credential rotation은 operations/gitops/rotate-private-gitops-repository-credential.sh를 사용합니다."
  fi
}

require_managed_existing() {
  local resource="$1"
  local resource_name
  local managed_by

  if ! resource_name="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" \
      "${resource}" --ignore-not-found -o name)"; then
    fail "기존 GitOps resource 상태를 조회하지 못했습니다: ${resource}"
  fi
  if [[ -z "${resource_name}" ]]; then
    fail "credential rotation 전에 기존 resource가 필요합니다: ${resource}"
  fi
  if ! managed_by="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" "${resource}" \
      -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')"; then
    fail "기존 resource owner를 조회하지 못했습니다: ${resource}"
  fi
  if [[ "${managed_by}" != "${MANAGED_BY_LABEL}" ]]; then
    fail "다른 owner의 resource credential을 변경하지 않습니다: ${resource}"
  fi
}

run_preflight_checks() {
  echo "[사전 확인] Argo CD Core와 기존 GitOps resource owner를 확인합니다."
  require_root
  require_commands
  require_gitops_manifests
  acquire_execution_lock
  require_ready_argocd_core
  require_application_namespace
  require_managed_or_absent "secret/${REPOSITORY_SECRET_NAME}"
  require_managed_or_absent "appproject/${PROJECT_NAME}"
  require_managed_or_absent "application/${APPLICATION_NAME}"
  require_initial_connection_incomplete
}

create_secret_work_directory() {
  local work_directory

  work_directory="$(mktemp -d /run/car-community-argocd-github-app.XXXXXX)"
  chmod 0700 "${work_directory}"
  printf '%s\n' "${work_directory}"
}

cleanup_secret_work_directory() {
  local work_directory="$1"

  if [[ -n "${work_directory}" && -d "${work_directory}" ]]; then
    rm -rf -- "${work_directory}"
  fi
}

read_and_verify_private_key() {
  local private_key_path="$1"

  umask 077
  cat >"${private_key_path}"
  chmod 0600 "${private_key_path}"
  if [[ ! -s "${private_key_path}" ]]; then
    fail "GitHub App private key가 비어 있습니다."
  fi
  if ! openssl pkey -in "${private_key_path}" -noout -check >/dev/null 2>&1; then
    fail "GitHub App private key를 유효한 PEM private key로 확인하지 못했습니다."
  fi
}

apply_repository_secret() {
  local github_app_id="$1"
  local github_app_installation_id="$2"
  local private_key_path="$3"

  kubectl_admin create secret generic "${REPOSITORY_SECRET_NAME}" \
    -n "${ARGOCD_NAMESPACE}" \
    --from-literal=type=git \
    --from-literal="url=${GITOPS_REPOSITORY_URL}" \
    --from-literal="project=${PROJECT_NAME}" \
    --from-literal="githubAppID=${github_app_id}" \
    --from-literal="githubAppInstallationID=${github_app_installation_id}" \
    --from-file="githubAppPrivateKey=${private_key_path}" \
    --dry-run=client \
    -o yaml \
    | kubectl_admin label --local -f - \
      "argocd.argoproj.io/secret-type=repository" \
      "app.kubernetes.io/managed-by=${MANAGED_BY_LABEL}" \
      -o yaml \
    | kubectl_admin apply --server-side --field-manager=car-community-bootstrap -f -
}

render_application_controller_secret_patch() {
  local secret_resource_version="$1"
  local destination_path="$2"
  local resource_version_token

  resource_version_token="$(printf '%s' "${secret_resource_version}" | base64 | tr -d '\n')"
  cat >"${destination_path}" <<EOF
spec:
  template:
    metadata:
      annotations:
        car-community-repository-secret-resource-version: "${resource_version_token}"
EOF
}

restart_application_controller_for_current_secret() {
  local work_directory="$1"
  local secret_resource_version
  local patch_path="${work_directory}/application-controller-secret-patch.yaml"

  # Controller가 이전 Secret informer cache로 reconcile하는 rotation 오판을 막습니다.
  if ! secret_resource_version="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" \
      "secret/${REPOSITORY_SECRET_NAME}" -o jsonpath='{.metadata.resourceVersion}')"; then
    fail "Repository Secret resourceVersion을 조회하지 못했습니다."
  fi
  if [[ -z "${secret_resource_version}" ]]; then
    fail "Repository Secret resourceVersion이 비어 있습니다."
  fi

  render_application_controller_secret_patch "${secret_resource_version}" "${patch_path}"
  kubectl_admin patch -n "${ARGOCD_NAMESPACE}" \
    statefulset/argocd-application-controller \
    --type=merge \
    --patch-file="${patch_path}"
  kubectl_admin_long rollout status -n "${ARGOCD_NAMESPACE}" \
    statefulset/argocd-application-controller \
    --timeout=300s
}

apply_gitops_resources() {
  kubectl_admin apply --server-side --field-manager=car-community-bootstrap \
    -f "${APP_PROJECT_MANIFEST}" \
    -f "${APPLICATION_MANIFEST}"
}

current_application_reconciled_at() {
  local reconciled_at

  if ! reconciled_at="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" \
      "application/${APPLICATION_NAME}" --ignore-not-found \
      -o jsonpath='{.status.reconciledAt}')"; then
    fail "기존 Argo CD Application reconcile 상태를 조회하지 못했습니다."
  fi
  printf '%s\n' "${reconciled_at}"
}

application_reconciliation_is_fresh() {
  local previous_reconciled_at="$1"
  local refresh_annotation="$2"
  local current_reconciled_at="$3"

  [[ -z "${refresh_annotation}" \
    && -n "${current_reconciled_at}" \
    && "${current_reconciled_at}" != "${previous_reconciled_at}" ]]
}

application_reconcile_state() {
  local request_timeout_seconds="$1"

  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl \
    --request-timeout="${request_timeout_seconds}s" \
    get -n "${ARGOCD_NAMESPACE}" "application/${APPLICATION_NAME}" \
    -o jsonpath='{.metadata.annotations.argocd\.argoproj\.io/refresh}{"|"}{.status.reconciledAt}'
}

force_and_wait_for_fresh_reconciliation() {
  local previous_reconciled_at="$1"
  local deadline=$((SECONDS + 300))
  local remaining_seconds
  local request_timeout_seconds
  local sleep_seconds
  local reconcile_state
  local refresh_annotation
  local current_reconciled_at

  # reconciledAt은 초 단위이므로 기존 상태와 같은 초의 갱신을 새 결과로 오판하지 않습니다.
  if [[ -n "${previous_reconciled_at}" ]]; then
    sleep 1
  fi

  kubectl_admin annotate -n "${ARGOCD_NAMESPACE}" \
    "application/${APPLICATION_NAME}" \
    argocd.argoproj.io/refresh=hard \
    --overwrite

  while (( SECONDS < deadline )); do
    remaining_seconds=$((deadline - SECONDS))
    request_timeout_seconds=30
    if (( remaining_seconds < request_timeout_seconds )); then
      request_timeout_seconds="${remaining_seconds}"
    fi

    if reconcile_state="$(application_reconcile_state "${request_timeout_seconds}")"; then
      IFS='|' read -r refresh_annotation current_reconciled_at <<<"${reconcile_state}"
      if application_reconciliation_is_fresh \
          "${previous_reconciled_at}" \
          "${refresh_annotation}" \
          "${current_reconciled_at}"; then
        return
      fi
    else
      echo "경고: Argo CD Application의 새 reconcile 상태를 조회하지 못해 대기 시간 안에서 재시도합니다." >&2
    fi
    remaining_seconds=$((deadline - SECONDS))
    if (( remaining_seconds <= 0 )); then
      break
    fi
    sleep_seconds=2
    if (( remaining_seconds < sleep_seconds )); then
      sleep_seconds="${remaining_seconds}"
    fi
    sleep "${sleep_seconds}"
  done

  fail "새 repository credential 적용 뒤 Application reconcile이 300초 안에 완료되지 않았습니다."
}

wait_for_application() {
  kubectl_admin_long wait -n "${ARGOCD_NAMESPACE}" \
    --for=jsonpath='{.status.sync.status}'=Synced \
    "application/${APPLICATION_NAME}" \
    --timeout=300s
  kubectl_admin_long wait -n "${ARGOCD_NAMESPACE}" \
    --for=jsonpath='{.status.health.status}'=Healthy \
    "application/${APPLICATION_NAME}" \
    --timeout=300s
}

verify_postconditions() {
  local repository_url
  local project_name
  local application_conditions

  repository_url="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" \
    "secret/${REPOSITORY_SECRET_NAME}" -o jsonpath='{.data.url}' | base64 --decode)"
  project_name="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" \
    "secret/${REPOSITORY_SECRET_NAME}" -o jsonpath='{.data.project}' | base64 --decode)"
  if [[ "${repository_url}" != "${GITOPS_REPOSITORY_URL}" || "${project_name}" != "${PROJECT_NAME}" ]]; then
    fail "Repository credential의 URL 또는 project scope가 목표와 다릅니다."
  fi

  application_conditions="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" \
    "application/${APPLICATION_NAME}" \
    -o jsonpath='{range .status.conditions[*]}{.type}{": "}{.message}{"\n"}{end}')"
  if [[ -n "${application_conditions}" ]]; then
    printf '%s\n' "${application_conditions}" >&2
    fail "Argo CD Application condition이 남아 있습니다."
  fi

  echo "[완료] 현재 private repository와 ${APPLICATION_NAME} Application이 Synced·Healthy입니다."
}

main() {
  local github_app_id="${1:-}"
  local github_app_installation_id="${2:-}"
  local work_directory=""
  local private_key_path
  local previous_reconciled_at

  parse_arguments "$@"
  run_preflight_checks
  previous_reconciled_at="$(current_application_reconciled_at)"
  work_directory="$(create_secret_work_directory)"
  trap 'cleanup_secret_work_directory "${work_directory:-}"' EXIT
  private_key_path="${work_directory}/github-app-private-key.pem"

  echo "[입력] GitHub App private key를 stdin에서 임시 runtime 경로로 읽습니다."
  read_and_verify_private_key "${private_key_path}"
  echo "[적용] Project에 한정된 private repository credential을 구성합니다."
  apply_repository_secret "${github_app_id}" "${github_app_installation_id}" "${private_key_path}"
  rm -f -- "${private_key_path}"
  echo "[대기] 현재 repository credential을 읽는 새 application-controller rollout을 확인합니다."
  restart_application_controller_for_current_secret "${work_directory}"
  echo "[적용] Git에 추적된 AppProject와 staging Application manifest를 적용합니다."
  apply_gitops_resources
  echo "[대기] 변경된 credential을 사용한 새 reconcile 완료를 확인합니다."
  force_and_wait_for_fresh_reconciliation "${previous_reconciled_at}"
  echo "[대기] GitOps 동기화와 workload health를 확인합니다."
  wait_for_application
  verify_postconditions
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
