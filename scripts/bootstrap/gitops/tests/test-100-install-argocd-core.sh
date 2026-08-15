#!/usr/bin/env bash

# Argo CD Core 설치 script의 artifact·기존 상태·적용 순서 계약을 검증합니다.
set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly TARGET_SCRIPT="${TEST_DIRECTORY}/../100-install-argocd-core.sh"

# shellcheck source=../100-install-argocd-core.sh
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

test_pins_kubernetes_136_compatible_release() {
  assert_equals "3.5.0" "${ARGOCD_VERSION}" \
    "Kubernetes 1.36을 official tested 대상으로 둔 exact Argo CD release를 고정해야 합니다."
  assert_equals \
    "166bffca5000482f8ae36d73b255610c9bb4c7d8da38fcf62003c2fdd8ab0747" \
    "${ARGOCD_CORE_MANIFEST_SHA256}" \
    "검토한 official Core manifest의 exact SHA-256을 고정해야 합니다."
  assert_equals \
    "sha256:8a14921f7e5dc3408111714f05bff68e337d459653c45f0aea738330d156079b" \
    "${ARGOCD_IMAGE_DIGEST}" \
    "Argo CD ARM64 runtime manifest digest를 고정해야 합니다."
  assert_equals \
    "sha256:2fe148588d93987cea3533873195ec2450f5dbf420c78606aaacad7b78bb5489" \
    "${ARGOCD_REDIS_IMAGE_DIGEST}" \
    "Redis ARM64 runtime manifest digest를 고정해야 합니다."
}

test_pins_runtime_images_in_verified_manifest() {
  local test_root="$1"
  local source_path="${test_root}/source-manifest.yaml"
  local pinned_path="${test_root}/pinned-manifest.yaml"

  cat >"${source_path}" <<EOF
image: ${ARGOCD_IMAGE_TAG}
image: ${ARGOCD_REDIS_IMAGE_TAG}
EOF

  pin_runtime_images "${source_path}" "${pinned_path}"

  grep -Fqx -- "image: ${ARGOCD_IMAGE}" "${pinned_path}"
  grep -Fqx -- "image: ${ARGOCD_REDIS_IMAGE}" "${pinned_path}"
  if grep -Fq -- "${ARGOCD_IMAGE_TAG}" "${pinned_path}" \
      || grep -Fq -- "${ARGOCD_REDIS_IMAGE_TAG}" "${pinned_path}"; then
    echo "FAIL: Mutable runtime image tag가 pinned manifest에 남으면 안 됩니다." >&2
    exit 1
  fi
}

test_download_rejects_checksum_mismatch() (
  local test_root="$1"
  local destination_path="${test_root}/core-install.yaml"
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
    printf '%s\n' "changed upstream bytes" >"${output_path}"
  }

  set +e
  (download_verified_manifest \
    "https://example.invalid/core-install.yaml" \
    "$(printf '0%.0s' {1..64})" \
    "${destination_path}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 || -e "${destination_path}" ]]; then
    echo "FAIL: checksum이 다른 manifest를 남기거나 적용 대상으로 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_download_uses_bounded_retry() (
  local test_root="$1"
  local destination_path="${test_root}/verified-core-install.yaml"
  local curl_arguments="${test_root}/curl-arguments"
  local expected_checksum

  expected_checksum="$(printf '%s\n' 'verified manifest' | sha256sum | awk '{print $1}')"

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
    printf '%s\n' 'verified manifest' >"${output_path}"
  }

  download_verified_manifest \
    "https://example.invalid/core-install.yaml" \
    "${expected_checksum}" \
    "${destination_path}"

  grep -Fq -- '--connect-timeout 15' "${curl_arguments}"
  grep -Fq -- '--max-time 300' "${curl_arguments}"
  grep -Fq -- '--retry 3' "${curl_arguments}"
  grep -Fq -- '--retry-delay 2' "${curl_arguments}"
  grep -Fq -- '--retry-max-time 300' "${curl_arguments}"
  if grep -Fq -- '--retry-all-errors' "${curl_arguments}"; then
    echo "FAIL: 비복구 HTTP 오류까지 재시도하면 안 됩니다." >&2
    exit 1
  fi
)

