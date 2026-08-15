#!/usr/bin/env bash

set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly SCRIPT_PATH="${TEST_DIRECTORY}/../300-install-kubernetes-components.sh"
TEST_ROOT=""

# shellcheck source=../300-install-kubernetes-components.sh
source "${SCRIPT_PATH}"

assert_equals() {
  local expected="$1"
  local actual="$2"
  local message="$3"

  if [[ "${expected}" != "${actual}" ]]; then
    echo "FAIL: ${message}" >&2
    echo "expected: ${expected}" >&2
    echo "actual:   ${actual}" >&2
    exit 1
  fi
}

assert_file_contains() {
  local file_path="$1"
  local expected_line="$2"
  local message="$3"

  if ! grep -Fqx "${expected_line}" "${file_path}"; then
    echo "FAIL: ${message}" >&2
    exit 1
  fi
}

file_mode() {
  local file_path="$1"

  if stat -c '%a' "${file_path}" 2>/dev/null; then
    return
  fi
  stat -f '%Lp' "${file_path}"
}

test_repository_configuration() {
  local test_root="$1"
  local repository_path="${test_root}/kubernetes.list"
  local first_checksum

  write_kubernetes_repository_configuration "${repository_path}" >/dev/null
  assert_equals \
    "${KUBERNETES_REPOSITORY_CONFIGURATION}" \
    "$(<"${repository_path}")" \
    "Kubernetes official minor repository 설정이 정확해야 합니다."

  first_checksum="$(cksum "${repository_path}")"
  chmod 0600 "${repository_path}"
  write_kubernetes_repository_configuration "${repository_path}" >/dev/null
  assert_equals \
    "${first_checksum}" \
    "$(cksum "${repository_path}")" \
    "동일한 repository 설정의 재실행은 file을 다시 교체하지 않아야 합니다."
  assert_equals \
    "644" \
    "$(file_mode "${repository_path}")" \
    "동일한 repository 설정의 재실행은 final file mode를 복구해야 합니다."

  printf '%s\n' "deb https://example.invalid/kubernetes /" >"${repository_path}"
  if write_kubernetes_repository_configuration "${repository_path}" >/dev/null 2>&1; then
    echo "FAIL: 관리 주체를 확인할 수 없는 repository 설정을 덮어쓰면 안 됩니다." >&2
    exit 1
  fi
  assert_file_contains \
    "${repository_path}" \
    "deb https://example.invalid/kubernetes /" \
    "거부한 repository 설정은 원문을 보존해야 합니다."
}

test_existing_exact_keyring_mode_is_repaired() {
  local test_root="$1"
  local keyring_directory="${test_root}/keyring-with-wrong-mode"
  local keyring_path="${keyring_directory}/kubernetes-apt-keyring.gpg"

  mkdir -p "${keyring_directory}"
  printf '%s\n' "official-keyring" >"${keyring_path}"
  chmod 0600 "${keyring_path}"

  (
    curl() {
      printf '%s\n' "official-release-key"
    }
    gpg() {
      local argument
      local output_path=""
      local write_next_argument=0

      for argument in "$@"; do
        if [[ "${write_next_argument}" -eq 1 ]]; then
          output_path="${argument}"
          break
        fi
        if [[ "${argument}" == "--output" ]]; then
          write_next_argument=1
        fi
      done
      printf '%s\n' "official-keyring" >"${output_path}"
    }

    install_kubernetes_keyring \
      "https://example.invalid/Release.key" \
      "${keyring_path}" >/dev/null
  )

  assert_equals \
    "official-keyring" \
    "$(<"${keyring_path}")" \
    "동일한 official keyring의 재실행은 final content를 보존해야 합니다."
  assert_equals \
    "644" \
    "$(file_mode "${keyring_path}")" \
    "동일한 official keyring의 재실행은 final file mode를 복구해야 합니다."
}

test_existing_keyring_is_preserved_when_dearmor_fails() {
  local test_root="$1"
  local keyring_directory="${test_root}/keyring-with-dearmor-failure"
  local keyring_path="${keyring_directory}/kubernetes-apt-keyring.gpg"
  local execution_status
  local -a temporary_files

  mkdir -p "${keyring_directory}"
  printf '%s\n' "existing-keyring" >"${keyring_path}"

  set +e
  (
    set -e
    curl() {
      printf '%s\n' "official-release-key"
    }
    gpg() {
      local argument
      local output_path=""
      local write_next_argument=0

      for argument in "$@"; do
        if [[ "${write_next_argument}" -eq 1 ]]; then
          output_path="${argument}"
          break
        fi
        if [[ "${argument}" == "--output" ]]; then
          write_next_argument=1
        fi
      done
      printf '%s\n' "partial-keyring" >"${output_path}"
      return 1
    }

    install_kubernetes_keyring \
      "https://example.invalid/Release.key" \
      "${keyring_path}" >/dev/null 2>&1
  )
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: signing key dearmor 실패를 keyring 설치 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi
  assert_equals \
    "existing-keyring" \
    "$(<"${keyring_path}")" \
    "signing key dearmor 실패는 기존 final keyring을 보존해야 합니다."

  shopt -s nullglob
  temporary_files=("${keyring_directory}"/.300-kubernetes-keyring.*)
  shopt -u nullglob
  if [[ "${#temporary_files[@]}" -ne 0 ]]; then
    echo "FAIL: signing key dearmor 실패 뒤 임시 keyring을 남기면 안 됩니다." >&2
    exit 1
  fi
}

