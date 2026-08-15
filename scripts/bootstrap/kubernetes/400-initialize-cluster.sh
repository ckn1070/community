#!/usr/bin/env bash

# kubeadm으로 Kubernetes v1.36.2 단일 control-plane cluster를 최초 초기화합니다.
set -euo pipefail

readonly SUPPORTED_OS_ID="ubuntu"
readonly SUPPORTED_OS_VERSION="24.04"
readonly SUPPORTED_ARCHITECTURE="arm64"
readonly TARGET_KUBERNETES_VERSION="1.36.2"
readonly KUBEADM_CONFIGURATION_API="kubeadm.k8s.io/v1beta4"
readonly CLUSTER_NAME="stg-car-community-k8s"
readonly EXPECTED_VPC_CIDR="10.0.0.0/16"
readonly POD_CIDR="192.168.0.0/16"
readonly SERVICE_CIDR="10.96.0.0/12"
readonly DNS_DOMAIN="cluster.local"
readonly API_SERVER_PORT="6443"
readonly CONTAINERD_CRI_SOCKET="unix:///run/containerd/containerd.sock"
readonly KUBECONFIG_PATH="/etc/kubernetes/admin.conf"
readonly LOCK_PATH="/run/lock/car-community-cluster-initialization.lock"
readonly CONTROL_PLANE_WAIT_TIMEOUT="120s"

PRIMARY_PRIVATE_IPV4=""
CLUSTER_STATE=""

