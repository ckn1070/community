#!/usr/bin/env bash

# Test double은 source한 production 함수가 간접 호출합니다.
# shellcheck disable=SC1090,SC2329
set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly TARGET_SCRIPT="${TEST_DIRECTORY}/../reconcile-catalog-redis-secrets.sh"
TEST_ROOT=""

source "${TARGET_SCRIPT}"

fail_test() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

test_reads_and_validates_three_distinct_fields() {
  read_inputs < <(printf '%s\0%s\0%s\0' \
    'application-password-with-32-bytes-0001' \
    'operation-password-with-32-bytes-000002' \
    'probe-password-with-at-least-32-bytes')
  validate_inputs

  OPERATION_PASSWORD="${APPLICATION_PASSWORD}"
  if (validate_inputs) >/dev/null 2>&1; then
    fail_test "같은 credential을 두 역할에 재사용하면 안 됩니다."
  fi
}

test_rejects_short_input_before_mutation() {
  APPLICATION_PASSWORD="short"
  OPERATION_PASSWORD="operation-password-with-32-bytes-000002"
  PROBE_PASSWORD="probe-password-with-at-least-32-bytes"
  if (validate_inputs) >/dev/null 2>&1; then
    fail_test "32 bytes 미만 credential을 거부해야 합니다."
  fi
}

test_rejects_existing_foreign_schema_before_mutation() (
  secret_state() { printf '%s\n' 'another-owner|Opaque|2|encoded-user|encoded-password'; }

  if (require_existing_secret_boundary target-secret 2 'unused') >/dev/null 2>&1; then
    fail_test "다른 owner의 기존 Secret을 mutation 전에 거부해야 합니다."
  fi

  secret_state() {
    printf '%s\n' "${APPLICATION_OWNER}|Opaque|3|encoded-user|encoded-password|unexpected"
  }
  if (require_existing_secret_boundary target-secret 2 'unused') >/dev/null 2>&1; then
    fail_test "승인되지 않은 key schema를 mutation 전에 거부해야 합니다."
  fi

  secret_state() {
    printf '%s\n' "${APPLICATION_OWNER}|Opaque|2|<no value>|encoded-password"
  }
  if (require_existing_secret_boundary target-secret 2 'unused') >/dev/null 2>&1; then
    fail_test "필수 key가 없는 기존 Secret을 mutation 전에 거부해야 합니다."
  fi
)

test_streams_encoded_values_without_plaintext_arguments() (
  local argument_log="$1"
  local manifest_log="${argument_log}.manifest"
  local application_secret='APPLICATION-SECRET-MUST-NOT-APPEAR-0001'
  local operation_secret='OPERATION-SECRET-MUST-NOT-APPEAR-000002'
  local probe_secret='PROBE-SECRET-MUST-NOT-APPEAR-0000000003'

  APPLICATION_PASSWORD="${application_secret}"
  OPERATION_PASSWORD="${operation_secret}"
  PROBE_PASSWORD="${probe_secret}"
  kubectl_admin() {
    printf '%s\n' "$*" >>"${argument_log}"
    cat >>"${manifest_log}"
  }

  apply_secret render_application_secret car-community-catalog-redis-application
  apply_secret render_server_secret car-community-catalog-redis-server

  ! grep -Fq 'SECRET-MUST-NOT-APPEAR' "${argument_log}" \
    || fail_test "Credential을 kubectl argument에 넣으면 안 됩니다."
  ! grep -Fq 'SECRET-MUST-NOT-APPEAR' "${manifest_log}" \
    || fail_test "Secret manifest에는 plaintext 대신 base64/hash만 있어야 합니다."
  grep -Fq 'REDIS_USERNAME:' "${manifest_log}" \
    || fail_test "Application Secret schema가 필요합니다."
  grep -Fq 'users.acl:' "${manifest_log}" \
    || fail_test "Redis server ACL schema가 필요합니다."
)