test_existing_keyring_is_preserved_when_download_fails() {
  local test_root="$1"
  local keyring_directory="${test_root}/keyring-with-download-failure"
  local keyring_path="${keyring_directory}/kubernetes-apt-keyring.gpg"
  local execution_status
  local -a temporary_files

  mkdir -p "${keyring_directory}"
  printf '%s\n' "existing-keyring" >"${keyring_path}"

  set +e
  (
    set -e
    curl() {
      printf '%s\n' "partial-release-key"
      return 1
    }
    gpg() {
      local argument
      local output_path=""
      local write_next_argument=0

      for argument in "$@"; do
        if [[ "${write_next_argument}" -eq 1 ]]; then
          output_path="${argument}"
          break
        fi
        if [[ "${argument}" == "--output" ]]; then
          write_next_argument=1
        fi
      done
      printf '%s\n' "partial-keyring" >"${output_path}"
    }

    install_kubernetes_keyring \
      "https://example.invalid/Release.key" \
      "${keyring_path}" >/dev/null 2>&1
  )
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: signing key download 실패를 keyring 설치 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi
  assert_equals \
    "existing-keyring" \
    "$(<"${keyring_path}")" \
    "signing key download 실패는 기존 final keyring을 보존해야 합니다."

  shopt -s nullglob
  temporary_files=("${keyring_directory}"/.300-kubernetes-keyring.*)
  shopt -u nullglob
  if [[ "${#temporary_files[@]}" -ne 0 ]]; then
    echo "FAIL: signing key download 실패 뒤 임시 file을 남기면 안 됩니다." >&2
    exit 1
  fi
}

