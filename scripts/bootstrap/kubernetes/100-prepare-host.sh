#!/usr/bin/env bash

# Ubuntu 24.04를 Kubernetes node로 사용하기 위한 운영체제 선행 조건을 구성합니다.
set -euo pipefail

readonly SUPPORTED_OS_ID="ubuntu"
readonly SUPPORTED_OS_VERSION="24.04"
readonly FSTAB_PATH="/etc/fstab"
readonly IPV4_FORWARDING_CONFIGURATION_PATH="/etc/sysctl.d/99-car-community-kubernetes.conf"
readonly IPV4_FORWARDING_CONFIGURATION="net.ipv4.ip_forward = 1"

print_usage() {
  cat <<'EOF'
사용법: sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/100-prepare-host.sh

Ubuntu 24.04 host의 package를 갱신하고 Kubernetes node 선행 조건을 구성합니다.
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

require_commands() {
  local command_name
  local -a required_commands=(
    apt-get
    awk
    cat
    chmod
    cp
    mktemp
    mv
    rm
    swapoff
    swapon
    sysctl
    systemctl
    timedatectl
  )

  for command_name in "${required_commands[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      fail "필수 command를 찾을 수 없습니다: ${command_name}"
    fi
  done
}

verify_time_synchronization() {
  local synchronization_state

  if ! synchronization_state="$(timedatectl show --property=NTPSynchronized --value 2>/dev/null)"; then
    fail "timedatectl로 시간 동기화 상태를 확인하지 못했습니다."
  fi

  if [[ "${synchronization_state}" != "yes" ]]; then
    fail "system clock이 동기화되지 않았습니다. 시간 동기화 원인을 확인한 뒤 다시 실행하세요."
  fi

  echo "system clock 동기화 상태를 확인했습니다."
}

classify_ufw_status() {
  local status_output="$1"
  local first_line

  first_line="$(printf '%s\n' "${status_output}" | awk 'NR == 1 { print; exit }')"
  case "${first_line}" in
    "Status: inactive")
      printf '%s\n' "inactive"
      ;;
    "Status: active")
      printf '%s\n' "active"
      ;;
    *)
      printf '%s\n' "unknown"
      ;;
  esac
}

verify_ufw_state() {
  local status_output
  local status

  if ! command -v ufw >/dev/null 2>&1; then
    echo "UFW가 설치되어 있지 않아 UFW rule 확인을 건너뜁니다."
    return
  fi

  if ! status_output="$(LC_ALL=C ufw status 2>&1)"; then
    fail "UFW 상태를 확인하지 못했습니다."
  fi

  status="$(classify_ufw_status "${status_output}")"
  case "${status}" in
    inactive)
      echo "UFW가 비활성 상태임을 확인했습니다."
      ;;
    active)
      fail "UFW가 활성 상태입니다. 필요한 Kubernetes port를 확정하기 전에는 rule을 자동 변경하지 않습니다."
      ;;
    *)
      fail "UFW 상태를 판정하지 못했습니다. 'sudo ufw status' 결과를 직접 확인하세요."
      ;;
  esac
}

find_enabled_systemd_swap_units() {
  systemctl list-unit-files \
    --no-legend \
    --no-pager \
    | awk '$1 ~ /\.swap$/ && ($2 == "enabled" || $2 == "enabled-runtime") { print $1 }'
}

verify_no_enabled_systemd_swap_units() {
  local enabled_swap_units

  if ! enabled_swap_units="$(find_enabled_systemd_swap_units)"; then
    fail "systemd swap unit 상태를 확인하지 못했습니다."
  fi

  if [[ -n "${enabled_swap_units}" ]]; then
    echo "재부팅 후 swap을 활성화할 systemd unit이 있습니다:" >&2
    printf '%s\n' "${enabled_swap_units}" >&2
    fail "해당 unit의 용도를 확인하고 명시적으로 비활성화한 뒤 다시 실행하세요."
  fi
}

run_preflight_checks() {
  echo "[사전 확인] 실행 환경과 변경 전 조건을 확인합니다."
  require_root
  require_supported_operating_system
  require_commands
  verify_time_synchronization
  verify_ufw_state
  verify_no_enabled_systemd_swap_units
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
  temporary_path="$(mktemp "${directory_path}/.100-prepare-host-backup.XXXXXX")"
  trap '[[ -z "${temporary_path}" ]] || rm -f -- "${temporary_path}"' EXIT

  if ! cp -p -- "${source_path}" "${temporary_path}"; then
    echo "백업 원본을 복사하지 못했습니다: ${source_path}" >&2
    return 1
  fi

  mv -- "${temporary_path}" "${backup_path}"
  temporary_path=""
)

persistent_fstab_swap_entries() {
  local fstab_path="$1"

  awk '
    $0 !~ /^[[:space:]]*#/ && NF >= 3 && $3 == "swap" {
      print
    }
  ' "${fstab_path}"
}

