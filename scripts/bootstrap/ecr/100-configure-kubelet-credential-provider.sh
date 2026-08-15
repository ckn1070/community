#!/usr/bin/env bash

# Kubelet이 EC2 instance profile로 private ECR credential을 얻도록 구성합니다.
set -euo pipefail

readonly SUPPORTED_OS_ID="ubuntu"
readonly SUPPORTED_OS_VERSION="24.04"
readonly SUPPORTED_ARCHITECTURE="arm64"
readonly TARGET_KUBERNETES_VERSION="1.36.2"
readonly TARGET_KUBELET_PACKAGE_VERSION="1.36.2-2.1"
readonly ECR_CREDENTIAL_PROVIDER_VERSION="1.36.1"
readonly ECR_CREDENTIAL_PROVIDER_URL="https://storage.googleapis.com/k8s-staging-provider-aws/releases/v${ECR_CREDENTIAL_PROVIDER_VERSION}/linux/arm64/ecr-credential-provider-linux-arm64"
readonly ECR_CREDENTIAL_PROVIDER_SHA256="96b58e09522e47cf653065a104809ecba439630eaabea42db2dcb9ccedb1ed62"
readonly PROVIDER_DIRECTORY="/usr/local/libexec/kubernetes/kubelet-credential-providers"
readonly PROVIDER_PATH="${PROVIDER_DIRECTORY}/ecr-credential-provider"
readonly PROVIDER_CONFIGURATION_PATH="/etc/kubernetes/image-credential-provider-config.yaml"
readonly KUBELET_DEFAULTS_PATH="/etc/default/kubelet"
readonly KUBELET_DEFAULTS_PACKAGE_EMPTY_CONFIGURATION='KUBELET_EXTRA_ARGS='
readonly KUBELET_DEFAULTS_CONFIGURATION='KUBELET_EXTRA_ARGS="--image-credential-provider-bin-dir=/usr/local/libexec/kubernetes/kubelet-credential-providers --image-credential-provider-config=/etc/kubernetes/image-credential-provider-config.yaml"'
readonly LOCK_PATH="/run/lock/car-community-ecr-credential-provider.lock"