test_rejects_other_argocd_version() (
  local execution_status

  resource_exists() { return 0; }
  kubectl_admin() { printf '%s\n' 'quay.io/argoproj/argocd:v3.4.2'; }

  set +e
  (require_component_uses_target_images_if_present \
    deployment/argocd-repo-server >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 기존 다른 Argo CD version을 자동 upgrade하면 안 됩니다." >&2
    exit 1
  fi
)

test_accepts_official_redis_component_images() (
  resource_exists() { return 0; }
  kubectl_admin() {
    if [[ "$*" != *'.spec.template.spec.initContainers[*]'* ]]; then
      printf '%s\n' "${ARGOCD_REDIS_IMAGE}"
      return
    fi
    printf '%s\n' "${ARGOCD_REDIS_IMAGE}" "${ARGOCD_IMAGE}"
  }

  require_component_uses_target_images_if_present deployment/argocd-redis
)

test_verifies_running_image_ids() (
  kubectl_admin() {
    printf '%s\n' "${ARGOCD_IMAGE}" "${ARGOCD_REDIS_IMAGE}"
  }

  verify_running_image_ids
)

test_existing_resource_lookup_failure_is_rejected() (
  local execution_status

  kubectl_admin() { return 77; }

  set +e
  (resource_exists deployment/argocd-repo-server >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 기존 Argo CD resource 조회 실패를 absent로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_rejects_foreign_namespace_owner() (
  local execution_status

  kubectl_admin() {
    if [[ "$*" == *'--ignore-not-found -o name'* ]]; then
      printf '%s\n' 'namespace/argocd'
    else
      printf '%s\n' 'another-owner'
    fi
  }

  set +e
  (require_managed_or_absent_namespace >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 owner의 argocd namespace를 force-conflicts로 인수하면 안 됩니다." >&2
    exit 1
  fi
)

test_main_preserves_installation_order() (
  local test_root="$1"
  local action_log="${test_root}/actions"
  local fixture_work_directory="${test_root}/work"

  parse_arguments() { :; }
  run_preflight_checks() { printf '%s\n' preflight >>"${action_log}"; }
  create_work_directory() {
    mkdir -p "${fixture_work_directory}"
    printf '%s\n' "${fixture_work_directory}"
  }
  download_verified_manifest() { printf '%s\n' download >>"${action_log}"; : >"$3"; }
  pin_runtime_images() { printf '%s\n' pin-images >>"${action_log}"; : >"$2"; }
  apply_argocd_core() { printf '%s\n' apply >>"${action_log}"; }
  wait_for_core_components() { printf '%s\n' wait >>"${action_log}"; }
  verify_postconditions() { printf '%s\n' verify >>"${action_log}"; }

  main >/dev/null

  assert_equals $'preflight\ndownload\npin-images\napply\nwait\nverify' "$(<"${action_log}")" \
    "검증된 manifest 적용 뒤 rollout과 postcondition을 순서대로 확인해야 합니다."
)

run_tests() {
  local test_root
  test_root="$(mktemp -d)"
  trap '[[ -z "${test_root:-}" ]] || rm -rf -- "${test_root}"' EXIT

  test_pins_kubernetes_136_compatible_release
  test_pins_runtime_images_in_verified_manifest "${test_root}"
  test_download_rejects_checksum_mismatch "${test_root}"
  test_download_uses_bounded_retry "${test_root}"
  test_rejects_other_argocd_version
  test_accepts_official_redis_component_images
  test_verifies_running_image_ids
  test_existing_resource_lookup_failure_is_rejected
  test_rejects_foreign_namespace_owner
  test_main_preserves_installation_order "${test_root}"

  echo "PASS: 100-install-argocd-core.sh contract tests"
}

run_tests
