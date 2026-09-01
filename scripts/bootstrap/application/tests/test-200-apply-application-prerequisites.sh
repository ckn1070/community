#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIRECTORY
readonly SCRIPT_PATH="${SCRIPT_DIRECTORY}/../200-apply-application-prerequisites.sh"

# shellcheck source=../200-apply-application-prerequisites.sh
source "${SCRIPT_PATH}"

TEST_ROOT=""

fail_test() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  local message="$3"

  [[ "${actual}" == "${expected}" ]] || fail_test "${message} (expected=${expected}, actual=${actual})"
}

assert_contains() {
  local file_path="$1"
  local expected="$2"
  local message="$3"

  grep -Fq -- "${expected}" "${file_path}" || fail_test "${message}"
}

assert_excludes() {
  local file_path="$1"
  local forbidden="$2"
  local message="$3"

  if grep -Fq -- "${forbidden}" "${file_path}"; then
    fail_test "${message}"
  fi
}

set_valid_inputs() {
  RUNTIME_PASSWORD='runtime-password'
  MIGRATOR_PASSWORD='migrator-password'
  AUTH_JWT_HMAC_SECRET='jwt-secret-with-at-least-thirty-two-bytes'
  AUTH_LOGIN_IDENTIFIER_HMAC_SECRET='identifier-secret-with-at-least-32-bytes'
  COMMUNITY_CURSOR_HMAC_SECRET='community-cursor-secret-with-at-least-32-bytes'
  RESEND_API_KEY='re_staging_notification_key'
}

test_accepts_exact_six_nul_fields() {
  read_fixed_input < <(printf '%s\0' \
    'runtime-password' \
    'migrator-password' \
    'jwt-secret-with-at-least-thirty-two-bytes' \
    'identifier-secret-with-at-least-32-bytes' \
    'community-cursor-secret-with-at-least-32-bytes' \
    're_staging_notification_key')
  validate_inputs

  assert_equals 'runtime-password' "${RUNTIME_PASSWORD}" "Runtime password를 읽어야 합니다."
  assert_equals 'migrator-password' "${MIGRATOR_PASSWORD}" "Migrator password를 읽어야 합니다."
  assert_equals 'community-cursor-secret-with-at-least-32-bytes' "${COMMUNITY_CURSOR_HMAC_SECRET}" \
    "Community cursor secret을 읽어야 합니다."
  assert_equals 're_staging_notification_key' "${RESEND_API_KEY}" "Resend API key를 읽어야 합니다."
}

test_rejects_missing_or_trailing_input() {
  local status

  set +e
  (read_fixed_input </dev/null) >/dev/null 2>&1
  status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "누락된 stdin을 거부해야 합니다."

  set +e
  (read_fixed_input < <(printf '%s\0' \
    'runtime-password' \
    'migrator-password' \
    'jwt-secret-with-at-least-thirty-two-bytes' \
    'identifier-secret-with-at-least-32-bytes' \
    'community-cursor-secret-with-at-least-32-bytes' \
    're_staging_notification_key' \
    'extra')) >/dev/null 2>&1
  status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "일곱 번째 stdin field를 거부해야 합니다."
}

test_rejects_invalid_runtime_contract() {
  local status

  set_valid_inputs
  MIGRATOR_PASSWORD="${RUNTIME_PASSWORD}"
  set +e
  (validate_inputs) >/dev/null 2>&1
  status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "공유 DB password를 거부해야 합니다."

  set_valid_inputs
  RUNTIME_PASSWORD=$'runtime\npassword'
  set +e
  (validate_inputs) >/dev/null 2>&1
  status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "Role bootstrap이 거부하는 줄바꿈 DB password를 거부해야 합니다."

  set_valid_inputs
  AUTH_JWT_HMAC_SECRET='short'
  set +e
  (validate_inputs) >/dev/null 2>&1
  status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "32 bytes 미만 HMAC secret을 거부해야 합니다."

  set_valid_inputs
  COMMUNITY_CURSOR_HMAC_SECRET="${AUTH_JWT_HMAC_SECRET}"
  set +e
  (validate_inputs) >/dev/null 2>&1
  status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "인증 secret을 Community cursor에 재사용하면 거부해야 합니다."
}

test_has_no_secondary_verification_mode_or_cross_script_dependency() {
  assert_excludes "${SCRIPT_PATH}" '--verify-existing-prerequisites' \
    "Application bootstrap에 별도 verification mode를 두면 안 됩니다."
  assert_excludes "${SCRIPT_PATH}" '600-enable-secret-encryption' \
    "Application bootstrap이 다른 bootstrap 단계를 재검증하면 안 됩니다."
  assert_excludes "${SCRIPT_PATH}" 'managedFields' \
    "Application bootstrap이 Kubernetes field owner 상태 머신을 구현하면 안 됩니다."
}

