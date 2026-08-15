#!/usr/bin/env bash

set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly TARGET_SCRIPT="${TEST_DIRECTORY}/../reconcile-notification-secret.sh"

TEST_ROOT=""

# shellcheck source=../reconcile-notification-secret.sh
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

test_accepts_one_nul_value_and_rejects_trailing_input() {
  local status

  read_notification_input < <(printf '%s\0' 're_new_key')
  assert_equals 're_new_key' "${RESEND_API_KEY}" "Resend key를 읽어야 합니다."

  set +e
  (read_notification_input < <(printf '%s\0' 're_new_key' 'extra')) >/dev/null 2>&1
  status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "두 번째 field를 거부해야 합니다."
}

test_rejects_missing_or_foreign_resources_before_apply() (
  local action_log="$1"
  local status

  kubectl_admin() {
    printf '%s\n' "$*" >>"${action_log}"
    if [[ "$*" == *'secret/car-community-notification'* ]]; then
      return 0
    fi
    return 1
  }
  kubectl_cluster() { return 1; }

  set +e
  (require_existing_runtime) >/dev/null 2>&1
  status=$?
  set -e
  [[ "${status}" -ne 0 ]] || fail_test "누락된 Deployment를 거부해야 합니다."
  [[ ! -e "${action_log}" ]] || ! grep -Fqx apply "${action_log}" \
    || fail_test "preflight 실패 전에 Secret을 적용하면 안 됩니다."
)

test_applies_secret_before_rollout_without_plaintext_arguments() (
  local action_log="$1"
  local secret_value='RESEND-SECRET-MUST-NOT-APPEAR-IN-ARGV'
  ACTION_LOG="${action_log}"
  ANNOTATION_STATE_PATH="${action_log}.annotation"
  printf '%s\n' '100' >"${ANNOTATION_STATE_PATH}"

  RESEND_API_KEY="${secret_value}"
  kubectl_admin() {
    printf '%s\n' "$*" >>"${ACTION_LOG}"
    case "$*" in
      *'get secret/car-community-notification -o go-template='*)
        printf '%s\n' 'car-community-application-bootstrap|Opaque|1|old-value|101'
        ;;
      *'get deployment/car-community -o jsonpath='*)
        cat "${ANNOTATION_STATE_PATH}"
        ;;
      *'apply --server-side'*)
        cat >/dev/null
        ;;
      *'patch deployment/car-community'*) printf '%s\n' '101' >"${ANNOTATION_STATE_PATH}" ;;
      *'rollout status deployment/car-community'*) ;;
    esac
  }
  kubectl_admin_long() { printf '%s\n' "$*" >>"${ACTION_LOG}"; }
  application_secret_notification_state() {
    printf '%s\n' 'car-community-application-bootstrap|Opaque|1|old-value|101'
  }
  application_secret_base64_no_wrap() { printf '%s\n' expected-value; }
  application_secret_verify_notification() { :; }
  apply_notification_secret() { printf '%s\n' apply >>"${ACTION_LOG}"; }

  reconcile_notification_secret

  assert_equals \
    $'apply\nget deployment/car-community -o jsonpath={.spec.template.metadata.annotations.car-community\\.io/notification-secret-resource-version}\npatch deployment/car-community --type=merge -p {"spec":{"template":{"metadata":{"annotations":{"car-community.io/notification-secret-resource-version":"101"}}}}}\nrollout status deployment/car-community --timeout=300s\nwait --for=condition=Ready pod -l app.kubernetes.io/name=car-community,app.kubernetes.io/component=api --timeout=300s\nget deployment/car-community -o jsonpath={.spec.template.metadata.annotations.car-community\\.io/notification-secret-resource-version}' \
    "$(grep -E '^(apply|patch|rollout|wait|get deployment)' "${action_log}")" \
    "Secret 적용 뒤 Deployment rollout을 진행해야 합니다."
  ! grep -Fq "${secret_value}" "${action_log}" \
    || fail_test "Resend key를 kubectl argument에 넣으면 안 됩니다."
)

test_resumes_rollout_and_skips_complete_noop() (
  local action_log="$1"

  ACTION_LOG="${action_log}"
  ANNOTATION_STATE_PATH="${action_log}.annotation"
  RESEND_API_KEY='re_same_key'
  application_secret_base64_no_wrap() { printf '%s\n' same-value; }
  application_secret_notification_state() {
    printf '%s\n' 'car-community-application-bootstrap|Opaque|1|same-value|202'
  }
  kubectl_admin() {
    printf '%s\n' "$*" >>"${ACTION_LOG}"
    if [[ "$*" == *'get deployment/car-community -o jsonpath='* ]]; then
      cat "${ANNOTATION_STATE_PATH}"
    elif [[ "$*" == *'patch deployment/car-community'* ]]; then
      printf '%s\n' '202' >"${ANNOTATION_STATE_PATH}"
    fi
  }
  kubectl_admin_long() { printf '%s\n' "$*" >>"${ACTION_LOG}"; }

  printf '%s\n' '201' >"${ANNOTATION_STATE_PATH}"
  reconcile_notification_secret
  ! grep -Fq 'apply --server-side' "${action_log}" \
    || fail_test "같은 Secret 값이면 API apply를 반복하면 안 됩니다."
  grep -Fq 'patch deployment/car-community' "${action_log}" \
    || fail_test "중단된 rollout은 같은 입력으로 재개해야 합니다."

  : >"${action_log}"
  printf '%s\n' '202' >"${ANNOTATION_STATE_PATH}"
  reconcile_notification_secret
  ! grep -Eq '^(apply|patch|rollout)' "${action_log}" \
    || fail_test "Secret과 rollout annotation이 같으면 no-op이어야 합니다."
)

cleanup_test_root() {
  [[ -z "${TEST_ROOT}" ]] || rm -rf -- "${TEST_ROOT}"
}

run_tests() {
  TEST_ROOT="$(mktemp -d)"
  trap cleanup_test_root EXIT

  test_accepts_one_nul_value_and_rejects_trailing_input
  test_rejects_missing_or_foreign_resources_before_apply "${TEST_ROOT}/preflight-actions"
  test_applies_secret_before_rollout_without_plaintext_arguments "${TEST_ROOT}/apply-actions"
  test_resumes_rollout_and_skips_complete_noop "${TEST_ROOT}/resume-actions"

  echo 'PASS: reconcile-notification-secret.sh contract tests'
}

run_tests
