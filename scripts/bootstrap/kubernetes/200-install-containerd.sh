#!/usr/bin/env bash

# Kubernetes v1.36.2 node가 사용할 고정 version의 containerd CRI runtime을 구성합니다.
set -euo pipefail

readonly SUPPORTED_OS_ID="ubuntu"
readonly SUPPORTED_OS_VERSION="24.04"
readonly SUPPORTED_ARCHITECTURE="arm64"
readonly TARGET_KUBERNETES_VERSION="1.36.2"
readonly CONTAINERD_PACKAGE_VERSION="2.2.1-0ubuntu1~24.04.3"
readonly CONTAINERD_CONFIGURATION_PATH="/etc/containerd/config.toml"
readonly CONTAINERD_CONFIGURATION_HEADER="# car-community bootstrap이 관리하는 containerd 설정입니다."
readonly CONTAINERD_CONFIGURATION_BACKUP_PATH="${CONTAINERD_CONFIGURATION_PATH}.before-containerd-bootstrap"
readonly OVERLAY_MODULE_CONFIGURATION_PATH="/etc/modules-load.d/99-car-community-containerd.conf"
readonly OVERLAY_MODULE_CONFIGURATION="overlay"
readonly CONTAINERD_SOCKET_PATH="/run/containerd/containerd.sock"
readonly LOCK_PATH="/run/lock/car-community-containerd-bootstrap.lock"

print_usage() {
  cat <<EOF
사용법: sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/200-install-containerd.sh

Ubuntu ${SUPPORTED_OS_VERSION} ARM64 host에 containerd ${CONTAINERD_PACKAGE_VERSION}을 설치하고
Kubernetes v${TARGET_KUBERNETES_VERSION}용 CRI runtime으로 구성합니다.
CLI option과 positional argument는 받지 않습니다.
EOF
}

fail() {
  echo "오류: $*" >&2
  exit 1
}

parse_arguments() {
  if [[ "$#" -eq 0 ]]; then
    return
  fi

  if [[ "$#" -eq 1 && ( "$1" == "--help" || "$1" == "-h" ) ]]; then
    print_usage
    exit 0
  fi

  print_usage >&2
  fail "지원하지 않는 인자입니다: $*"
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    fail "sudo로 실행해야 합니다."
  fi
}

require_supported_operating_system() {
  if [[ ! -r /etc/os-release ]]; then
    fail "/etc/os-release를 읽을 수 없습니다."
  fi

  # /etc/os-release는 target host의 system file이라 local analysis에서 열 수 없습니다.
  # OS 판정을 repository 소유 parser로 바꾸면 이 suppression을 제거합니다.
  # shellcheck disable=SC1091
  source /etc/os-release

  if [[ "${ID:-}" != "${SUPPORTED_OS_ID}" || "${VERSION_ID:-}" != "${SUPPORTED_OS_VERSION}" ]]; then
    echo "현재 운영체제: ${PRETTY_NAME:-확인 불가}" >&2
    fail "Ubuntu ${SUPPORTED_OS_VERSION}에서만 실행할 수 있습니다."
  fi
}

require_supported_architecture() {
  local architecture

  architecture="$(dpkg --print-architecture)"
  if [[ "${architecture}" != "${SUPPORTED_ARCHITECTURE}" ]]; then
    fail "${SUPPORTED_ARCHITECTURE} architecture에서만 실행할 수 있습니다. 현재 값: ${architecture}"
  fi
}

require_commands() {
  local command_name
  local -a required_commands=(
    apt-get
    apt-mark
    awk
    chmod
    cmp
    cp
    dpkg
    dpkg-query
    flock
    grep
    install
    mktemp
    modprobe
    mv
    rm
    sed
    systemctl
  )

  for command_name in "${required_commands[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      fail "필수 command를 찾을 수 없습니다: ${command_name}"
    fi
  done
}