test_applies_exact_resources_without_secret_arguments() {
  local argument_log="${TEST_ROOT}/kubectl-arguments"
  local manifest_log="${TEST_ROOT}/kubectl-manifests"
  local secret_sentinel='SECRET-MUST-NOT-APPEAR-IN-KUBECTL-ARGV'

  set_valid_inputs
  RUNTIME_PASSWORD="${secret_sentinel}-runtime"
  MIGRATOR_PASSWORD="${secret_sentinel}-migrator"
  AUTH_JWT_HMAC_SECRET="${secret_sentinel}-jwt-01234567890123456789012345678901"
  AUTH_LOGIN_IDENTIFIER_HMAC_SECRET="${secret_sentinel}-identifier-01234567890123456789012345678901"
  COMMUNITY_CURSOR_HMAC_SECRET="${secret_sentinel}-community-01234567890123456789012345678901"
  RESEND_API_KEY="${secret_sentinel}-resend"

  kubectl_admin() {
    printf '%s\n' "$*" >>"${argument_log}"
    cat >>"${manifest_log}"
  }

  apply_resources

  assert_excludes "${argument_log}" "${secret_sentinel}" \
    "Secret 값을 kubectl argument에 넣으면 안 됩니다."
  assert_contains "${argument_log}" '--server-side' "Server-side apply를 사용해야 합니다."
  assert_excludes "${argument_log}" '--labels=' \
    "kubectl create configmap이 지원하지 않는 --labels flag를 사용하면 안 됩니다."
  assert_excludes "${argument_log}" '--force-conflicts' "Foreign field를 강제로 인수하면 안 됩니다."
  assert_contains "${manifest_log}" 'kind: Namespace' "Namespace를 적용해야 합니다."
  assert_excludes "${manifest_log}" 'kind: ConfigMap' "공개 설정 ConfigMap은 GitOps가 적용해야 합니다."
  assert_contains "${manifest_log}" 'name: car-community-db-runtime' "Runtime Secret을 적용해야 합니다."
  assert_contains "${manifest_log}" "DB_USERNAME: $(base64_no_wrap 'carmunity_runtime')" \
    "Runtime username은 고정 값을 적용해야 합니다."
  assert_contains "${manifest_log}" 'name: car-community-db-migrator' "Migrator Secret을 적용해야 합니다."
  assert_contains "${manifest_log}" "DB_MIGRATOR_USERNAME: $(base64_no_wrap 'carmunity_migrator')" \
    "Migrator username은 고정 값을 적용해야 합니다."
  assert_contains "${manifest_log}" 'name: car-community-auth' "Auth Secret을 적용해야 합니다."
  assert_contains "${manifest_log}" 'name: car-community-community' "Community Secret을 적용해야 합니다."
  assert_contains "${manifest_log}" 'COMMUNITY_CURSOR_HMAC_SECRET:' "Cursor key schema를 적용해야 합니다."
  assert_contains "${manifest_log}" 'name: car-community-notification' "Notification Secret을 적용해야 합니다."
  assert_contains "${manifest_log}" 'RESEND_API_KEY:' "Resend key schema를 적용해야 합니다."
  assert_equals '6' "$(grep -c '^kind:' "${manifest_log}")" "정확히 여섯 resource를 적용해야 합니다."
  assert_excludes "${manifest_log}" "${secret_sentinel}" "Secret manifest에 plaintext를 쓰면 안 됩니다."
}

test_rejects_input_before_external_work() {
  local action_log="${TEST_ROOT}/invalid-input-actions"
  local status

  run_preflight() { printf '%s\n' preflight >>"${action_log}"; }
  apply_resources() { printf '%s\n' apply >>"${action_log}"; }

  set +e
  (main </dev/null) >/dev/null 2>&1
  status=$?
  set -e

  [[ "${status}" -ne 0 ]] || fail_test "누락 입력을 거부해야 합니다."
  [[ ! -e "${action_log}" ]] || fail_test "입력 검증 전에 외부 작업을 시작하면 안 됩니다."
}

test_help_exposes_only_apply_mode() {
  local help_output="${TEST_ROOT}/help-output"

  print_usage >"${help_output}"
  assert_contains "${help_output}" "printf '%s\\0'" "NUL stdin 계약을 설명해야 합니다."
  assert_contains "${help_output}" 'STG_DB_RUNTIME_PASSWORD' "Runtime password 입력을 안내해야 합니다."
  assert_excludes "${help_output}" 'STG_DB_RUNTIME_USERNAME' "고정 runtime username을 입력받으면 안 됩니다."
  assert_excludes "${help_output}" 'STG_DB_MIGRATOR_USERNAME' "고정 migrator username을 입력받으면 안 됩니다."
  assert_contains "${help_output}" 'sudo -n' "Non-interactive sudo를 안내해야 합니다."
  assert_excludes "${help_output}" '--verify' "별도 verification mode를 안내하면 안 됩니다."
}

cleanup() {
  [[ "${BASH_SUBSHELL:-0}" -eq 0 ]] || return
  [[ -z "${TEST_ROOT}" ]] || rm -rf -- "${TEST_ROOT}"
}

run_test() (
  trap - EXIT
  "$@"
)

run_tests() {
  TEST_ROOT="$(mktemp -d)"
  trap cleanup EXIT

  run_test test_accepts_exact_six_nul_fields
  run_test test_rejects_missing_or_trailing_input
  run_test test_rejects_invalid_runtime_contract
  run_test test_has_no_secondary_verification_mode_or_cross_script_dependency
  run_test test_applies_exact_resources_without_secret_arguments
  run_test test_rejects_input_before_external_work
  run_test test_help_exposes_only_apply_mode

  echo 'PASS: 200-apply-application-prerequisites.sh contract tests'
}

run_tests
