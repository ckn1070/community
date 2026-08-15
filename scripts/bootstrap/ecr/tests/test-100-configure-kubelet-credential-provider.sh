#!/usr/bin/env bash

# ECR credential provider 구성 script의 file·checksum·재실행 계약을 검증합니다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
readonly TARGET_SCRIPT="${SCRIPT_DIR}/../100-configure-kubelet-credential-provider.sh"

# shellcheck source=../100-configure-kubelet-credential-provider.sh
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

test_renders_exact_provider_configuration() {
  local test_root="$1"
  local rendered_path="${test_root}/credential-provider.yaml"

  render_credential_provider_configuration "${rendered_path}"

  assert_equals "1.36.2-2.1" "${TARGET_KUBELET_PACKAGE_VERSION}" \
    "Bootstrap이 고정한 exact kubelet package를 소비해야 합니다."
  assert_equals "1.36.1" "${ECR_CREDENTIAL_PROVIDER_VERSION}" \
    "Kubernetes 1.36 release line의 검토된 provider를 고정해야 합니다."
  assert_equals "96b58e09522e47cf653065a104809ecba439630eaabea42db2dcb9ccedb1ed62" \
    "${ECR_CREDENTIAL_PROVIDER_SHA256}" \
    "Official ARM64 provider artifact의 검증된 checksum을 고정해야 합니다."

  assert_file_contains "${rendered_path}" 'apiVersion: kubelet.config.k8s.io/v1' \
    "Kubelet v1 CredentialProviderConfig를 사용해야 합니다."
  assert_file_contains "${rendered_path}" '  - name: ecr-credential-provider' \
    "설치 binary 이름과 provider 이름이 일치해야 합니다."
  assert_file_contains "${rendered_path}" '      - "*.dkr.ecr.*.amazonaws.com"' \
    "Private ECR registry domain을 match해야 합니다."
  assert_file_contains "${rendered_path}" '    defaultCacheDuration: "12h"' \
    "Provider response가 duration을 주지 않을 때 사용할 필수 fallback을 명시해야 합니다."
  assert_file_contains "${rendered_path}" '    apiVersion: credentialprovider.kubelet.k8s.io/v1' \
    "Stable exec credential provider API를 사용해야 합니다."
  assert_file_excludes "${rendered_path}" 'get-credentials' \
    "ECR provider는 stdin 요청을 직접 읽으므로 지원하지 않는 subcommand를 전달하면 안 됩니다."
}

