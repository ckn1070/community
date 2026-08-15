#!/usr/bin/env bash

# Ubuntu 24.04 ARM64 host에 Kubernetes v1.36.2 node component를 설치합니다.
set -euo pipefail

readonly SUPPORTED_OS_ID="ubuntu"
readonly SUPPORTED_OS_VERSION="24.04"
readonly SUPPORTED_ARCHITECTURE="arm64"
readonly TARGET_KUBERNETES_VERSION="1.36.2"
readonly KUBERNETES_PACKAGE_VERSION="1.36.2-2.1"
readonly KUBERNETES_REPOSITORY_URL="https://pkgs.k8s.io/core:/stable:/v1.36/deb/"
readonly KUBERNETES_RELEASE_KEY_URL="${KUBERNETES_REPOSITORY_URL}Release.key"
readonly KUBERNETES_KEYRING_PATH="/etc/apt/keyrings/kubernetes-apt-keyring.gpg"
readonly KUBERNETES_REPOSITORY_PATH="/etc/apt/sources.list.d/kubernetes.list"
readonly KUBERNETES_REPOSITORY_CONFIGURATION="deb [signed-by=${KUBERNETES_KEYRING_PATH}] ${KUBERNETES_REPOSITORY_URL} /"
readonly LOCK_PATH="/run/lock/car-community-kubernetes-components-bootstrap.lock"
readonly -a KUBERNETES_PACKAGES=(kubelet kubeadm kubectl)

print_usage() {
  cat <<EOF
사용법: sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/300-install-kubernetes-components.sh

Ubuntu ${SUPPORTED_OS_VERSION} ARM64 host에 Kubernetes v${TARGET_KUBERNETES_VERSION}의
kubelet, kubeadm과 kubectl을 exact package version으로 설치합니다.
kubeadm init과 CNI 구성은 수행하지 않습니다.
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
    dpkg
    dpkg-query
    flock
    grep
    install
    mktemp
    mv
    rm
    stat
    systemctl
  )

  for command_name in "${required_commands[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      fail "필수 command를 찾을 수 없습니다: ${command_name}"
    fi
  done
}

require_repository_commands() {
  local command_name
  local -a required_commands=(curl gpg)

  for command_name in "${required_commands[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      fail "Kubernetes repository 구성에 필요한 command를 찾을 수 없습니다: ${command_name}"
    fi
  done
}

package_status_is_installed() {
  local package_status="$1"

  [[ "${package_status}" == ?i\  ]]
}

package_status_abbreviation() {
  local package_name="$1"

  dpkg-query -W -f='${db:Status-Abbrev}' "${package_name}" 2>/dev/null
}

recorded_package_version() {
  local package_name="$1"

  dpkg-query -W -f='${Version}' "${package_name}" 2>/dev/null
}

is_package_installed() {
  local package_name="$1"
  local package_status

  package_status="$(package_status_abbreviation "${package_name}" || true)"
  package_status_is_installed "${package_status}"
}

installed_package_version() {
  local package_name="$1"

  if ! is_package_installed "${package_name}"; then
    return 1
  fi

  recorded_package_version "${package_name}"
}

has_desired_kubernetes_repository_configuration() {
  local repository_path="$1"

  [[ -f "${repository_path}" ]] \
    && [[ "$(<"${repository_path}")" == "${KUBERNETES_REPOSITORY_CONFIGURATION}" ]]
}

require_safe_existing_kubernetes_package_state() {
  local package_name
  local package_status=""
  local installed_version=""
  local recorded_version=""

  for package_name in "${KUBERNETES_PACKAGES[@]}"; do
    if installed_version="$(installed_package_version "${package_name}" 2>/dev/null)" \
      && [[ "${installed_version}" != "${KUBERNETES_PACKAGE_VERSION}" ]]; then
      fail "다른 ${package_name} package version이 설치되어 있습니다: ${installed_version}"
    fi

    if [[ "${installed_version}" == "${KUBERNETES_PACKAGE_VERSION}" ]]; then
      continue
    fi

    package_status="$(package_status_abbreviation "${package_name}" || true)"
    if [[ -z "${package_status}" \
      || "${package_status:1:1}" == "n" \
      || "${package_status:1:1}" == "c" ]]; then
      continue
    fi

    recorded_version="$(recorded_package_version "${package_name}" || true)"
    if [[ "${recorded_version}" != "${KUBERNETES_PACKAGE_VERSION}" ]]; then
      fail "완료되지 않은 ${package_name} package가 다른 version을 사용합니다: ${recorded_version:-확인 실패}"
    fi
  done
}

require_safe_existing_repository_configuration() {
  if [[ -e "${KUBERNETES_REPOSITORY_PATH}" ]] \
    && ! has_desired_kubernetes_repository_configuration "${KUBERNETES_REPOSITORY_PATH}"; then
    fail "기존 ${KUBERNETES_REPOSITORY_PATH}의 관리 주체를 확인할 수 없어 덮어쓰지 않습니다."
  fi
}