print_usage() {
  cat <<EOF
사용법: sudo /home/ubuntu/car-community-scripts/bootstrap/ecr/100-configure-kubelet-credential-provider.sh

Kubernetes v${TARGET_KUBERNETES_VERSION} kubelet에 official AWS ECR credential provider
v${ECR_CREDENTIAL_PROVIDER_VERSION}를 설치하고 EC2 instance profile credential을 사용하도록
구성합니다. IAM role을 생성하거나 instance에 연결하지는 않습니다.
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

require_commands() {
  local command_name
  local -a required_commands=(
    awk
    chmod
    cmp
    curl
    dirname
    dpkg
    dpkg-query
    flock
    grep
    install
    mktemp
    mv
    readlink
    rm
    sha256sum
    stat
    systemctl
    tr
  )

  for command_name in "${required_commands[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      fail "필수 command를 찾을 수 없습니다: ${command_name}"
    fi
  done
}

require_supported_operating_system() {
  if [[ ! -r /etc/os-release ]]; then
    fail "/etc/os-release를 읽을 수 없습니다."
  fi

  # /etc/os-release는 target host의 system file이라 local analysis에서 열 수 없습니다.
  # shellcheck disable=SC1091
  source /etc/os-release

  if [[ "${ID:-}" != "${SUPPORTED_OS_ID}" || "${VERSION_ID:-}" != "${SUPPORTED_OS_VERSION}" ]]; then
    fail "Ubuntu ${SUPPORTED_OS_VERSION}에서만 실행할 수 있습니다. 현재 값: ${PRETTY_NAME:-확인 불가}"
  fi
}

require_supported_architecture() {
  local architecture

  architecture="$(dpkg --print-architecture)"
  if [[ "${architecture}" != "${SUPPORTED_ARCHITECTURE}" ]]; then
    fail "${SUPPORTED_ARCHITECTURE} architecture에서만 실행할 수 있습니다. 현재 값: ${architecture}"
  fi
}

require_exact_kubelet_version() {
  local installed_version

  if ! installed_version="$(dpkg-query -W -f='${Version}' kubelet 2>/dev/null)"; then
    fail "kubelet package가 설치되어 있지 않습니다."
  fi
  if [[ "${installed_version}" != "${TARGET_KUBELET_PACKAGE_VERSION}" ]]; then
    fail "kubelet package ${TARGET_KUBELET_PACKAGE_VERSION}에서만 실행할 수 있습니다. 현재 값: ${installed_version}"
  fi
}

require_kubelet_defaults_loaded() {
  if ! systemctl cat kubelet.service | grep -Fq -- 'EnvironmentFile=-/etc/default/kubelet'; then
    fail "kubelet service가 ${KUBELET_DEFAULTS_PATH}를 읽는지 확인하지 못했습니다."
  fi
}

acquire_execution_lock() {
  exec 9>"${LOCK_PATH}"
  if ! flock --nonblock 9; then
    fail "다른 ECR credential provider 구성이 진행 중입니다: ${LOCK_PATH}"
  fi
}

render_credential_provider_configuration() {
  local destination_path="$1"

  cat >"${destination_path}" <<'EOF'
apiVersion: kubelet.config.k8s.io/v1
kind: CredentialProviderConfig
providers:
  - name: ecr-credential-provider
    matchImages:
      - "*.dkr.ecr.*.amazonaws.com"
      - "*.dkr.ecr.*.amazonaws.com.cn"
      - "*.dkr.ecr-fips.*.amazonaws.com"
      - "*.dkr.ecr.us-iso-east-1.c2s.ic.gov"
      - "*.dkr.ecr.us-isob-east-1.sc2s.sgov.gov"
    defaultCacheDuration: "12h"
    apiVersion: credentialprovider.kubelet.k8s.io/v1
EOF
}

require_safe_existing_kubelet_defaults() {
  local defaults_path="$1"
  local existing_content

  if [[ ! -e "${defaults_path}" && ! -L "${defaults_path}" ]]; then
    return
  fi
  if [[ ! -f "${defaults_path}" || -L "${defaults_path}" ]]; then
    fail "일반 file이 아닌 kubelet defaults는 변경하지 않습니다: ${defaults_path}"
  fi

  existing_content="$(<"${defaults_path}")"
  case "${existing_content}" in
    ""|"${KUBELET_DEFAULTS_PACKAGE_EMPTY_CONFIGURATION}"|"${KUBELET_DEFAULTS_CONFIGURATION}")
      ;;
    *)
      fail "다른 kubelet extra argument를 발견해 덮어쓰지 않습니다: ${defaults_path}"
      ;;
  esac
}

require_safe_existing_provider_binary() {
  local existing_checksum

  if [[ ! -e "${PROVIDER_PATH}" && ! -L "${PROVIDER_PATH}" ]]; then
    return
  fi
  if [[ ! -f "${PROVIDER_PATH}" || -L "${PROVIDER_PATH}" ]]; then
    fail "일반 file이 아닌 provider 경로는 변경하지 않습니다: ${PROVIDER_PATH}"
  fi

  existing_checksum="$(sha256sum "${PROVIDER_PATH}" | awk '{print $1}')"
  if [[ "${existing_checksum}" != "${ECR_CREDENTIAL_PROVIDER_SHA256}" ]]; then
    fail "다른 ECR credential provider binary를 발견해 덮어쓰지 않습니다: ${PROVIDER_PATH}"
  fi
}

require_safe_existing_provider_configuration() {
  local work_directory="$1"
  local expected_path="${work_directory}/expected-provider-configuration.yaml"

  if [[ ! -e "${PROVIDER_CONFIGURATION_PATH}" && ! -L "${PROVIDER_CONFIGURATION_PATH}" ]]; then
    return
  fi
  if [[ ! -f "${PROVIDER_CONFIGURATION_PATH}" || -L "${PROVIDER_CONFIGURATION_PATH}" ]]; then
    fail "일반 file이 아닌 provider 설정 경로는 변경하지 않습니다: ${PROVIDER_CONFIGURATION_PATH}"
  fi

  render_credential_provider_configuration "${expected_path}"
  if ! cmp --silent "${expected_path}" "${PROVIDER_CONFIGURATION_PATH}"; then
    fail "다른 image credential provider 설정을 발견해 덮어쓰지 않습니다: ${PROVIDER_CONFIGURATION_PATH}"
  fi
}

