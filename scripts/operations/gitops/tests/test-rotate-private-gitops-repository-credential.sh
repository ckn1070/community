#!/usr/bin/env bash

# GitHub App credential rotation의 기존 resource 경계와 실행 순서를 검증합니다.
set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly TARGET_SCRIPT="${TEST_DIRECTORY}/../rotate-private-gitops-repository-credential.sh"

# shellcheck source=../rotate-private-gitops-repository-credential.sh
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

test_rejects_missing_repository_resource() (
  local execution_status

  # shellcheck disable=SC2329  # require_managed_existing가 함수 이름으로 간접 호출합니다.
  kubectl_admin() { :; }

  set +e
  (require_managed_existing secret/car-community-gitops-repository >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 최초 연결 resource 없이 credential rotation을 시작하면 안 됩니다." >&2
    exit 1
  fi
)

test_rotation_preserves_existing_gitops_resources() (
  local test_root="$1"
  local action_log="${test_root}/rotation-actions"
  local fixture_work_directory="${test_root}/rotation-work"

  parse_arguments() { :; }
  run_rotation_preflight_checks() { printf '%s\n' preflight >>"${action_log}"; }
  current_application_reconciled_at() {
    printf '%s\n' baseline >>"${action_log}"
    printf '%s\n' '2026-08-09T08:00:00Z'
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
  # shellcheck disable=SC2329  # 호출되지 않는 것이 이 회귀 검증의 계약입니다.
  apply_gitops_resources() {
    echo "FAIL: credential rotation은 AppProject나 Application을 다시 적용하면 안 됩니다." >&2
    exit 1
  }
  force_and_wait_for_fresh_reconciliation() {
    printf 'fresh-reconcile:%s\n' "$1" >>"${action_log}"
  }
  wait_for_application() { printf '%s\n' wait >>"${action_log}"; }
  verify_postconditions() { printf '%s\n' verify >>"${action_log}"; }

  main 123 456 </dev/null >/dev/null

  assert_equals \
    $'preflight\nbaseline\nread-key\napply-secret\nrestart-application-controller\nfresh-reconcile:2026-08-09T08:00:00Z\nwait\nverify' \
    "$(<"${action_log}")" \
    "Rotation은 기존 GitOps 선언을 유지하고 Secret·controller·fresh reconcile만 갱신해야 합니다."
)

run_tests() {
  local test_root
  test_root="$(mktemp -d)"
  trap '[[ -z "${test_root:-}" ]] || rm -rf -- "${test_root}"' EXIT

  test_rejects_missing_repository_resource
  test_rotation_preserves_existing_gitops_resources "${test_root}"

  echo "PASS: rotate-private-gitops-repository-credential.sh contract tests"
}

run_tests
