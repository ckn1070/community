#!/usr/bin/env bash

set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly SCRIPT_PATH="${TEST_DIRECTORY}/../200-install-containerd.sh"
TEST_ROOT=""

# shellcheck source=../200-install-containerd.sh
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

test_backup_file_once_publishes_only_complete_backup() (
  local test_root="$1"
  local source_path="${test_root}/backup-source"
  local backup_path="${test_root}/backup-target"

  printf '%s\n' "original" >"${source_path}"
  backup_file_once "${source_path}" "${backup_path}"
  assert_equals "original" "$(<"${backup_path}")" "완성된 backup만 최종 경로에 게시해야 합니다."

  printf '%s\n' "changed" >"${source_path}"
  backup_file_once "${source_path}" "${backup_path}"
  assert_equals "original" "$(<"${backup_path}")" "완성된 최초 backup을 재실행으로 덮어쓰면 안 됩니다."

  rm -- "${backup_path}"

  cp() {
    local destination_path

    for destination_path in "$@"; do
      :
    done
    printf '%s\n' "partial" >"${destination_path}"
    return 1
  }

  if backup_file_once "${source_path}" "${backup_path}" 2>/dev/null; then
    echo "FAIL: 일부만 복사한 뒤 실패한 backup을 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi

  if [[ -e "${backup_path}" ]]; then
    echo "FAIL: 일부만 복사한 뒤 실패하면 최종 backup 경로를 남기면 안 됩니다." >&2
    exit 1
  fi
)

test_overlay_temporary_files_are_cleaned_when_backup_fails() {
  local test_root="$1"
  local configuration_path="${test_root}/modules-with-backup-failure.conf"
  local execution_status
  local -a temporary_files

  printf '%s\n' "legacy-module" >"${configuration_path}"

  set +e
  (
    set -e
    cp() {
      local destination_path

      for destination_path in "$@"; do
        :
      done
      printf '%s\n' "partial" >"${destination_path}"
      return 1
    }

    write_overlay_module_configuration "${configuration_path}" >/dev/null 2>&1
  )
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: backup 실패를 overlay module 설정 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi

  shopt -s nullglob
  temporary_files=("${test_root}"/.200-install-containerd.*)
  shopt -u nullglob
  if [[ "${#temporary_files[@]}" -ne 0 ]]; then
    echo "FAIL: backup 실패 뒤 overlay module 설정용 임시 file을 남기면 안 됩니다." >&2
    exit 1
  fi
}

test_containerd_temporary_file_is_cleaned_when_validation_fails() (
  local test_root="$1"
  local configuration_directory="${test_root}/containerd-with-validation-failure"
  local configuration_path="${configuration_directory}/config.toml"
  local execution_status
  local -a temporary_files

  containerd() {
    return 1
  }

  set +e
  install_containerd_configuration "${configuration_path}" >/dev/null 2>&1
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: containerd 설정 검증 실패를 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi

  shopt -s nullglob
  temporary_files=("${configuration_directory}"/.200-containerd-rendered.*)
  shopt -u nullglob
  if [[ "${#temporary_files[@]}" -ne 0 ]]; then
    echo "FAIL: containerd 설정 검증 실패 뒤 임시 file을 남기면 안 됩니다." >&2
    exit 1
  fi

  if [[ -e "${configuration_path}" ]]; then
    echo "FAIL: 검증하지 못한 containerd 설정을 최종 경로에 게시하면 안 됩니다." >&2
    exit 1
  fi
)

test_main_holds_containerd_before_runtime_configuration() (
  local test_root="$1"
  local action_log="${test_root}/containerd-action-order"

  run_preflight_checks() { :; }
  apt-get() { [[ "$1" == "update" ]]; }
  install_containerd_package() { printf '%s\n' "install" >>"${action_log}"; }
  hold_containerd_package() { printf '%s\n' "hold" >>"${action_log}"; }
  configure_overlay_module() { printf '%s\n' "later-mutation" >>"${action_log}"; }
  install_containerd_configuration() { :; }
  enable_containerd_service() { :; }
  verify_containerd_postconditions() { :; }

  main >/dev/null

  assert_equals \
    $'install\nhold\nlater-mutation' \
    "$(<"${action_log}")" \
    "containerd package를 hold한 뒤 후속 runtime 설정을 변경해야 합니다."
)

test_install_uses_exact_containerd_package_version() (
  apt-get() { printf '%s\n' "$*"; }

  assert_equals \
    "install --yes --no-install-recommends containerd=${CONTAINERD_PACKAGE_VERSION}" \
    "$(install_containerd_package)" \
    "이미 설치된 재실행도 containerd exact package spec을 APT에 전달해야 합니다."
)

