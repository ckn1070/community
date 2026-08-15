#!/usr/bin/env bash

# Private ECR image pull smoke script의 target·cleanup 계약을 검증합니다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
readonly TARGET_SCRIPT="${SCRIPT_DIR}/../verify-private-image-pull.sh"

# shellcheck source=../verify-private-image-pull.sh
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

test_kubectl_calls_have_bounded_request_timeouts() (
  local test_root="$1"
  local action_log="${test_root}/kubectl-actions"

  kubectl() {
    printf '%s\n' "$*" >>"${action_log}"
  }

  kubectl_admin get namespace example
  wait_for_smoke_job example
  delete_smoke_namespace example

  grep -Fq -- "--request-timeout=${KUBECTL_REQUEST_TIMEOUT} get namespace example" "${action_log}"
  grep -Fq -- "--request-timeout=${SMOKE_WAIT_REQUEST_TIMEOUT} -n example wait --for=condition=complete --timeout=${SMOKE_WAIT_TIMEOUT}" "${action_log}"
  grep -Fq -- "--request-timeout=${SMOKE_DELETE_REQUEST_TIMEOUT} delete namespace example --wait=true --timeout=${SMOKE_DELETE_TIMEOUT}" "${action_log}"
)

test_kubectl_request_timeout_failure_propagates() (
  local execution_status

  kubectl() {
    return 124
  }

  set +e
  kubectl_admin get namespace example
  execution_status=$?
  set -e

  assert_equals "124" "${execution_status}" "kubectl request timeout을 성공으로 처리하면 안 됩니다."
)

test_renders_forced_registry_pull_job() (
  local test_root="$1"
  local manifest_path="${test_root}/job.yaml"

  kubectl_admin() {
    cat >"${manifest_path}"
  }

  create_smoke_job \
    "car-community-ecr-smoke" \
    "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/car-community@sha256:$(printf 'd%.0s' {1..64})"

  grep -Fqx -- '          imagePullPolicy: Always' "${manifest_path}"
  grep -Fqx -- '  backoffLimit: 0' "${manifest_path}"
  grep -Fqx -- '            - java' "${manifest_path}"
  grep -Fqx -- '            - -version' "${manifest_path}"
)

test_accepts_exact_private_ecr_digest_reference() {
  require_valid_image_reference \
    "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/car-community@sha256:$(printf 'a%.0s' {1..64})"
}

test_rejects_mutable_or_wrong_target_reference() (
  local image_reference="$1"
  local execution_status

  set +e
  (require_valid_image_reference "${image_reference}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    printf 'FAIL: 허용하지 않은 image reference를 smoke target으로 사용하면 안 됩니다: %s\n' "${image_reference}" >&2
    exit 1
  fi
)

test_smoke_success_cleans_namespace() (
  local test_root="$1"
  local action_log="${test_root}/success-actions"

  create_smoke_namespace() { printf '%s\n' "create:$1" >>"${action_log}"; }
  create_smoke_job() { printf '%s\n' "job:$1:$2" >>"${action_log}"; }
  wait_for_smoke_job() { printf '%s\n' "wait:$1" >>"${action_log}"; }
  verify_pulled_image_digest() { printf '%s\n' "digest:$1:$2" >>"${action_log}"; }
  print_smoke_logs() { printf '%s\n' "logs:$1" >>"${action_log}"; }
  delete_smoke_namespace() { printf '%s\n' "delete:$1" >>"${action_log}"; }

  run_private_image_pull_smoke \
    "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/car-community@sha256:$(printf 'b%.0s' {1..64})"

  assert_equals \
    $'create:car-community-ecr-smoke\njob:car-community-ecr-smoke:123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/car-community@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\nwait:car-community-ecr-smoke\ndigest:car-community-ecr-smoke:sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\nlogs:car-community-ecr-smoke\ndelete:car-community-ecr-smoke' \
    "$(<"${action_log}")" \
    "성공한 smoke resource도 digest 확인과 log 수집 뒤 삭제해야 합니다."
)

test_smoke_failure_prints_diagnostics_and_cleans_namespace() (
  local test_root="$1"
  local action_log="${test_root}/failure-actions"
  local execution_status

  create_smoke_namespace() { printf '%s\n' "create:$1" >>"${action_log}"; }
  create_smoke_job() { printf '%s\n' "job:$1" >>"${action_log}"; }
  wait_for_smoke_job() { return 1; }
  verify_pulled_image_digest() { echo "FAIL: wait 실패 뒤 digest를 확인하면 안 됩니다." >&2; return 1; }
  print_smoke_diagnostics() { printf '%s\n' "diagnostics:$1" >>"${action_log}"; }
  print_smoke_logs() { echo "FAIL: 완료되지 않은 Job log를 성공 결과로 출력하면 안 됩니다." >&2; return 1; }
  delete_smoke_namespace() { printf '%s\n' "delete:$1" >>"${action_log}"; }

  set +e
  (run_private_image_pull_smoke \
    "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/car-community@sha256:$(printf 'c%.0s' {1..64})" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: image pull 또는 Job 실패를 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi
  assert_equals \
    $'create:car-community-ecr-smoke\njob:car-community-ecr-smoke\ndiagnostics:car-community-ecr-smoke\ndelete:car-community-ecr-smoke' \
    "$(<"${action_log}")" \
    "실패 시 진단을 출력하고 이 실행이 만든 namespace를 삭제해야 합니다."
)

run_tests() {
  local test_root
  test_root="$(mktemp -d)"
  trap '[[ -z "${test_root:-}" ]] || rm -rf -- "${test_root}"' EXIT

  test_accepts_exact_private_ecr_digest_reference
  test_kubectl_calls_have_bounded_request_timeouts "${test_root}"
  test_kubectl_request_timeout_failure_propagates
  test_rejects_mutable_or_wrong_target_reference \
    "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/car-community:latest"
  test_rejects_mutable_or_wrong_target_reference \
    "123456789012.dkr.ecr.us-east-1.amazonaws.com/car-community@sha256:$(printf 'a%.0s' {1..64})"
  test_rejects_mutable_or_wrong_target_reference \
    "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/other@sha256:$(printf 'a%.0s' {1..64})"
  test_renders_forced_registry_pull_job "${test_root}"
  test_smoke_success_cleans_namespace "${test_root}"
  test_smoke_failure_prints_diagnostics_and_cleans_namespace "${test_root}"

  echo "PASS: verify-private-image-pull.sh contract tests"
}

run_tests