package_status_is_installed() {
  local package_status="$1"

  [[ "${package_status}" == ?i\  ]]
}

is_package_installed() {
  local package_name="$1"
  local package_status

  package_status="$(dpkg-query -W -f='${db:Status-Abbrev}' "${package_name}" 2>/dev/null || true)"
  package_status_is_installed "${package_status}"
}

installed_package_version() {
  local package_name="$1"

  if ! is_package_installed "${package_name}"; then
    return 1
  fi

  dpkg-query -W -f='${Version}' "${package_name}"
}

require_no_conflicting_runtime_packages() {
  local package_name
  local -a conflicting_packages=(
    containerd.io
    cri-o
    cri-o-runc
    docker-ce
    docker.io
  )

  for package_name in "${conflicting_packages[@]}"; do
    if is_package_installed "${package_name}"; then
      fail "충돌하거나 별도 생애주기를 가진 runtime package가 이미 설치되어 있습니다: ${package_name}"
    fi
  done
}

is_managed_containerd_configuration() {
  local configuration_path="$1"

  [[ -f "${configuration_path}" ]] \
    && [[ "$(sed -n '1p' "${configuration_path}")" == "${CONTAINERD_CONFIGURATION_HEADER}" ]]
}

require_safe_existing_containerd_state() {
  local installed_version=""

  if installed_version="$(installed_package_version containerd 2>/dev/null)"; then
    if [[ "${installed_version}" != "${CONTAINERD_PACKAGE_VERSION}" ]]; then
      fail "다른 containerd package version이 설치되어 있습니다: ${installed_version}"
    fi
  fi

  if [[ -e "${CONTAINERD_CONFIGURATION_PATH}" ]] \
    && ! is_managed_containerd_configuration "${CONTAINERD_CONFIGURATION_PATH}"; then
    fail "기존 ${CONTAINERD_CONFIGURATION_PATH}의 관리 주체를 확인할 수 없어 덮어쓰지 않습니다."
  fi
}

require_no_pending_reboot() {
  if [[ -e /var/run/reboot-required ]]; then
    fail "host 재부팅이 남아 있습니다. EC2를 재부팅하고 100-prepare-host.sh를 다시 확인하세요."
  fi
}

acquire_execution_lock() {
  exec 9>"${LOCK_PATH}"
  if ! flock --nonblock 9; then
    fail "다른 containerd bootstrap 실행이 진행 중입니다: ${LOCK_PATH}"
  fi
}

run_preflight_checks() {
  echo "[사전 확인] 실행 환경과 기존 container runtime 상태를 확인합니다."
  require_root
  require_commands
  require_supported_operating_system
  require_supported_architecture
  acquire_execution_lock
  require_no_pending_reboot
  require_no_conflicting_runtime_packages
  require_safe_existing_containerd_state
}

backup_file_once() (
  local source_path="$1"
  local backup_path="$2"
  local directory_path
  local temporary_path=""

  if [[ -e "${backup_path}" ]]; then
    return
  fi

  directory_path="${backup_path%/*}"
  [[ -n "${directory_path}" ]] || directory_path="."
  temporary_path="$(mktemp "${directory_path}/.200-install-containerd-backup.XXXXXX")"
  trap '[[ -z "${temporary_path}" ]] || rm -f -- "${temporary_path}"' EXIT

  if ! cp -p -- "${source_path}" "${temporary_path}"; then
    echo "백업 원본을 복사하지 못했습니다: ${source_path}" >&2
    return 1
  fi

  mv -- "${temporary_path}" "${backup_path}"
  temporary_path=""
)

install_containerd_package() {
  apt-get install \
    --yes \
    --no-install-recommends \
    "containerd=${CONTAINERD_PACKAGE_VERSION}"
}

has_desired_overlay_module_configuration() {
  local configuration_path="$1"

  [[ -f "${configuration_path}" ]] \
    && [[ "$(<"${configuration_path}")" == "${OVERLAY_MODULE_CONFIGURATION}" ]]
}