run_preflight_checks() {
  local work_directory="$1"

  echo "[사전 확인] 지원 환경과 기존 kubelet credential provider 설정을 확인합니다."
  require_root
  require_commands
  require_supported_operating_system
  require_supported_architecture
  require_exact_kubelet_version
  require_kubelet_defaults_loaded
  acquire_execution_lock
  require_safe_existing_kubelet_defaults "${KUBELET_DEFAULTS_PATH}"
  require_safe_existing_provider_binary
  require_safe_existing_provider_configuration "${work_directory}"
}

create_work_directory() {
  local work_directory

  work_directory="$(mktemp -d /tmp/car-community-ecr-provider.XXXXXX)"
  chmod 0700 "${work_directory}"
  printf '%s\n' "${work_directory}"
}

cleanup_work_directory() {
  local work_directory="$1"

  if [[ -n "${work_directory}" && -d "${work_directory}" ]]; then
    rm -rf -- "${work_directory}"
  fi
}

download_verified_provider() {
  local source_url="$1"
  local expected_checksum="$2"
  local destination_path="$3"
  local temporary_path="${destination_path}.download"
  local actual_checksum

  rm -f -- "${temporary_path}" "${destination_path}"
  if ! curl \
      --fail \
      --location \
      --silent \
      --show-error \
      --connect-timeout 15 \
      --max-time 300 \
      --retry 3 \
      --retry-delay 2 \
      --output "${temporary_path}" \
      "${source_url}"; then
    rm -f -- "${temporary_path}"
    fail "ECR credential provider를 내려받지 못했습니다."
  fi

  actual_checksum="$(sha256sum "${temporary_path}" | awk '{print $1}')"
  if [[ "${actual_checksum}" != "${expected_checksum}" ]]; then
    rm -f -- "${temporary_path}"
    fail "ECR credential provider SHA-256이 고정 값과 다릅니다."
  fi

  mv "${temporary_path}" "${destination_path}"
}

install_provider_binary() {
  local downloaded_path="$1"
  local temporary_path

  install -d -m 0755 "${PROVIDER_DIRECTORY}"
  temporary_path="$(mktemp "${PROVIDER_DIRECTORY}/.ecr-credential-provider.XXXXXX")"
  if ! install -m 0755 "${downloaded_path}" "${temporary_path}"; then
    rm -f -- "${temporary_path}"
    fail "Provider binary를 staging하지 못했습니다."
  fi
  mv "${temporary_path}" "${PROVIDER_PATH}"
}

install_provider_configuration() {
  local work_directory="$1"
  local rendered_path="${work_directory}/image-credential-provider-config.yaml"
  local configuration_directory
  local temporary_path

  render_credential_provider_configuration "${rendered_path}"
  configuration_directory="$(dirname "${PROVIDER_CONFIGURATION_PATH}")"
  install -d -m 0755 "${configuration_directory}"
  temporary_path="$(mktemp "${configuration_directory}/.image-credential-provider-config.XXXXXX")"
  if ! install -m 0644 "${rendered_path}" "${temporary_path}"; then
    rm -f -- "${temporary_path}"
    fail "Provider configuration을 staging하지 못했습니다."
  fi
  mv "${temporary_path}" "${PROVIDER_CONFIGURATION_PATH}"
}

install_kubelet_defaults() {
  local work_directory="$1"
  local rendered_path="${work_directory}/kubelet"
  local defaults_directory
  local temporary_path

  printf '%s\n' "${KUBELET_DEFAULTS_CONFIGURATION}" >"${rendered_path}"
  defaults_directory="$(dirname "${KUBELET_DEFAULTS_PATH}")"
  temporary_path="$(mktemp "${defaults_directory}/.car-community-kubelet.XXXXXX")"
  if ! install -m 0644 "${rendered_path}" "${temporary_path}"; then
    rm -f -- "${temporary_path}"
    fail "Kubelet defaults를 staging하지 못했습니다."
  fi
  mv "${temporary_path}" "${KUBELET_DEFAULTS_PATH}"
}