acquire_execution_lock() {
  exec 9>"${LOCK_PATH}"
  if ! flock --nonblock 9; then
    fail "다른 Kubernetes component bootstrap 실행이 진행 중입니다: ${LOCK_PATH}"
  fi
}

run_preflight_checks() {
  echo "[사전 확인] 실행 환경과 기존 Kubernetes package·repository 상태를 확인합니다."
  require_root
  require_commands
  require_supported_operating_system
  require_supported_architecture
  acquire_execution_lock
  require_safe_existing_kubernetes_package_state
  require_safe_existing_repository_configuration
}

install_repository_dependencies() {
  apt-get install \
    --yes \
    --no-install-recommends \
    ca-certificates \
    curl \
    gpg
}

install_kubernetes_keyring() (
  local release_key_url="$1"
  local keyring_path="$2"
  local keyring_directory="${keyring_path%/*}"
  local rendered_keyring_path=""

  install -d -m 0755 "${keyring_directory}"

  rendered_keyring_path="$(mktemp "${keyring_directory}/.300-kubernetes-keyring.XXXXXX")"
  trap '[[ -z "${rendered_keyring_path}" ]] || rm -f -- "${rendered_keyring_path}"' EXIT

  if ! curl \
      --fail \
      --silent \
      --show-error \
      --location \
      --proto '=https' \
      --tlsv1.2 \
      "${release_key_url}" \
    | gpg \
      --batch \
      --yes \
      --dearmor \
      --output "${rendered_keyring_path}"; then
    fail "Kubernetes repository signing key를 내려받아 APT keyring으로 변환하지 못했습니다."
  fi

  if [[ ! -s "${rendered_keyring_path}" ]]; then
    fail "생성한 Kubernetes APT keyring이 비어 있습니다."
  fi

  chmod 0644 "${rendered_keyring_path}"
  if [[ -e "${keyring_path}" ]] \
    && cmp --silent "${rendered_keyring_path}" "${keyring_path}"; then
    chmod 0644 "${keyring_path}"
    echo "Kubernetes APT keyring이 이미 official key와 같습니다."
    return
  fi

  mv -- "${rendered_keyring_path}" "${keyring_path}"
  rendered_keyring_path=""
  echo "Kubernetes official repository keyring을 ${keyring_path}에 기록했습니다."
)

write_kubernetes_repository_configuration() (
  local repository_path="$1"
  local repository_directory="${repository_path%/*}"
  local temporary_path=""

  if has_desired_kubernetes_repository_configuration "${repository_path}"; then
    chmod 0644 "${repository_path}"
    echo "Kubernetes APT repository 설정이 이미 원하는 상태입니다."
    return
  fi

  if [[ -e "${repository_path}" ]]; then
    fail "기존 ${repository_path}의 관리 주체를 확인할 수 없어 덮어쓰지 않습니다."
  fi

  install -d -m 0755 "${repository_directory}"
  temporary_path="$(mktemp "${repository_directory}/.300-kubernetes-repository.XXXXXX")"
  trap '[[ -z "${temporary_path}" ]] || rm -f -- "${temporary_path}"' EXIT

  printf '%s\n' "${KUBERNETES_REPOSITORY_CONFIGURATION}" >"${temporary_path}"
  chmod 0644 "${temporary_path}"
  mv -- "${temporary_path}" "${repository_path}"
  temporary_path=""
  echo "Kubernetes v1.36 official APT repository를 ${repository_path}에 기록했습니다."
)

package_is_target_kubernetes_version() {
  local package_name="$1"
  local installed_version=""

  installed_version="$(installed_package_version "${package_name}" 2>/dev/null || true)"
  [[ "${installed_version}" == "${KUBERNETES_PACKAGE_VERSION}" ]]
}

install_kubernetes_packages() {
  apt-get install \
    --yes \
    "kubelet=${KUBERNETES_PACKAGE_VERSION}" \
    "kubeadm=${KUBERNETES_PACKAGE_VERSION}" \
    "kubectl=${KUBERNETES_PACKAGE_VERSION}"
}

hold_installed_target_kubernetes_packages() {
  local package_name

  for package_name in "${KUBERNETES_PACKAGES[@]}"; do
    if package_is_target_kubernetes_version "${package_name}"; then
      apt-mark hold "${package_name}"
    fi
  done
}

hold_all_kubernetes_packages() {
  apt-mark hold "${KUBERNETES_PACKAGES[@]}"
}

enable_kubelet_service() {
  systemctl enable kubelet
}