write_overlay_module_configuration() (
  local configuration_path="$1"
  local backup_path="${configuration_path}.before-containerd-bootstrap"
  local directory_path
  local temporary_path=""

  if has_desired_overlay_module_configuration "${configuration_path}"; then
    echo "overlay module 영구 설정이 이미 원하는 상태입니다."
    return
  fi

  directory_path="${configuration_path%/*}"
  [[ -n "${directory_path}" ]] || directory_path="."
  temporary_path="$(mktemp "${directory_path}/.200-install-containerd.XXXXXX")"
  trap '[[ -z "${temporary_path}" ]] || rm -f -- "${temporary_path}"' EXIT

  if [[ -e "${configuration_path}" ]]; then
    backup_file_once "${configuration_path}" "${backup_path}"
  fi

  printf '%s\n' "${OVERLAY_MODULE_CONFIGURATION}" >"${temporary_path}"
  chmod 0644 "${temporary_path}"
  mv -- "${temporary_path}" "${configuration_path}"
  temporary_path=""

  echo "overlay module 영구 설정을 ${configuration_path}에 기록했습니다."
)

configure_overlay_module() {
  write_overlay_module_configuration "${OVERLAY_MODULE_CONFIGURATION_PATH}"
  modprobe overlay

  if [[ ! -d /sys/module/overlay ]]; then
    fail "overlay kernel module이 활성화되지 않았습니다."
  fi
}

render_containerd_configuration() {
  local rendered_configuration_path="$1"

  printf '%s\n\n%s\n\n%s\n%s\n' \
    "${CONTAINERD_CONFIGURATION_HEADER}" \
    "version = 3" \
    "[plugins.'io.containerd.cri.v1.runtime'.containerd.runtimes.runc.options]" \
    "  SystemdCgroup = true" \
    >"${rendered_configuration_path}"
}

install_containerd_configuration() (
  local configuration_path="$1"
  local directory_path="${configuration_path%/*}"
  local rendered_configuration_path=""

  install -d -m 0755 "${directory_path}"
  rendered_configuration_path="$(mktemp "${directory_path}/.200-containerd-rendered.XXXXXX")"
  trap '[[ -z "${rendered_configuration_path}" ]] || rm -f -- "${rendered_configuration_path}"' EXIT

  render_containerd_configuration "${rendered_configuration_path}"
  chmod 0644 "${rendered_configuration_path}"
  if ! containerd --config "${rendered_configuration_path}" config dump >/dev/null; then
    fail "생성한 containerd 설정을 containerd가 읽지 못했습니다."
  fi

  if [[ -e "${configuration_path}" ]]; then
    if cmp --silent "${rendered_configuration_path}" "${configuration_path}"; then
      chmod 0644 "${configuration_path}"
      echo "containerd 설정이 이미 원하는 상태입니다."
      return
    fi

    if ! is_managed_containerd_configuration "${configuration_path}"; then
      fail "기존 ${configuration_path}의 관리 주체를 확인할 수 없어 덮어쓰지 않습니다."
    fi

    backup_file_once "${configuration_path}" "${CONTAINERD_CONFIGURATION_BACKUP_PATH}"
  fi

  mv -- "${rendered_configuration_path}" "${configuration_path}"
  rendered_configuration_path=""
  echo "containerd CRI 설정을 ${configuration_path}에 기록했습니다."
)

containerd_cri_plugins_are_healthy() {
  local plugin_output="$1"

  printf '%s\n' "${plugin_output}" \
    | awk '
      $1 == "io.containerd.cri.v1" && $2 == "images" && $NF == "ok" { images_ok = 1 }
      $1 == "io.containerd.cri.v1" && $2 == "runtime" && $NF == "ok" { runtime_ok = 1 }
      END { exit(images_ok && runtime_ok ? 0 : 1) }
    '
}