test_rejects_different_installed_containerd_version() (
  local execution_status

  installed_package_version() { printf '%s\n' "2.3.0-1"; }

  set +e
  (require_safe_existing_containerd_state >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 containerd package version을 자동 교체하면 안 됩니다." >&2
    exit 1
  fi
)

test_docker_cli_does_not_block_runtime_installation() (
  is_package_installed() {
    [[ "$1" == "docker-ce-cli" ]]
  }

  require_no_conflicting_runtime_packages
)

test_docker_runtime_blocks_containerd_installation() (
  local execution_status

  is_package_installed() {
    [[ "$1" == "docker-ce" ]]
  }

  set +e
  (require_no_conflicting_runtime_packages >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 별도 생애주기의 Docker runtime을 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_install_writes_minimal_containerd_configuration() (
  local test_root="$1"
  local configuration_path="${test_root}/minimal-containerd/config.toml"
  local expected_configuration

  expected_configuration="${CONTAINERD_CONFIGURATION_HEADER}"$'\n\nversion = 3\n\n[plugins.\'io.containerd.cri.v1.runtime\'.containerd.runtimes.runc.options]\n  SystemdCgroup = true'

  containerd() {
    [[ "$1" == "--config" && "$3" == "config" && "$4" == "dump" ]]
  }

  install_containerd_configuration "${configuration_path}" >/dev/null

  assert_equals \
    "${expected_configuration}" \
    "$(<"${configuration_path}")" \
    "containerd 설정은 필요한 version과 systemd cgroup option만 명시해야 합니다."

  install_containerd_configuration "${configuration_path}" >/dev/null
  assert_equals \
    "${expected_configuration}" \
    "$(<"${configuration_path}")" \
    "containerd 설정을 다시 적용해도 최소 설정이 유지되어야 합니다."

  if [[ -e "${configuration_path}.before-containerd-bootstrap" ]]; then
    echo "FAIL: 같은 containerd 설정을 재실행하면서 불필요한 backup을 만들면 안 됩니다." >&2
    exit 1
  fi
)

test_write_overlay_module_configuration() {
  local test_root="$1"
  local configuration_path="${test_root}/containerd-modules.conf"
  local first_backup_checksum

  printf '%s\n' "legacy-module" >"${configuration_path}"
  write_overlay_module_configuration "${configuration_path}" >/dev/null

  assert_equals \
    "${OVERLAY_MODULE_CONFIGURATION}" \
    "$(<"${configuration_path}")" \
    "overlay module 영구 설정이 정확해야 합니다."

  first_backup_checksum="$(cksum "${configuration_path}.before-containerd-bootstrap")"
  write_overlay_module_configuration "${configuration_path}" >/dev/null
  assert_equals \
    "${first_backup_checksum}" \
    "$(cksum "${configuration_path}.before-containerd-bootstrap")" \
    "재실행 시 최초 module 설정 backup을 덮어쓰면 안 됩니다."
}

test_managed_configuration_detection() {
  local test_root="$1"
  local managed_path="${test_root}/managed.toml"
  local unmanaged_path="${test_root}/unmanaged.toml"

  printf '%s\n' "${CONTAINERD_CONFIGURATION_HEADER}" "version = 3" >"${managed_path}"
  printf '%s\n' "version = 3" >"${unmanaged_path}"

  if ! is_managed_containerd_configuration "${managed_path}"; then
    echo "FAIL: bootstrap이 생성한 containerd 설정을 식별해야 합니다." >&2
    exit 1
  fi

  if is_managed_containerd_configuration "${unmanaged_path}"; then
    echo "FAIL: 관리 표식이 없는 containerd 설정을 소유한 것으로 판단하면 안 됩니다." >&2
    exit 1
  fi
}

test_cri_plugin_health_detection() {
  local healthy_plugins
  local missing_images_plugin
  local unhealthy_runtime_plugin

  healthy_plugins=$'TYPE                            ID        PLATFORMS      STATUS\nio.containerd.cri.v1           images    linux/arm64    ok\nio.containerd.cri.v1           runtime   linux/arm64    ok'
  missing_images_plugin=$'TYPE                            ID        PLATFORMS      STATUS\nio.containerd.cri.v1           runtime   linux/arm64    ok'
  unhealthy_runtime_plugin=$'TYPE                            ID        PLATFORMS      STATUS\nio.containerd.cri.v1           images    linux/arm64    ok\nio.containerd.cri.v1           runtime   linux/arm64    error'

  if ! containerd_cri_plugins_are_healthy "${healthy_plugins}"; then
    echo "FAIL: 정상 CRI images·runtime plugin을 식별해야 합니다." >&2
    exit 1
  fi

  if containerd_cri_plugins_are_healthy "${missing_images_plugin}"; then
    echo "FAIL: images plugin이 없는 상태를 통과시키면 안 됩니다." >&2
    exit 1
  fi

  if containerd_cri_plugins_are_healthy "${unhealthy_runtime_plugin}"; then
    echo "FAIL: 비정상 CRI runtime plugin을 통과시키면 안 됩니다." >&2
    exit 1
  fi
}

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
    "2.2.1-0ubuntu1~24.04.3" \
    "${CONTAINERD_PACKAGE_VERSION}" \
    "Ubuntu containerd package version을 고정해야 합니다."
  test_backup_file_once_publishes_only_complete_backup "${TEST_ROOT}"
  test_overlay_temporary_files_are_cleaned_when_backup_fails "${TEST_ROOT}"
  test_containerd_temporary_file_is_cleaned_when_validation_fails "${TEST_ROOT}"
  test_main_holds_containerd_before_runtime_configuration "${TEST_ROOT}"
  test_install_uses_exact_containerd_package_version
  test_rejects_different_installed_containerd_version
  test_docker_cli_does_not_block_runtime_installation
  test_docker_runtime_blocks_containerd_installation
  test_install_writes_minimal_containerd_configuration "${TEST_ROOT}"
  test_write_overlay_module_configuration "${TEST_ROOT}"
  test_managed_configuration_detection "${TEST_ROOT}"
  test_cri_plugin_health_detection
  test_package_status_detection

  echo "PASS: 200-install-containerd.sh contract tests"
}

run_tests "$@"
