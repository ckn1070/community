#!/usr/bin/env bash

set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly TARGET_SCRIPT="${TEST_DIRECTORY}/../reconcile-community-secret.sh"

TEST_ROOT=""

# shellcheck source=../reconcile-community-secret.sh
source "${TARGET_SCRIPT}"

fail_test() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  local message="$3"

  [[ "${actual}" == "${expected}" ]] \
    || fail_test "${message} (expected=${expected}, actual=${actual})"
}

test_input_and_validation_contract() {
  local status

  read_community_input < <(printf '%s\0' 'community-secret-with-at-least-32-bytes')
  validate_community_input

  COMMUNITY_CURSOR_HMAC_SECRET='too-short'
  set +e
  (validate_community_input) >/dev/null 2>&1
  status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "32 bytes 미만 값을 거부해야 합니다."
}

test_allows_missing_owned_secret_and_rejects_foreign_schema() {
  local status

  kubectl_cluster() { printf '%s\n' car-community-application-bootstrap; }
  kubectl_admin() { printf '%s\n' argocd; }
  application_secret_community_state() { :; }
  require_runtime_and_secret_boundary

  application_secret_community_state() {
    printf '%s\n' 'another-owner|Opaque|1|encoded|100'
  }
  set +e
  (require_runtime_and_secret_boundary) >/dev/null 2>&1
  status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "다른 owner의 기존 Secret을 거부해야 합니다."
}

test_rejects_auth_hmac_reuse_without_printing_values() {
  local secret_value='community-secret-with-at-least-32-bytes'
  local encoded_value
  local status

  COMMUNITY_CURSOR_HMAC_SECRET="${secret_value}"
  encoded_value="$(application_secret_base64_no_wrap "${secret_value}")"
  # Shared helper가 이 test double을 간접 호출합니다.
  # shellcheck disable=SC2329
  kubectl_admin() {
    printf '%s\n' "car-community-application-bootstrap|Opaque|2|${encoded_value}|another-encoded"
  }
  set +e
  status=0
  (require_distinct_from_auth_hmac_secrets) >/dev/null 2>&1 || status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "Auth HMAC 재사용을 거부해야 합니다."

  kubectl_admin() {
    printf '%s\n' "car-community-application-bootstrap|Opaque|2|another-encoded|${encoded_value}"
  }
  set +e
  status=0
  (require_distinct_from_auth_hmac_secrets) >/dev/null 2>&1 || status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "두 번째 Auth HMAC 재사용도 거부해야 합니다."
}

test_apply_streams_secret_without_plaintext_arguments() (
  local argument_log="$1"
  local manifest_log="${argument_log}.manifest"
  local secret_value='SECRET-MUST-NOT-APPEAR-IN-KUBECTL-ARGV-0123456789'

  # Shared helper가 이 test double을 간접 호출합니다.
  # shellcheck disable=SC2329
  kubectl_admin() {
    printf '%s\n' "$*" >>"${argument_log}"
    cat >>"${manifest_log}"
  }

  application_secret_apply_community \
    car-community-application-bootstrap \
    car-community \
    car-community-application-bootstrap \
    "${secret_value}"

  ! grep -Fq "${secret_value}" "${argument_log}" \
    || fail_test "Secret 값을 kubectl argument에 넣으면 안 됩니다."
  grep -Fq -- '--server-side' "${argument_log}" \
    || fail_test "Server-side apply를 사용해야 합니다."
  grep -Fq 'COMMUNITY_CURSOR_HMAC_SECRET:' "${manifest_log}" \
    || fail_test "Community cursor key schema를 stdin manifest로 전달해야 합니다."
  ! grep -Fq "${secret_value}" "${manifest_log}" \
    || fail_test "Secret manifest에 plaintext를 쓰면 안 됩니다."
)