test_rejects_different_installed_kubernetes_version() (
  local execution_status

  installed_package_version() {
    if [[ "$1" == "kubelet" ]]; then
      printf '%s\n' "1.36.3-1.1"
      return
    fi
    return 1
  }

  set +e
  (require_safe_existing_kubernetes_package_state >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 Kubernetes package version을 자동 교체하면 안 됩니다." >&2
    exit 1
  fi
)

test_rejects_different_transitional_kubernetes_version() (
  local execution_status

  installed_package_version() { return 1; }
  package_status_abbreviation() {
    if [[ "$1" == "kubelet" ]]; then
      printf '%s\n' "iF "
      return
    fi
    return 1
  }
  recorded_package_version() {
    if [[ "$1" == "kubelet" ]]; then
      printf '%s\n' "1.36.3-1.1"
      return
    fi
    return 1
  }

  set +e
  (require_safe_existing_kubernetes_package_state >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 완료되지 않은 다른 Kubernetes package version을 자동 교체하면 안 됩니다." >&2
    exit 1
  fi
)

test_accepts_exact_transitional_kubernetes_version_for_recovery() (
  installed_package_version() { return 1; }
  package_status_abbreviation() {
    if [[ "$1" == "kubelet" ]]; then
      printf '%s\n' "iF "
      return
    fi
    return 1
  }
  recorded_package_version() {
    if [[ "$1" == "kubelet" ]]; then
      printf '%s\n' "${KUBERNETES_PACKAGE_VERSION}"
      return
    fi
    return 1
  }

  require_safe_existing_kubernetes_package_state
)

test_install_uses_exact_kubernetes_package_versions() (
  apt-get() { printf '%s\n' "$*"; }

  assert_equals \
    "install --yes kubelet=${KUBERNETES_PACKAGE_VERSION} kubeadm=${KUBERNETES_PACKAGE_VERSION} kubectl=${KUBERNETES_PACKAGE_VERSION}" \
    "$(install_kubernetes_packages)" \
    "세 Kubernetes component를 모두 exact version으로 요청해야 합니다."
)

test_main_publishes_current_key_before_repository_update() (
  local test_root="$1"
  local action_log="${test_root}/kubernetes-key-update-order"

  run_preflight_checks() { :; }
  apt-get() { printf '%s\n' "apt-get:$*" >>"${action_log}"; }
  install_repository_dependencies() { :; }
  require_repository_commands() { :; }
  install_kubernetes_keyring() { printf '%s\n' "keyring" >>"${action_log}"; }
  write_kubernetes_repository_configuration() { printf '%s\n' "repository" >>"${action_log}"; }
  install_kubernetes_packages() { :; }
  hold_all_kubernetes_packages() { :; }
  enable_kubelet_service() { :; }
  verify_kubernetes_component_postconditions() { :; }

  main >/dev/null

  assert_equals \
    $'keyring\nrepository\napt-get:update' \
    "$(<"${action_log}")" \
    "기존 key가 rotation돼도 새 keyring을 게시한 뒤 repository index를 갱신해야 합니다."
)

test_main_holds_packages_immediately_after_install() (
  local test_root="$1"
  local action_log="${test_root}/kubernetes-component-action-order"

  run_preflight_checks() { :; }
  apt-get() { :; }
  install_repository_dependencies() { :; }
  require_repository_commands() { :; }
  install_kubernetes_keyring() { :; }
  write_kubernetes_repository_configuration() { :; }
  install_kubernetes_packages() { printf '%s\n' "install" >>"${action_log}"; }
  hold_installed_target_kubernetes_packages() { printf '%s\n' "partial-hold" >>"${action_log}"; }
  hold_all_kubernetes_packages() { printf '%s\n' "hold" >>"${action_log}"; }
  enable_kubelet_service() { printf '%s\n' "enable" >>"${action_log}"; }
  verify_kubernetes_component_postconditions() { :; }

  main >/dev/null

  assert_equals \
    $'install\nhold\nenable' \
    "$(<"${action_log}")" \
    "exact package 설치 직후 모두 hold한 다음 kubelet을 enable해야 합니다."
)

test_main_holds_partial_exact_install_after_apt_failure() (
  local test_root="$1"
  local action_log="${test_root}/kubernetes-component-install-failure-order"
  local execution_status

  run_preflight_checks() { :; }
  apt-get() { :; }
  install_repository_dependencies() { :; }
  require_repository_commands() { :; }
  install_kubernetes_keyring() { :; }
  write_kubernetes_repository_configuration() { :; }
  install_kubernetes_packages() {
    printf '%s\n' "install-failed" >>"${action_log}"
    return 1
  }
  hold_installed_target_kubernetes_packages() {
    printf '%s\n' "partial-hold" >>"${action_log}"
  }
  hold_all_kubernetes_packages() {
    echo "FAIL: 실패한 전체 설치를 모두 설치된 것으로 hold하면 안 됩니다." >&2
    return 1
  }
  enable_kubelet_service() { :; }
  verify_kubernetes_component_postconditions() { :; }

  set +e
  (main >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: Kubernetes package 설치 실패를 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi
  assert_equals \
    $'install-failed\npartial-hold' \
    "$(<"${action_log}")" \
    "부분 설치된 exact package를 hold한 뒤 실패를 보고해야 합니다."
)

test_kubelet_is_enabled_without_forcing_pre_init_active_state() (
  systemctl() { printf '%s\n' "$*"; }

  assert_equals \
    "enable kubelet" \
    "$(enable_kubelet_service)" \
    "kubelet은 재부팅 뒤 시작되도록 enable하되 init 전에 start·restart를 강제하지 않아야 합니다."
)

test_package_status_detection() {
  if ! package_status_is_installed "ii "; then
    echo "FAIL: 일반 설치 상태의 package를 식별해야 합니다." >&2
    exit 1
  fi

  if ! package_status_is_installed "hi "; then
    echo "FAIL: hold된 설치 package를 식별해야 합니다." >&2
    exit 1
  fi

  if package_status_is_installed "rc "; then
    echo "FAIL: config만 남은 제거 package를 설치 상태로 판단하면 안 됩니다." >&2
    exit 1
  fi
}

cleanup_test_root() {
  if [[ -n "${TEST_ROOT}" ]]; then
    rm -rf -- "${TEST_ROOT}"
  fi
}

run_tests() {
  TEST_ROOT="$(mktemp -d)"
  trap cleanup_test_root EXIT

  assert_equals "1.36.2" "${TARGET_KUBERNETES_VERSION}" "Kubernetes 목표 version을 고정해야 합니다."
  assert_equals \
    "1.36.2-2.1" \
    "${KUBERNETES_PACKAGE_VERSION}" \
    "Kubernetes Debian package version을 고정해야 합니다."
  assert_equals \
    "https://pkgs.k8s.io/core:/stable:/v1.36/deb/" \
    "${KUBERNETES_REPOSITORY_URL}" \
    "Kubernetes v1.36 official repository를 사용해야 합니다."
  assert_equals \
    "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.36/deb/ /" \
    "${KUBERNETES_REPOSITORY_CONFIGURATION}" \
    "Kubernetes repository가 project keyring을 명시해야 합니다."

  test_repository_configuration "${TEST_ROOT}"
  test_existing_exact_keyring_mode_is_repaired "${TEST_ROOT}"
  test_existing_keyring_is_preserved_when_dearmor_fails "${TEST_ROOT}"
  test_existing_keyring_is_preserved_when_download_fails "${TEST_ROOT}"
  test_rejects_different_installed_kubernetes_version
  test_rejects_different_transitional_kubernetes_version
  test_accepts_exact_transitional_kubernetes_version_for_recovery
  test_install_uses_exact_kubernetes_package_versions
  test_main_publishes_current_key_before_repository_update "${TEST_ROOT}"
  test_main_holds_packages_immediately_after_install "${TEST_ROOT}"
  test_main_holds_partial_exact_install_after_apt_failure "${TEST_ROOT}"
  test_kubelet_is_enabled_without_forcing_pre_init_active_state
  test_package_status_detection

  echo "PASS: 300-install-kubernetes-components.sh contract tests"
}

run_tests "$@"