restart_kubelet() {
  systemctl daemon-reload
  systemctl restart kubelet.service
}

verify_postconditions() {
  local work_directory="$1"
  local actual_checksum
  local expected_configuration="${work_directory}/expected-installed-provider-configuration.yaml"
  local kubelet_pid
  local process_arguments
  local expected_bin_argument="--image-credential-provider-bin-dir=${PROVIDER_DIRECTORY}"
  local expected_config_argument="--image-credential-provider-config=${PROVIDER_CONFIGURATION_PATH}"

  actual_checksum="$(sha256sum "${PROVIDER_PATH}" | awk '{print $1}')"
  if [[ "${actual_checksum}" != "${ECR_CREDENTIAL_PROVIDER_SHA256}" ]]; then
    fail "설치된 provider binary checksum 검증에 실패했습니다."
  fi
  if [[ "$(stat -c '%a' "${PROVIDER_PATH}")" != "755" ]]; then
    fail "설치된 provider binary mode가 0755가 아닙니다."
  fi
  if [[ "$(stat -c '%a' "${PROVIDER_CONFIGURATION_PATH}")" != "644" ]]; then
    fail "설치된 provider configuration mode가 0644가 아닙니다."
  fi
  render_credential_provider_configuration "${expected_configuration}"
  if ! cmp --silent "${expected_configuration}" "${PROVIDER_CONFIGURATION_PATH}"; then
    fail "설치된 provider configuration이 exact 계약과 다릅니다."
  fi
  if [[ "$(stat -c '%a' "${KUBELET_DEFAULTS_PATH}")" != "644" ]]; then
    fail "설치된 kubelet defaults mode가 0644가 아닙니다."
  fi
  if [[ "$(<"${KUBELET_DEFAULTS_PATH}")" != "${KUBELET_DEFAULTS_CONFIGURATION}" ]]; then
    fail "kubelet extra argument가 exact 계약과 다릅니다."
  fi
  if ! systemctl is-active --quiet kubelet.service; then
    fail "kubelet service가 active 상태가 아닙니다."
  fi

  kubelet_pid="$(systemctl show --property MainPID --value kubelet.service)"
  if [[ ! "${kubelet_pid}" =~ ^[1-9][0-9]*$ || ! -r "/proc/${kubelet_pid}/cmdline" ]]; then
    fail "실행 중인 kubelet process를 확인하지 못했습니다."
  fi
  process_arguments="$(tr '\0' '\n' <"/proc/${kubelet_pid}/cmdline")"
  if ! grep -Fqx -- "${expected_bin_argument}" <<<"${process_arguments}"; then
    fail "실행 중인 kubelet에 provider binary directory 인자가 적용되지 않았습니다."
  fi
  if ! grep -Fqx -- "${expected_config_argument}" <<<"${process_arguments}"; then
    fail "실행 중인 kubelet에 provider configuration 인자가 적용되지 않았습니다."
  fi

  echo "[완료] Kubelet ECR credential provider 설치와 실행 인자 검증을 통과했습니다."
}

main() (
  local downloaded_provider
  local WORK_DIRECTORY

  WORK_DIRECTORY="$(create_work_directory)"
  trap 'cleanup_work_directory "${WORK_DIRECTORY}"' EXIT
  downloaded_provider="${WORK_DIRECTORY}/ecr-credential-provider"

  run_preflight_checks "${WORK_DIRECTORY}"
  download_verified_provider \
    "${ECR_CREDENTIAL_PROVIDER_URL}" \
    "${ECR_CREDENTIAL_PROVIDER_SHA256}" \
    "${downloaded_provider}"
  install_provider_binary "${downloaded_provider}"
  install_provider_configuration "${WORK_DIRECTORY}"
  install_kubelet_defaults "${WORK_DIRECTORY}"
  restart_kubelet
  verify_postconditions "${WORK_DIRECTORY}"
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  parse_arguments "$@"
  main
fi