print_usage() {
  cat <<EOF
사용법: sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/400-initialize-cluster.sh

현재 EC2 private IPv4를 API advertise address로 사용하는 Kubernetes
v${TARGET_KUBERNETES_VERSION} 단일 control-plane cluster를 kubeadm으로 초기화합니다.
CNI 설치와 control-plane taint 제거는 수행하지 않습니다.
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
    awk
    chmod
    cmp
    dpkg
    flock
    ip
    kubeadm
    kubelet
    kubectl
    mktemp
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

acquire_execution_lock() {
  exec 9>"${LOCK_PATH}"
  if ! flock --nonblock 9; then
    fail "다른 cluster 초기화 실행이 진행 중입니다: ${LOCK_PATH}"
  fi
}

require_exact_kubernetes_versions() {
  local binary_version

  binary_version="$(kubeadm version -o short 2>/dev/null || true)"
  if [[ "${binary_version}" != "v${TARGET_KUBERNETES_VERSION}" ]]; then
    fail "kubeadm binary version이 v${TARGET_KUBERNETES_VERSION}이 아닙니다: ${binary_version:-확인 실패}"
  fi

  binary_version="$(kubelet --version 2>/dev/null || true)"
  if [[ "${binary_version}" != "Kubernetes v${TARGET_KUBERNETES_VERSION}" ]]; then
    fail "kubelet binary version이 v${TARGET_KUBERNETES_VERSION}이 아닙니다: ${binary_version:-확인 실패}"
  fi
}

partial_cluster_state_marker_paths() {
  printf '%s\n' \
    /etc/kubernetes/super-admin.conf \
    /etc/kubernetes/kubelet.conf \
    /etc/kubernetes/controller-manager.conf \
    /etc/kubernetes/scheduler.conf \
    /etc/kubernetes/manifests/kube-apiserver.yaml \
    /etc/kubernetes/manifests/kube-controller-manager.yaml \
    /etc/kubernetes/manifests/kube-scheduler.yaml \
    /etc/kubernetes/manifests/etcd.yaml \
    /etc/kubernetes/pki/ca.crt \
    /etc/kubernetes/pki/ca.key \
    /var/lib/etcd/member \
    /var/lib/kubelet/config.yaml \
    /var/lib/kubelet/instance-config.yaml \
    /var/lib/kubelet/kubeadm-flags.env
}

classify_cluster_state() {
  local admin_kubeconfig_path="${1:-${KUBECONFIG_PATH}}"
  local path

  if [[ -s "${admin_kubeconfig_path}" ]]; then
    printf '%s\n' "complete"
    return
  fi

  if [[ -e "${admin_kubeconfig_path}" || -L "${admin_kubeconfig_path}" ]]; then
    printf '%s\n' "partial"
    return
  fi

  while IFS= read -r path; do
    [[ -n "${path}" ]] || continue
    if [[ -e "${path}" || -L "${path}" ]]; then
      printf '%s\n' "partial"
      return
    fi
  done < <(partial_cluster_state_marker_paths)

  printf '%s\n' "pristine"
}

detect_primary_private_ipv4() {
  local route_output
  local detected_address

  if ! route_output="$(ip -4 route get 1.1.1.1 2>/dev/null)"; then
    fail "primary private IPv4 확인을 위한 route 조회에 실패했습니다."
  fi
  detected_address="$(printf '%s\n' "${route_output}" | awk '
    {
      for (field_number = 1; field_number <= NF; field_number++) {
        if ($field_number == "src" && (field_number + 1) <= NF) {
          print $(field_number + 1)
          exit
        }
      }
    }
  ')"

  if [[ -z "${detected_address}" || "${detected_address}" != 10.0.* ]]; then
    fail "${EXPECTED_VPC_CIDR} 안의 primary private IPv4를 자동 확인하지 못했습니다: ${detected_address:-없음}"
  fi

  printf '%s\n' "${detected_address}"
}

host_routes_overlapping_cidr() {
  local cidr="$1"
  local contained_routes
  local covering_routes

  if ! contained_routes="$(ip -4 route show table main root "${cidr}")"; then
    fail "${cidr} 내부 host route를 조회하지 못했습니다."
  fi
  if ! covering_routes="$(ip -4 route show table main match "${cidr}")"; then
    fail "${cidr}를 포함하는 host route를 조회하지 못했습니다."
  fi

  printf '%s\n%s\n' "${contained_routes}" "${covering_routes}" \
    | awk 'NF && $1 != "default" && !seen[$0]++ { print }'
}

require_pristine_host_routes_do_not_overlap_cluster_networks() {
  local cidr
  local overlapping_routes

  for cidr in "${POD_CIDR}" "${SERVICE_CIDR}"; do
    if ! overlapping_routes="$(host_routes_overlapping_cidr "${cidr}")"; then
      fail "${cidr} host route 충돌 판정을 완료하지 못했습니다."
    fi
    if [[ -n "${overlapping_routes}" ]]; then
      echo "${cidr}와 겹치는 host route가 있습니다:" >&2
      printf '%s\n' "${overlapping_routes}" >&2
      fail "Pod 또는 Service CIDR과 겹치는 host route를 자동 변경하지 않습니다."
    fi
  done
}

run_preflight_checks() {
  echo "[사전 확인] 실행 환경, kubeadm 입력과 기존 cluster 상태를 확인합니다."
  require_root
  require_commands
  require_supported_operating_system
  require_supported_architecture
  acquire_execution_lock
  require_exact_kubernetes_versions

  PRIMARY_PRIVATE_IPV4="$(detect_primary_private_ipv4)"
  CLUSTER_STATE="$(classify_cluster_state)"

  if [[ "${CLUSTER_STATE}" == "pristine" ]]; then
    require_pristine_host_routes_do_not_overlap_cluster_networks
  fi
}

create_work_directory() {
  local work_directory

  work_directory="$(mktemp -d /tmp/car-community-400.XXXXXX)"
  chmod 0700 "${work_directory}"
  printf '%s\n' "${work_directory}"
}

cleanup_work_directory() {
  local work_directory="$1"

  if [[ -n "${work_directory}" && -d "${work_directory}" ]]; then
    rm -rf -- "${work_directory}"
  fi
}

render_kubeadm_configuration() {
  local init_configuration_path="$1"
  local cluster_configuration_path="$2"
  local advertise_address="$3"

  cat >"${cluster_configuration_path}" <<EOF
apiVersion: ${KUBEADM_CONFIGURATION_API}
kind: ClusterConfiguration
clusterName: ${CLUSTER_NAME}
kubernetesVersion: v${TARGET_KUBERNETES_VERSION}
networking:
  podSubnet: ${POD_CIDR}
  serviceSubnet: ${SERVICE_CIDR}
  dnsDomain: ${DNS_DOMAIN}
EOF

  cat >"${init_configuration_path}" <<EOF
apiVersion: ${KUBEADM_CONFIGURATION_API}
kind: InitConfiguration
localAPIEndpoint:
  advertiseAddress: ${advertise_address}
  bindPort: ${API_SERVER_PORT}
nodeRegistration:
  criSocket: ${CONTAINERD_CRI_SOCKET}
---
$(<"${cluster_configuration_path}")
EOF

  chmod 0600 "${init_configuration_path}" "${cluster_configuration_path}"
}

validate_kubeadm_configuration() {
  local init_configuration_path="$1"

  kubeadm config validate --config "${init_configuration_path}"
}

pull_control_plane_images() {
  local init_configuration_path="$1"

  kubeadm config images pull \
    --config "${init_configuration_path}" \
    --cri-socket "${CONTAINERD_CRI_SOCKET}"
}

initialize_cluster() {
  local init_configuration_path="$1"

  kubeadm init \
    --config "${init_configuration_path}" \
    --skip-token-print
}

kubectl_admin() {
  kubectl \
    --kubeconfig "${KUBECONFIG_PATH}" \
    "$@"
}

kubectl_admin_short() {
  kubectl \
    --kubeconfig "${KUBECONFIG_PATH}" \
    --request-timeout=10s \
    "$@"
}

canonicalize_cluster_configuration() {
  local source_path="$1"
  local destination_path="$2"
  local migrated_configuration_path="${destination_path}.migrated"

  kubeadm config migrate \
    --old-config "${source_path}" \
    --new-config "${migrated_configuration_path}"

  awk '
    function emit_cluster_configuration() {
      if (is_cluster_configuration) {
        printf "%s", document
      }
    }

    /^---[[:space:]]*$/ {
      emit_cluster_configuration()
      document = ""
      is_cluster_configuration = 0
      next
    }

    {
      document = document $0 ORS
      if ($0 ~ /^kind:[[:space:]]*ClusterConfiguration[[:space:]]*$/) {
        is_cluster_configuration = 1
      }
    }

    END {
      emit_cluster_configuration()
    }
  ' "${migrated_configuration_path}" >"${destination_path}"

  rm -- "${migrated_configuration_path}"

  if [[ ! -s "${destination_path}" ]]; then
    fail "kubeadm migrate 결과에서 ClusterConfiguration을 찾지 못했습니다."
  fi
}

verify_uploaded_cluster_configuration() {
  local expected_cluster_configuration_path="$1"
  local verification_directory="$2"
  local actual_configuration_path="${verification_directory}/actual-cluster-configuration.yaml"
  local expected_canonical_path="${verification_directory}/expected-cluster-configuration.canonical.yaml"
  local actual_canonical_path="${verification_directory}/actual-cluster-configuration.canonical.yaml"

  kubectl_admin_short \
    -n kube-system \
    get configmap kubeadm-config \
    -o jsonpath='{.data.ClusterConfiguration}' \
    >"${actual_configuration_path}"

  if [[ ! -s "${actual_configuration_path}" ]]; then
    fail "cluster의 kubeadm ClusterConfiguration을 읽지 못했습니다."
  fi

  kubeadm config validate --config "${actual_configuration_path}"
  canonicalize_cluster_configuration \
    "${expected_cluster_configuration_path}" \
    "${expected_canonical_path}"
  canonicalize_cluster_configuration \
    "${actual_configuration_path}" \
    "${actual_canonical_path}"

  if ! cmp --silent "${expected_canonical_path}" "${actual_canonical_path}"; then
    fail "기존 cluster의 kubeadm 설정이 project의 확정 계약과 다릅니다."
  fi
}

control_plane_node_name() {
  local node_names
  local node_count

  node_names="$(kubectl_admin_short \
    get nodes \
    -l node-role.kubernetes.io/control-plane \
    -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}')"
  node_count="$(printf '%s\n' "${node_names}" | awk 'NF { count++ } END { print count + 0 }')"

  if [[ "${node_count}" -ne 1 ]]; then
    fail "단일 control-plane node를 정확히 한 개 확인해야 합니다. 현재 개수: ${node_count}"
  fi

  printf '%s\n' "${node_names}" | awk 'NF { print; exit }'
}

verify_admin_kubeconfig() {
  local advertise_address="$1"
  local api_endpoint

  if [[ ! -s "${KUBECONFIG_PATH}" ]]; then
    fail "관리자 kubeconfig를 읽을 수 없습니다: ${KUBECONFIG_PATH}"
  fi
  if [[ "$(stat -c '%a' "${KUBECONFIG_PATH}")" != "600" ]]; then
    fail "${KUBECONFIG_PATH} 권한이 0600이 아닙니다."
  fi

  api_endpoint="$(kubectl config view \
    --kubeconfig "${KUBECONFIG_PATH}" \
    --minify \
    -o jsonpath='{.clusters[0].cluster.server}')"
  if [[ "${api_endpoint}" != "https://${advertise_address}:${API_SERVER_PORT}" ]]; then
    fail "admin kubeconfig의 API endpoint가 현재 private IPv4와 다릅니다: ${api_endpoint:-확인 실패}"
  fi
}

verify_cluster_postconditions() {
  local advertise_address="$1"
  local expected_cluster_configuration_path="$2"
  local verification_directory="${expected_cluster_configuration_path%/*}"
  local node_name
  local node_internal_ip
  local node_kubelet_version
  local node_ready_state
  local component

  echo "[검증] kubeadm cluster 설정, API와 control-plane component를 확인합니다."

  verify_admin_kubeconfig "${advertise_address}"

  if ! systemctl is-active --quiet kubelet; then
    fail "cluster 초기화 후 kubelet service가 active 상태가 아닙니다."
  fi

  if [[ "$(kubectl_admin_short get --raw='/readyz')" != "ok" ]]; then
    fail "Kubernetes API server readyz 확인을 통과하지 못했습니다."
  fi

  verify_uploaded_cluster_configuration \
    "${expected_cluster_configuration_path}" \
    "${verification_directory}"

  node_name="$(control_plane_node_name)"
  node_internal_ip="$(kubectl_admin_short \
    get node "${node_name}" \
    -o jsonpath='{.status.addresses[?(@.type=="InternalIP")].address}')"
  if [[ "${node_internal_ip}" != "${advertise_address}" ]]; then
    fail "Node InternalIP가 API advertise address와 다릅니다: ${node_internal_ip:-확인 실패}"
  fi

  node_kubelet_version="$(kubectl_admin_short \
    get node "${node_name}" \
    -o jsonpath='{.status.nodeInfo.kubeletVersion}')"
  if [[ "${node_kubelet_version}" != "v${TARGET_KUBERNETES_VERSION}" ]]; then
    fail "Node kubelet version이 v${TARGET_KUBERNETES_VERSION}이 아닙니다: ${node_kubelet_version:-확인 실패}"
  fi

  for component in etcd kube-apiserver kube-controller-manager kube-scheduler; do
    kubectl_admin \
      -n kube-system \
      wait --for=condition=Ready \
      "pod/${component}-${node_name}" \
      --timeout="${CONTROL_PLANE_WAIT_TIMEOUT}"
  done

  kubectl_admin \
    -n kube-system \
    rollout status daemonset/kube-proxy \
    --timeout="${CONTROL_PLANE_WAIT_TIMEOUT}"
  kubectl_admin_short -n kube-system get deployment/coredns >/dev/null
  kubectl_admin_short -n kube-system get service/kube-dns >/dev/null

  node_ready_state="$(kubectl_admin_short \
    get node "${node_name}" \
    -o jsonpath='{range .status.conditions[?(@.type=="Ready")]}{.status}{"\t"}{.reason}{"\t"}{.message}{"\n"}{end}')"
  echo "CNI 설치 전 Node Ready 상태는 완료 조건이 아닙니다: ${node_ready_state:-확인되지 않음}"
  echo "Kubernetes v${TARGET_KUBERNETES_VERSION} control plane 검증을 통과했습니다."
}

main() (
  local work_directory=""
  local init_configuration_path
  local cluster_configuration_path

  parse_arguments "$@"
  umask 077
  run_preflight_checks

  if [[ "${CLUSTER_STATE}" == "partial" ]]; then
    fail "kubeadm cluster state가 일부만 존재합니다. 자동 reset·재초기화하지 않고 진단을 위해 중단합니다."
  fi

  work_directory="$(create_work_directory)"
  trap 'cleanup_work_directory "${work_directory}"' EXIT
  init_configuration_path="${work_directory}/kubeadm-init.yaml"
  cluster_configuration_path="${work_directory}/cluster-configuration.yaml"

  echo "[계획] ${CLUSTER_NAME} cluster의 kubeadm exact 설정을 생성하고 현재 상태에 맞게 초기화 또는 검증합니다."
  render_kubeadm_configuration \
    "${init_configuration_path}" \
    "${cluster_configuration_path}" \
    "${PRIMARY_PRIVATE_IPV4}"
  validate_kubeadm_configuration "${init_configuration_path}"

  case "${CLUSTER_STATE}" in
    pristine)
      echo "[변경] control-plane image를 cluster state 생성 전에 받습니다."
      pull_control_plane_images "${init_configuration_path}"
      echo "[변경] bootstrap token을 출력하지 않고 kubeadm cluster를 최초 초기화합니다."
      initialize_cluster "${init_configuration_path}"
      ;;
    complete)
      echo "[변경 없음] 완료 후보 cluster를 다시 초기화하지 않고 확정 계약을 검증합니다."
      ;;
    *)
      fail "알 수 없는 cluster 상태입니다: ${CLUSTER_STATE:-없음}"
      ;;
  esac

  verify_cluster_postconditions \
    "${PRIMARY_PRIVATE_IPV4}" \
    "${cluster_configuration_path}"

  echo "[보고] 400-initialize-cluster.sh 단계가 완료됐습니다."
  echo "관리자 kubeconfig는 ${KUBECONFIG_PATH}에 있으며 사용자 홈으로 자동 복사하지 않았습니다."
  echo "CNI와 단일 node workload scheduling은 500-install-calico.sh가 구성합니다."
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