enable_containerd_service() {
  systemctl enable containerd
  systemctl restart containerd
}

hold_containerd_package() {
  apt-mark hold containerd
}

verify_containerd_postconditions() {
  local installed_version
  local plugin_output
  local command_name
  local -a installed_commands=(containerd ctr runc)

  echo "[검증] containerd package, 설정, service와 CRI 상태를 확인합니다."

  installed_version="$(installed_package_version containerd 2>/dev/null || true)"
  if [[ "${installed_version}" != "${CONTAINERD_PACKAGE_VERSION}" ]]; then
    fail "설치된 containerd package version이 다릅니다: ${installed_version:-설치되지 않음}"
  fi

  for command_name in "${installed_commands[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      fail "containerd package 설치 후 필수 command를 찾을 수 없습니다: ${command_name}"
    fi
  done

  if ! apt-mark showhold | grep -Fqx "containerd"; then
    fail "containerd package가 hold 상태가 아닙니다."
  fi

  if ! has_desired_overlay_module_configuration "${OVERLAY_MODULE_CONFIGURATION_PATH}" \
    || [[ ! -d /sys/module/overlay ]]; then
    fail "overlay module의 영구 또는 현재 설정이 완료되지 않았습니다."
  fi

  if ! is_managed_containerd_configuration "${CONTAINERD_CONFIGURATION_PATH}" \
    || ! grep -Eq '^[[:space:]]*SystemdCgroup[[:space:]]*=[[:space:]]*true[[:space:]]*$' \
      "${CONTAINERD_CONFIGURATION_PATH}"; then
    fail "containerd의 project 관리 설정 또는 systemd cgroup 설정이 올바르지 않습니다."
  fi

  if ! systemctl is-enabled --quiet containerd; then
    fail "containerd service가 enabled 상태가 아닙니다."
  fi

  if ! systemctl is-active --quiet containerd; then
    fail "containerd service가 active 상태가 아닙니다."
  fi

  if [[ ! -S "${CONTAINERD_SOCKET_PATH}" ]]; then
    fail "containerd CRI socket을 찾을 수 없습니다: ${CONTAINERD_SOCKET_PATH}"
  fi

  if ! plugin_output="$(ctr --address "${CONTAINERD_SOCKET_PATH}" plugins ls 2>&1)"; then
    fail "containerd plugin 상태를 조회하지 못했습니다."
  fi

  if ! containerd_cri_plugins_are_healthy "${plugin_output}"; then
    printf '%s\n' "${plugin_output}" >&2
    fail "containerd CRI images·runtime plugin이 모두 정상 상태가 아닙니다."
  fi

  echo "containerd ${CONTAINERD_PACKAGE_VERSION} CRI runtime 검증을 통과했습니다."
}

main() {
  parse_arguments "$@"
  run_preflight_checks

  export DEBIAN_FRONTEND=noninteractive

  echo "[계획] Kubernetes v${TARGET_KUBERNETES_VERSION}용 containerd ${CONTAINERD_PACKAGE_VERSION}을 구성합니다."
  echo "[변경] Ubuntu package 색인을 갱신합니다."
  apt-get update

  echo "[변경] 고정 version의 containerd package를 설치합니다."
  install_containerd_package

  echo "[변경] containerd package를 의도하지 않은 자동 upgrade에서 제외합니다."
  hold_containerd_package

  echo "[변경] overlay snapshotter의 kernel module을 구성합니다."
  configure_overlay_module

  echo "[변경] containerd CRI와 systemd cgroup driver를 구성합니다."
  install_containerd_configuration "${CONTAINERD_CONFIGURATION_PATH}"

  echo "[변경] containerd service를 활성화하고 다시 시작합니다."
  enable_containerd_service

  verify_containerd_postconditions
  echo "[보고] 200-install-containerd.sh 단계가 완료됐습니다. Kubernetes component 설치는 별도 단계입니다."
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