verify_kubernetes_component_postconditions() {
  local package_name
  local command_name
  local installed_version=""
  local kubeadm_version=""
  local kubelet_version=""
  local kubectl_version=""
  local kubectl_version_output=""
  local -a installed_commands=(kubelet kubeadm kubectl)

  echo "[검증] Kubernetes repository, package, hold와 kubelet service 상태를 확인합니다."

  if ! has_desired_kubernetes_repository_configuration "${KUBERNETES_REPOSITORY_PATH}" \
    || [[ "$(stat -c '%a' "${KUBERNETES_REPOSITORY_PATH}")" != "644" ]]; then
    fail "Kubernetes official APT repository 설정 또는 권한이 올바르지 않습니다."
  fi

  if [[ ! -s "${KUBERNETES_KEYRING_PATH}" ]] \
    || [[ "$(stat -c '%a' "${KUBERNETES_KEYRING_PATH}")" != "644" ]]; then
    fail "Kubernetes APT keyring이 없거나 읽기 권한이 올바르지 않습니다."
  fi

  for package_name in "${KUBERNETES_PACKAGES[@]}"; do
    installed_version="$(installed_package_version "${package_name}" 2>/dev/null || true)"
    if [[ "${installed_version}" != "${KUBERNETES_PACKAGE_VERSION}" ]]; then
      fail "설치된 ${package_name} package version이 다릅니다: ${installed_version:-설치되지 않음}"
    fi

    if ! apt-mark showhold | grep -Fqx "${package_name}"; then
      fail "${package_name} package가 hold 상태가 아닙니다."
    fi
  done

  for command_name in "${installed_commands[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      fail "Kubernetes package 설치 후 필수 command를 찾을 수 없습니다: ${command_name}"
    fi
  done

  if ! kubeadm_version="$(kubeadm version -o short 2>/dev/null)" \
    || [[ "${kubeadm_version}" != "v${TARGET_KUBERNETES_VERSION}" ]]; then
    fail "kubeadm binary version이 v${TARGET_KUBERNETES_VERSION}이 아닙니다: ${kubeadm_version:-확인 실패}"
  fi

  if ! kubelet_version="$(kubelet --version 2>/dev/null)" \
    || [[ "${kubelet_version}" != "Kubernetes v${TARGET_KUBERNETES_VERSION}" ]]; then
    fail "kubelet binary version이 v${TARGET_KUBERNETES_VERSION}이 아닙니다: ${kubelet_version:-확인 실패}"
  fi

  if ! kubectl_version_output="$(kubectl version --client=true --output=yaml 2>/dev/null)"; then
    fail "kubectl client version을 확인하지 못했습니다."
  fi
  kubectl_version="$(printf '%s\n' "${kubectl_version_output}" | awk '$1 == "gitVersion:" { print $2; exit }')"
  if [[ "${kubectl_version}" != "v${TARGET_KUBERNETES_VERSION}" ]]; then
    fail "kubectl binary version이 v${TARGET_KUBERNETES_VERSION}이 아닙니다: ${kubectl_version:-확인 실패}"
  fi

  if ! systemctl is-enabled --quiet kubelet; then
    fail "kubelet service가 enabled 상태가 아닙니다."
  fi

  echo "Kubernetes v${TARGET_KUBERNETES_VERSION} component 설치 검증을 통과했습니다."
}

main() {
  parse_arguments "$@"
  run_preflight_checks

  export DEBIAN_FRONTEND=noninteractive

  echo "[계획] Kubernetes v${TARGET_KUBERNETES_VERSION} component를 exact package version으로 설치합니다."
  echo "[변경] official Kubernetes APT repository 구성 dependency를 설치합니다."
  install_repository_dependencies
  require_repository_commands

  echo "[변경] Kubernetes official repository signing key를 구성합니다."
  install_kubernetes_keyring "${KUBERNETES_RELEASE_KEY_URL}" "${KUBERNETES_KEYRING_PATH}"

  echo "[변경] Kubernetes v1.36 official APT repository를 구성합니다."
  write_kubernetes_repository_configuration "${KUBERNETES_REPOSITORY_PATH}"

  echo "[변경] Kubernetes repository를 포함해 package 색인을 갱신합니다."
  apt-get update

  echo "[변경] kubelet, kubeadm과 kubectl exact package를 설치합니다."
  if ! install_kubernetes_packages; then
    hold_installed_target_kubernetes_packages
    fail "Kubernetes component package 설치를 완료하지 못했습니다. 설치된 exact package만 hold했습니다."
  fi

  echo "[변경] Kubernetes component package를 의도하지 않은 자동 upgrade에서 제외합니다."
  hold_all_kubernetes_packages

  echo "[변경] 재부팅 뒤 kubelet이 시작되도록 service를 enable합니다."
  enable_kubelet_service

  verify_kubernetes_component_postconditions
  echo "[보고] 300-install-kubernetes-components.sh 단계가 완료됐습니다."
  echo "kubelet은 kubeadm init 전이라 active가 아니거나 재시작 중일 수 있으며, 이 단계에서는 정상입니다."
  echo "kubeadm init과 CNI 구성은 별도 후속 단계입니다."
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