test_rejects_foreign_kubelet_extra_arguments() (
  local test_root="$1"
  local kubelet_defaults="${test_root}/kubelet"
  local execution_status

  printf '%s\n' 'KUBELET_EXTRA_ARGS="--node-ip=10.0.0.10"' >"${kubelet_defaults}"

  set +e
  (require_safe_existing_kubelet_defaults "${kubelet_defaults}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 owner의 kubelet extra argument를 덮어쓰면 안 됩니다." >&2
    exit 1
  fi
)

test_accepts_absent_empty_package_default_and_exact_kubelet_defaults() (
  local test_root="$1"
  local kubelet_defaults="${test_root}/accepted-kubelet"

  require_safe_existing_kubelet_defaults "${kubelet_defaults}"
  : >"${kubelet_defaults}"
  require_safe_existing_kubelet_defaults "${kubelet_defaults}"
  printf '%s\n' 'KUBELET_EXTRA_ARGS=' >"${kubelet_defaults}"
  require_safe_existing_kubelet_defaults "${kubelet_defaults}"
  printf '%s\n' "${KUBELET_DEFAULTS_CONFIGURATION}" >"${kubelet_defaults}"
  require_safe_existing_kubelet_defaults "${kubelet_defaults}"
)

test_download_rejects_checksum_mismatch() (
  local test_root="$1"
  local downloaded_path="${test_root}/provider"
  local execution_status

  curl() {
    local argument
    local output_path=""
    local next_is_output=0

    for argument in "$@"; do
      if [[ "${next_is_output}" -eq 1 ]]; then
        output_path="${argument}"
        break
      fi
      if [[ "${argument}" == "--output" ]]; then
        next_is_output=1
      fi
    done
    printf '%s\n' "tampered artifact" >"${output_path}"
  }

  set +e
  (download_verified_provider "https://example.invalid/provider" "$(printf '0%.0s' {1..64})" "${downloaded_path}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 || -e "${downloaded_path}" ]]; then
    echo "FAIL: checksum이 다른 provider binary를 남기거나 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_download_uses_bounded_transient_retry_only() (
  local test_root="$1"
  local downloaded_path="${test_root}/provider-with-retry-contract"
  local curl_arguments="${test_root}/curl-arguments"
  local expected_checksum

  expected_checksum="$(printf '%s\n' 'verified artifact' | sha256sum | awk '{print $1}')"
  curl() {
    local argument
    local output_path=""
    local next_is_output=0

    printf '%s\n' "$*" >"${curl_arguments}"
    for argument in "$@"; do
      if [[ "${next_is_output}" -eq 1 ]]; then
        output_path="${argument}"
        break
      fi
      if [[ "${argument}" == "--output" ]]; then
        next_is_output=1
      fi
    done
    printf '%s\n' 'verified artifact' >"${output_path}"
  }

  download_verified_provider "https://example.invalid/provider" "${expected_checksum}" "${downloaded_path}"

  grep -Fq -- '--connect-timeout 15' "${curl_arguments}"
  grep -Fq -- '--max-time 300' "${curl_arguments}"
  grep -Fq -- '--retry 3' "${curl_arguments}"
  grep -Fq -- '--retry-delay 2' "${curl_arguments}"
  if grep -Fq -- '--retry-all-errors' "${curl_arguments}"; then
    echo "FAIL: 404·인증 오류 같은 비복구 실패까지 재시도하면 안 됩니다." >&2
    exit 1
  fi
)

test_main_preserves_mutation_order() (
  local test_root="$1"
  local action_log="${test_root}/actions"
  local work_directory="${test_root}/work"

  run_preflight_checks() { printf '%s\n' "preflight" >>"${action_log}"; }
  create_work_directory() { mkdir -p "${work_directory}"; printf '%s\n' "${work_directory}"; }
  download_verified_provider() { printf '%s\n' "download" >>"${action_log}"; : >"$3"; }
  install_provider_binary() { printf '%s\n' "install-binary" >>"${action_log}"; }
  install_provider_configuration() { printf '%s\n' "install-config" >>"${action_log}"; }
  install_kubelet_defaults() { printf '%s\n' "install-kubelet-args" >>"${action_log}"; }
  restart_kubelet() { printf '%s\n' "restart" >>"${action_log}"; }
  verify_postconditions() { printf '%s\n' "verify" >>"${action_log}"; }

  main >/dev/null

  assert_equals \
    $'preflight\ndownload\ninstall-binary\ninstall-config\ninstall-kubelet-args\nrestart\nverify' \
    "$(<"${action_log}")" \
    "검증된 binary와 설정을 모두 게시한 뒤 kubelet을 재시작하고 postcondition을 확인해야 합니다."
)

run_tests() {
  local test_root
  test_root="$(mktemp -d)"
  trap '[[ -z "${test_root:-}" ]] || rm -rf -- "${test_root}"' EXIT

  test_renders_exact_provider_configuration "${test_root}"
  test_rejects_foreign_kubelet_extra_arguments "${test_root}"
  test_accepts_absent_empty_package_default_and_exact_kubelet_defaults "${test_root}"
  test_download_rejects_checksum_mismatch "${test_root}"
  test_download_uses_bounded_transient_retry_only "${test_root}"
  test_main_preserves_mutation_order "${test_root}"

  echo "PASS: 100-configure-kubelet-credential-provider.sh contract tests"
}

run_tests