disable_persistent_fstab_swap() (
  local fstab_path="$1"
  local backup_path="${fstab_path}.before-kubernetes-bootstrap"
  local directory_path
  local swap_entries
  local temporary_path=""

  if ! swap_entries="$(persistent_fstab_swap_entries "${fstab_path}")"; then
    fail "${fstab_path}의 swap 설정을 조회하지 못했습니다."
  fi
  if [[ -z "${swap_entries}" ]]; then
    echo "재부팅 후 활성화될 ${fstab_path} swap 설정이 없습니다."
    return
  fi

  directory_path="${fstab_path%/*}"
  [[ -n "${directory_path}" ]] || directory_path="."
  temporary_path="$(mktemp "${directory_path}/.100-prepare-host.XXXXXX")"
  trap '[[ -z "${temporary_path}" ]] || rm -f -- "${temporary_path}"' EXIT

  backup_file_once "${fstab_path}" "${backup_path}"

  cp -p "${fstab_path}" "${temporary_path}"
  if ! awk '
    $0 !~ /^[[:space:]]*#/ && NF >= 3 && $3 == "swap" {
      print "# Kubernetes bootstrap disabled swap: " $0
      next
    }
    { print }
  ' "${fstab_path}" >"${temporary_path}"; then
    fail "${fstab_path} swap 설정을 변환하지 못했습니다."
  fi

  mv -- "${temporary_path}" "${fstab_path}"
  temporary_path=""

  if ! swap_entries="$(persistent_fstab_swap_entries "${fstab_path}")"; then
    fail "변경 후 ${fstab_path}의 swap 설정을 조회하지 못했습니다."
  fi
  if [[ -n "${swap_entries}" ]]; then
    fail "${fstab_path}에 활성 swap 설정이 남아 있습니다."
  fi

  echo "재부팅 후 swap이 다시 활성화되지 않도록 ${fstab_path}을 갱신했습니다."
)

disable_swap() {
  local active_swap

  if ! active_swap="$(swapon --noheadings --show=NAME)"; then
    fail "현재 활성 swap 상태를 조회하지 못했습니다."
  fi
  if [[ -n "${active_swap}" ]]; then
    echo "현재 활성화된 swap을 비활성화합니다:"
    printf '%s\n' "${active_swap}"
    swapoff --all
  else
    echo "현재 활성화된 swap이 없습니다."
  fi

  disable_persistent_fstab_swap "${FSTAB_PATH}"
  systemctl daemon-reload
}

has_desired_ipv4_forwarding_configuration() {
  local configuration_path="$1"

  [[ -f "${configuration_path}" ]] \
    && [[ "$(<"${configuration_path}")" == "${IPV4_FORWARDING_CONFIGURATION}" ]]
}

write_ipv4_forwarding_configuration() (
  local configuration_path="$1"
  local backup_path="${configuration_path}.before-kubernetes-bootstrap"
  local directory_path
  local temporary_path=""

  if has_desired_ipv4_forwarding_configuration "${configuration_path}"; then
    echo "IPv4 forwarding 영구 설정이 이미 원하는 상태입니다."
    return
  fi

  directory_path="${configuration_path%/*}"
  [[ -n "${directory_path}" ]] || directory_path="."
  temporary_path="$(mktemp "${directory_path}/.100-prepare-host.XXXXXX")"
  trap '[[ -z "${temporary_path}" ]] || rm -f -- "${temporary_path}"' EXIT

  if [[ -e "${configuration_path}" ]]; then
    backup_file_once "${configuration_path}" "${backup_path}"
  fi

  printf '%s\n' "${IPV4_FORWARDING_CONFIGURATION}" >"${temporary_path}"
  chmod 0644 "${temporary_path}"
  mv -- "${temporary_path}" "${configuration_path}"
  temporary_path=""

  echo "IPv4 forwarding 영구 설정을 ${configuration_path}에 기록했습니다."
)

enable_ipv4_forwarding() {
  write_ipv4_forwarding_configuration "${IPV4_FORWARDING_CONFIGURATION_PATH}"
  sysctl --load "${IPV4_FORWARDING_CONFIGURATION_PATH}"
}

verify_host_postconditions() {
  local active_swap
  local persistent_swap_entries

  echo "[검증] host 준비 결과를 확인합니다."

  if ! active_swap="$(swapon --noheadings --show=NAME)"; then
    fail "검증 중 활성 swap 상태를 조회하지 못했습니다."
  fi
  if [[ -n "${active_swap}" ]]; then
    fail "활성 swap이 남아 있습니다."
  fi

  if ! persistent_swap_entries="$(persistent_fstab_swap_entries "${FSTAB_PATH}")"; then
    fail "검증 중 ${FSTAB_PATH}의 swap 설정을 조회하지 못했습니다."
  fi
  if [[ -n "${persistent_swap_entries}" ]]; then
    fail "${FSTAB_PATH}에 재부팅 후 활성화될 swap 설정이 남아 있습니다."
  fi

  verify_no_enabled_systemd_swap_units

  if ! has_desired_ipv4_forwarding_configuration "${IPV4_FORWARDING_CONFIGURATION_PATH}"; then
    fail "IPv4 forwarding 영구 설정이 원하는 내용과 다릅니다."
  fi

  if [[ "$(sysctl -n net.ipv4.ip_forward)" != "1" ]]; then
    fail "현재 kernel의 net.ipv4.ip_forward 값이 1이 아닙니다."
  fi

  verify_time_synchronization
  verify_ufw_state
  echo "host 준비 결과 검증을 통과했습니다."
}

report_reboot_requirement() {
  echo "[보고] 다음 bootstrap 단계 진행 가능 여부를 확인합니다."
  if [[ -f /var/run/reboot-required ]]; then
    echo "재부팅이 필요합니다. 다음 bootstrap 단계 전에 EC2를 재부팅하고 다시 접속하세요."
    return
  fi

  echo "재부팅이 필요하지 않습니다. 다음 bootstrap 단계는 별도 절차에서 진행합니다."
}

main() {
  parse_arguments "$@"
  run_preflight_checks

  export DEBIAN_FRONTEND=noninteractive

  echo "[변경] package 색인을 갱신합니다."
  apt-get update

  echo "[변경] 설치된 package의 보안 및 bug fix version을 적용합니다."
  apt-get upgrade --yes

  echo "[변경] swap을 비활성화합니다."
  disable_swap

  echo "[변경] IPv4 packet forwarding을 활성화합니다."
  enable_ipv4_forwarding

  verify_host_postconditions
  report_reboot_requirement
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