test_operation_acl_is_read_only_and_subcommand_scoped() {
  APPLICATION_PASSWORD='application-password-with-32-bytes-0001'
  OPERATION_PASSWORD='operation-password-with-32-bytes-000002'
  PROBE_PASSWORD='probe-password-with-at-least-32-bytes'
  local acl

  acl="$(render_server_acl)"
  grep -Fq '+ttl +info|commandstats +info|stats +ping' <<<"${acl}" \
    || fail_test "Operation ACL에는 smoke에 필요한 read-only command만 있어야 합니다."
  ! grep -Eq '\+(info|latency|memory)([[:space:]]|$)' <<<"${acl}" \
    || fail_test "Operation ACL에 전체 진단 command family를 허용하면 안 됩니다."
  ! grep -Eq '(^|[[:space:]])\+(latency\|reset|memory\|purge)([[:space:]]|$)' <<<"${acl}" \
    || fail_test "Operation ACL에 상태 변경 subcommand를 허용하면 안 됩니다."
}

test_reconcile_verifies_both_postconditions_and_is_reentrant() (
  local action_log="$1"

  APPLICATION_PASSWORD='application-password-with-32-bytes-0001'
  # render_server_secret가 전역 credential 세 개를 간접 소비합니다.
  # shellcheck disable=SC2034
  OPERATION_PASSWORD='operation-password-with-32-bytes-000002'
  PROBE_PASSWORD='probe-password-with-at-least-32-bytes'
  secret_state() {
    case "$1" in
      car-community-catalog-redis-application)
        printf '%s|Opaque|2|%s|%s\n' \
          "${APPLICATION_OWNER}" \
          "$(base64_no_wrap application)" \
          "$(base64_no_wrap "${APPLICATION_PASSWORD}")"
        ;;
      car-community-catalog-redis-server)
        printf '%s|Opaque|3|%s|%s|%s\n' \
          "${APPLICATION_OWNER}" \
          "$(render_server_secret | awk '/^  users\.acl: / {print $2}')" \
          "$(base64_no_wrap probe)" \
          "$(base64_no_wrap "${PROBE_PASSWORD}")"
        ;;
      *) return 1 ;;
    esac
  }
  apply_secret() { printf 'apply:%s\n' "$2" >>"${action_log}"; }

  reconcile_secrets >>"${action_log}"
  reconcile_secrets >>"${action_log}"

  [[ "$(grep -c '^\[완료\]' "${action_log}")" -eq 2 ]] \
    || fail_test "같은 입력 재실행도 두 Secret postcondition을 확인하고 성공해야 합니다."
  [[ "$(grep -c '^apply:' "${action_log}")" -eq 4 ]] \
    || fail_test "각 실행은 두 Secret을 같은 목표로 안전하게 재수렴해야 합니다."
)

test_partial_apply_failure_is_not_reported_as_success() (
  local calls=0

  apply_secret() {
    calls=$((calls + 1))
    [[ "${calls}" -lt 2 ]] || return 1
  }
  verify_secrets() { fail_test "부분 적용 뒤 postcondition 검증을 실행하면 안 됩니다."; }
  require_existing_secret_boundary() { :; }

  if (reconcile_secrets) >/dev/null 2>&1; then
    fail_test "두 번째 Secret 적용 실패를 성공으로 보고하면 안 됩니다."
  fi
)

run_tests() {
  TEST_ROOT="$(mktemp -d)"
  trap 'rm -rf -- "${TEST_ROOT}"' EXIT
  test_reads_and_validates_three_distinct_fields
  test_rejects_short_input_before_mutation
  test_rejects_existing_foreign_schema_before_mutation
  test_streams_encoded_values_without_plaintext_arguments "${TEST_ROOT}/arguments"
  test_operation_acl_is_read_only_and_subcommand_scoped
  test_reconcile_verifies_both_postconditions_and_is_reentrant "${TEST_ROOT}/actions"
  test_partial_apply_failure_is_not_reported_as_success
  echo 'PASS: reconcile-catalog-redis-secrets.sh contract tests'
}

run_tests