test_creates_secret_before_rollout_and_resumes() (
  local action_log="$1"
  local annotation_path="${action_log}.annotation"

  COMMUNITY_CURSOR_HMAC_SECRET='community-secret-with-at-least-32-bytes'
  printf '%s\n' 'old' >"${annotation_path}"
  application_secret_base64_no_wrap() { printf '%s\n' encoded-community; }
  application_secret_community_state() {
    if [[ -e "${action_log}.applied" ]]; then
      printf '%s\n' 'car-community-application-bootstrap|Opaque|1|encoded-community|301'
    fi
  }
  application_secret_apply_community() {
    printf '%s\n' apply >>"${action_log}"
    : >"${action_log}.applied"
  }
  application_secret_verify_community() { printf '%s\n' verify >>"${action_log}"; }
  kubectl_admin() {
    printf '%s\n' "$*" >>"${action_log}"
    if [[ "$*" == *'get deployment/car-community -o jsonpath='* ]]; then
      cat "${annotation_path}"
    elif [[ "$*" == *'patch deployment/car-community'* ]]; then
      printf '%s\n' '301' >"${annotation_path}"
    fi
  }
  kubectl_admin_long() { printf '%s\n' "$*" >>"${action_log}"; }

  reconcile_community_secret

  assert_equals \
    $'apply\nverify\nget deployment/car-community -o jsonpath={.spec.template.metadata.annotations.car-community\\.io/community-secret-resource-version}\npatch deployment/car-community --type=merge -p {"spec":{"template":{"metadata":{"annotations":{"car-community.io/community-secret-resource-version":"301"}}}}}\nrollout status deployment/car-community --timeout=300s\nwait --for=condition=Ready pod -l app.kubernetes.io/name=car-community,app.kubernetes.io/component=api --timeout=300s\nget deployment/car-community -o jsonpath={.spec.template.metadata.annotations.car-community\\.io/community-secret-resource-version}' \
    "$(cat "${action_log}")" \
    "Secret 생성·검증 뒤 Deployment rollout을 진행해야 합니다."
)

test_same_revision_rechecks_interrupted_rollout() (
  local action_log="$1"

  COMMUNITY_CURSOR_HMAC_SECRET='community-secret-with-at-least-32-bytes'
  application_secret_base64_no_wrap() { printf '%s\n' encoded-community; }
  application_secret_community_state() {
    printf '%s\n' 'car-community-application-bootstrap|Opaque|1|encoded-community|401'
  }
  kubectl_admin() {
    printf '%s\n' "$*" >>"${action_log}"
    if [[ "$*" == *'get deployment/car-community -o jsonpath='* ]]; then
      printf '%s\n' '401'
    fi
  }
  kubectl_admin_long() { printf '%s\n' "$*" >>"${action_log}"; }

  reconcile_community_secret

  grep -Fqx 'rollout status deployment/car-community --timeout=300s' "${action_log}" \
    || fail_test "같은 revision도 중단된 rollout 완료를 다시 확인해야 합니다."
  grep -Fqx 'wait --for=condition=Ready pod -l app.kubernetes.io/name=car-community,app.kubernetes.io/component=api --timeout=300s' "${action_log}" \
    || fail_test "같은 revision도 현재 Pod Ready를 다시 확인해야 합니다."
  ! grep -Eq '^(apply|patch)' "${action_log}" \
    || fail_test "같은 Secret과 annotation이면 apply·patch는 반복하면 안 됩니다."
)

run_tests() {
  TEST_ROOT="$(mktemp -d)"
  trap 'rm -rf -- "${TEST_ROOT}"' EXIT

  test_input_and_validation_contract
  test_allows_missing_owned_secret_and_rejects_foreign_schema
  test_rejects_auth_hmac_reuse_without_printing_values
  test_apply_streams_secret_without_plaintext_arguments "${TEST_ROOT}/apply-arguments"
  test_creates_secret_before_rollout_and_resumes "${TEST_ROOT}/actions"
  test_same_revision_rechecks_interrupted_rollout "${TEST_ROOT}/resume-actions"

  echo 'PASS: reconcile-community-secret.sh contract tests'
}

run_tests
