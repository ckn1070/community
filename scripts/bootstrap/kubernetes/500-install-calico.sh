#!/usr/bin/env bash

# Calico를 설치하고 단일 node Kubernetes cluster의 Pod network를 검증합니다.
set -euo pipefail

readonly SUPPORTED_OS_ID="ubuntu"
readonly SUPPORTED_OS_VERSION="24.04"
readonly SUPPORTED_ARCHITECTURE="arm64"
readonly TARGET_KUBERNETES_VERSION="1.36.2"
readonly POD_CIDR="192.168.0.0/16"
readonly KUBECONFIG_PATH="/etc/kubernetes/admin.conf"
readonly CALICO_VERSION="3.32.1"
readonly CALICO_OPERATOR_IMAGE="quay.io/tigera/operator:v1.42.3"
readonly CALICO_CRD_URL="https://raw.githubusercontent.com/projectcalico/calico/v${CALICO_VERSION}/manifests/v1_crd_projectcalico_org.yaml"
readonly CALICO_CRD_SHA256="192f8b2d934ef24b62e86b7e1a6e1762d1c8af26a916f78057bc13fa5ed60f71"
readonly CALICO_OPERATOR_URL="https://raw.githubusercontent.com/projectcalico/calico/v${CALICO_VERSION}/manifests/tigera-operator.yaml"
readonly CALICO_OPERATOR_SHA256="f18e073794207d372606bc3ea6f8fd73972f86d6828f4ba666dfe0d4aa8ab07f"
readonly CALICO_FIELD_MANAGER="car-community-bootstrap"
readonly CALICO_WAIT_TIMEOUT="600s"
readonly SMOKE_WAIT_TIMEOUT="180s"
readonly SMOKE_DELETE_TIMEOUT="120s"
readonly SMOKE_NAMESPACE="car-community-network-smoke"
readonly SMOKE_IMAGE="registry.k8s.io/e2e-test-images/agnhost:2.63.0@sha256:a62f40481e05709988fe44e5035b2e0f553cd52bf0ae4e83614928255b00e8c1"
readonly LOCK_PATH="/run/lock/car-community-calico-bootstrap.lock"

print_usage() {
  cat <<EOF
사용법: sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/500-install-calico.sh

Kubernetes v${TARGET_KUBERNETES_VERSION} 단일 node cluster에 Calico v${CALICO_VERSION}를
operator 방식으로 설치하고 Node·CoreDNS·일반 workload network를 검증합니다.
AWS Load Balancer Controller와 application resource는 구성하지 않습니다.
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
    basename
    chmod
    curl
    dpkg
    find
    flock
    kubectl
    mktemp
    rm
    sha256sum
    sysctl
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
    fail "다른 Calico bootstrap 실행이 진행 중입니다: ${LOCK_PATH}"
  fi
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

require_cluster_access() {
  if [[ ! -s "${KUBECONFIG_PATH}" ]]; then
    fail "${KUBECONFIG_PATH}가 없습니다. 400-initialize-cluster.sh 완료 상태가 아닙니다."
  fi

  if [[ "$(sysctl -n net.ipv4.ip_forward 2>/dev/null || true)" != "1" ]]; then
    fail "현재 kernel의 IPv4 forwarding 값이 1이 아닙니다."
  fi

  if [[ "$(kubectl_admin_short get --raw='/readyz')" != "ok" ]]; then
    fail "Kubernetes API server readyz 확인을 통과하지 못했습니다."
  fi
}

require_exact_pod_cidr_source() {
  local controller_manager_commands
  local pod_cidr_arguments

  if ! controller_manager_commands="$(kubectl_admin_short \
      --allow-missing-template-keys=false \
      -n kube-system \
      get pods \
      -l component=kube-controller-manager \
      -o 'jsonpath={range .items[*].spec.containers[?(@.name=="kube-controller-manager")].command[*]}{@}{"\n"}{end}')"; then
    fail "API에 등록된 kube-controller-manager Pod spec의 cluster CIDR command를 조회하지 못했습니다."
  fi

  if [[ -z "${controller_manager_commands}" ]]; then
    fail "API에 등록된 kube-controller-manager Pod spec에서 command를 찾지 못했습니다."
  fi

  pod_cidr_arguments="$(printf '%s\n' "${controller_manager_commands}" \
    | awk '/^--cluster-cidr=/ { print }')"
  if [[ "${pod_cidr_arguments}" != "--cluster-cidr=${POD_CIDR}" ]]; then
    fail "API에 등록된 kube-controller-manager Pod spec의 Pod CIDR이 ${POD_CIDR} exact 계약과 다릅니다."
  fi
}

require_network_manager_not_active() {
  local active_state
  local load_state
  local unit_state

  if ! unit_state="$(systemctl show \
      NetworkManager.service \
      --property=LoadState \
      --property=ActiveState \
      --no-pager)"; then
    fail "NetworkManager unit 상태를 조회하지 못했습니다."
  fi

  load_state="$(printf '%s\n' "${unit_state}" | awk -F= '$1 == "LoadState" { print $2 }')"
  active_state="$(printf '%s\n' "${unit_state}" | awk -F= '$1 == "ActiveState" { print $2 }')"
  if [[ -z "${load_state}" || -z "${active_state}" ]]; then
    fail "NetworkManager unit 상태 응답에서 LoadState와 ActiveState를 확인하지 못했습니다."
  fi

  case "${load_state}:${active_state}" in
    not-found:inactive|loaded:inactive)
      ;;
    *)
      fail "NetworkManager 상태를 안전한 비활성·부재로 판정하지 못했습니다: LoadState=${load_state}, ActiveState=${active_state}"
      ;;
  esac
}

ufw_is_available() {
  command -v ufw >/dev/null 2>&1
}

require_ufw_inactive() {
  local status_output
  local ufw_status

  if ! ufw_is_available; then
    return
  fi

  if ! status_output="$(ufw status 2>/dev/null)"; then
    fail "UFW 상태를 확인하지 못했습니다."
  fi
  ufw_status="$(printf '%s\n' "${status_output}" | awk 'NR == 1 { print; exit }')"
  case "${ufw_status}" in
    "Status: inactive")
      ;;
    "Status: active")
      fail "UFW가 active 상태입니다. Calico에 필요한 traffic 정책을 결정하기 전에는 자동 변경하지 않습니다."
      ;;
    *)
      fail "UFW 상태를 판정하지 못했습니다. 'sudo ufw status' 결과를 직접 확인하세요."
      ;;
  esac
}

cni_configuration_paths() {
  local configuration_directory="${1:-/etc/cni/net.d}"

  if [[ ! -e "${configuration_directory}" && ! -L "${configuration_directory}" ]]; then
    return
  fi
  if [[ ! -d "${configuration_directory}" ]]; then
    return 1
  fi

  find "${configuration_directory}" \
    -mindepth 1 \
    -maxdepth 1 \
    ! -type d \
    \( -name '*.conf' -o -name '*.conflist' -o -name '*.json' \) \
    -print
}

require_no_foreign_cni_configuration() {
  local configuration_directory="${1:-/etc/cni/net.d}"
  local configuration_path
  local configuration_name
  local configuration_paths

  if ! configuration_paths="$(cni_configuration_paths "${configuration_directory}")"; then
    fail "기존 CNI network 설정 file을 조회하지 못했습니다."
  fi

  while IFS= read -r configuration_path; do
    [[ -n "${configuration_path}" ]] || continue
    configuration_name="$(basename "${configuration_path}")"
    case "${configuration_name}" in
      10-calico.conflist)
        ;;
      *)
        fail "다른 CNI network 설정을 발견해 변경하지 않습니다: ${configuration_path}"
        ;;
    esac
  done <<<"${configuration_paths}"
}

require_no_raw_manifest_calico() {
  local resource_name

  if ! resource_name="$(kubectl_admin_short \
      -n kube-system \
      get daemonset/calico-node \
      --ignore-not-found \
      -o name)"; then
    fail "기존 raw-manifest Calico DaemonSet 상태를 조회하지 못했습니다."
  fi
  if [[ -n "${resource_name}" ]]; then
    fail "kube-system의 raw-manifest Calico node를 발견해 operator 방식으로 자동 전환하지 않습니다."
  fi

  if ! resource_name="$(kubectl_admin_short \
      -n kube-system \
      get deployment/calico-kube-controllers \
      --ignore-not-found \
      -o name)"; then
    fail "기존 raw-manifest Calico controller 상태를 조회하지 못했습니다."
  fi
  if [[ -n "${resource_name}" ]]; then
    fail "kube-system의 raw-manifest Calico controller를 발견해 operator 방식으로 자동 전환하지 않습니다."
  fi
}

require_exact_existing_calico_operator() {
  local existing_name
  local existing_image
  local existing_state

  if ! existing_state="$(kubectl_admin_short \
      -n tigera-operator \
      get deployment/tigera-operator \
      --ignore-not-found \
      -o 'jsonpath={.metadata.name}{"\t"}{.spec.template.spec.containers[?(@.name=="tigera-operator")].image}')"; then
    fail "기존 Tigera operator image를 조회하지 못했습니다."
  fi
  if [[ -z "${existing_state}" ]]; then
    return
  fi

  IFS=$'\t' read -r existing_name existing_image <<<"${existing_state}"
  if [[ "${existing_name}" != "tigera-operator" || "${existing_image}" != "${CALICO_OPERATOR_IMAGE}" ]]; then
    fail "다른 version 또는 구조의 Tigera operator를 발견해 자동 upgrade·downgrade하지 않습니다: ${existing_image:-image 확인 실패}"
  fi
}

run_preflight_checks() {
  echo "[사전 확인] 실행 환경과 Calico가 직접 소비하는 host·cluster 상태를 확인합니다."
  require_root
  require_commands
  require_supported_operating_system
  require_supported_architecture
  acquire_execution_lock
  require_cluster_access
  control_plane_node_name >/dev/null
  require_exact_pod_cidr_source
  require_network_manager_not_active
  require_ufw_inactive
  require_no_foreign_cni_configuration
  require_no_raw_manifest_calico
  require_exact_existing_calico_operator
}

create_work_directory() {
  local work_directory

  work_directory="$(mktemp -d /tmp/car-community-500.XXXXXX)"
  chmod 0700 "${work_directory}"
  printf '%s\n' "${work_directory}"
}

cleanup_work_directory() {
  local work_directory="$1"

  if [[ -n "${work_directory}" && -d "${work_directory}" ]]; then
    rm -rf -- "${work_directory}"
  fi
}

download_verified_artifact() {
  local artifact_url="$1"
  local expected_checksum="$2"
  local destination_path="$3"

  if ! curl \
      --fail \
      --silent \
      --show-error \
      --location \
      --proto '=https' \
      --tlsv1.2 \
      --output "${destination_path}" \
      "${artifact_url}"; then
    rm -f -- "${destination_path}"
    fail "Calico artifact를 내려받지 못했습니다: ${artifact_url}"
  fi

  if ! printf '%s  %s\n' "${expected_checksum}" "${destination_path}" \
    | sha256sum --check --status; then
    rm -f -- "${destination_path}"
    fail "Calico artifact SHA-256이 repository 고정값과 다릅니다: ${artifact_url}"
  fi

  chmod 0600 "${destination_path}"
}

download_calico_artifacts() {
  local work_directory="$1"

  download_verified_artifact \
    "${CALICO_CRD_URL}" \
    "${CALICO_CRD_SHA256}" \
    "${work_directory}/calico-crds.yaml"
  download_verified_artifact \
    "${CALICO_OPERATOR_URL}" \
    "${CALICO_OPERATOR_SHA256}" \
    "${work_directory}/tigera-operator.yaml"
}

render_calico_installation() {
  local installation_path="$1"

  cat >"${installation_path}" <<EOF
apiVersion: operator.tigera.io/v1
kind: Installation
metadata:
  name: default
spec:
  calicoNetwork:
    linuxDataplane: Iptables
    kubeProxyManagement: Disabled
    nodeAddressAutodetectionV4:
      kubernetes: NodeInternalIP
    ipPools:
      - name: default-ipv4-ippool
        blockSize: 26
        cidr: ${POD_CIDR}
        encapsulation: VXLANCrossSubnet
        natOutgoing: Enabled
        nodeSelector: all()
EOF
  chmod 0600 "${installation_path}"
}

print_calico_diagnostics() {
  echo "Calico 진단: Tigera status와 관련 Pod·event를 출력합니다." >&2
  kubectl_admin_short get tigerastatus 2>&1 || echo "Tigera status를 아직 조회할 수 없습니다." >&2
  kubectl_admin_short -n tigera-operator get pods -o wide 2>&1 \
    || echo "tigera-operator Pod를 아직 조회할 수 없습니다." >&2
  kubectl_admin_short -n calico-system get pods -o wide 2>&1 \
    || echo "calico-system Pod를 아직 조회할 수 없습니다." >&2
  kubectl_admin_short get events --all-namespaces --sort-by=.lastTimestamp 2>&1 \
    || echo "cluster event를 조회하지 못했습니다." >&2
}

apply_calico_base_resources() {
  local work_directory="$1"

  # 다른 manager와 충돌하는 field는 강제로 인수하지 않습니다.
  if ! kubectl_admin apply \
      --server-side \
      --field-manager="${CALICO_FIELD_MANAGER}" \
      -f "${work_directory}/calico-crds.yaml"; then
    fail "Calico CRD server-side apply에 실패했습니다. Field conflict는 강제 인수하지 않습니다."
  fi
  if ! kubectl_admin wait \
      --for=condition=Established \
      customresourcedefinition/installations.operator.tigera.io \
      --timeout="${CALICO_WAIT_TIMEOUT}"; then
    fail "Calico Installation CRD가 제한 시간 안에 Established 상태가 되지 않았습니다."
  fi

  if ! kubectl_admin apply \
      --server-side \
      --field-manager="${CALICO_FIELD_MANAGER}" \
      -f "${work_directory}/tigera-operator.yaml"; then
    fail "Tigera operator server-side apply에 실패했습니다. Field conflict는 강제 인수하지 않습니다."
  fi
  if ! kubectl_admin \
      -n tigera-operator \
      rollout status deployment/tigera-operator \
      --timeout="${CALICO_WAIT_TIMEOUT}"; then
    fail "Tigera operator가 제한 시간 안에 rollout을 완료하지 못했습니다."
  fi
}

apply_calico_installation() {
  local installation_path="$1"

  if ! kubectl_admin apply \
      --server-side \
      --field-manager="${CALICO_FIELD_MANAGER}" \
      -f "${installation_path}"; then
    fail "Calico Installation server-side apply에 실패했습니다. Field conflict는 강제 인수하지 않습니다."
  fi
}

wait_for_calico_network() {
  if ! kubectl_admin wait \
      --for=create \
      tigerastatus/calico \
      --timeout="${CALICO_WAIT_TIMEOUT}" \
    || ! kubectl_admin wait \
      --for=condition=Available \
      tigerastatus/calico \
      --timeout="${CALICO_WAIT_TIMEOUT}"; then
    print_calico_diagnostics
    fail "Calico v${CALICO_VERSION}가 제한 시간 안에 Available 상태가 되지 않았습니다."
  fi
}

control_plane_node_name() {
  local all_node_states
  local control_plane_node_names
  local node_count
  local node_name
  local node_version

  if ! all_node_states="$(kubectl_admin_short \
      get nodes \
      -o 'jsonpath={range .items[*]}{.metadata.name}{"\t"}{.status.nodeInfo.kubeletVersion}{"\n"}{end}')"; then
    fail "전체 Kubernetes Node 상태를 조회하지 못했습니다."
  fi
  node_count="$(printf '%s\n' "${all_node_states}" | awk 'NF { count++ } END { print count + 0 }')"

  if [[ "${node_count}" -ne 1 ]]; then
    fail "지원 범위인 단일 Kubernetes Node를 정확히 한 개 확인해야 합니다. 현재 개수: ${node_count}"
  fi
  IFS=$'\t' read -r node_name node_version <<<"${all_node_states}"
  if [[ "${node_version}" != "v${TARGET_KUBERNETES_VERSION}" ]]; then
    fail "단일 Node의 kubelet version이 v${TARGET_KUBERNETES_VERSION} exact 계약과 다릅니다: ${node_version:-확인 실패}"
  fi

  if ! control_plane_node_names="$(kubectl_admin_short \
      get nodes \
      -l node-role.kubernetes.io/control-plane \
      -o 'jsonpath={range .items[*]}{.metadata.name}{"\n"}{end}')"; then
    fail "control-plane Node label을 조회하지 못했습니다."
  fi
  if [[ "${control_plane_node_names}" != "${node_name}" ]]; then
    fail "유일한 Node가 control-plane role을 가져야 합니다: ${node_name}"
  fi

  printf '%s\n' "${node_name}"
}

control_plane_taint_exists() {
  local node_name="$1"
  local taint_key

  if ! taint_key="$(kubectl_admin_short \
      get node "${node_name}" \
      -o jsonpath='{.spec.taints[?(@.key=="node-role.kubernetes.io/control-plane")].key}')"; then
    fail "control-plane taint 상태를 조회하지 못했습니다: ${node_name}"
  fi

  [[ "${taint_key}" == "node-role.kubernetes.io/control-plane" ]]
}

remove_control_plane_taint() {
  local node_name

  node_name="$(control_plane_node_name)"
  if ! control_plane_taint_exists "${node_name}"; then
    echo "control-plane taint가 이미 없어 일반 Pod를 배치할 수 있습니다."
    return
  fi

  kubectl_admin taint \
    node "${node_name}" \
    node-role.kubernetes.io/control-plane-
}

require_exact_calico_contract() {
  local actual_installation
  local actual_operator_image
  local expected_installation

  if ! actual_operator_image="$(kubectl_admin_short \
      -n tigera-operator \
      get deployment/tigera-operator \
      -o jsonpath='{.spec.template.spec.containers[?(@.name=="tigera-operator")].image}')"; then
    fail "Calico operator image를 조회하지 못했습니다."
  fi
  if [[ "${actual_operator_image}" != "${CALICO_OPERATOR_IMAGE}" ]]; then
    fail "Calico operator image가 v${CALICO_VERSION} 계약과 다릅니다: ${actual_operator_image:-확인 실패}"
  fi

  if ! actual_installation="$(kubectl_admin_short \
      get installation.operator.tigera.io/default \
      -o 'jsonpath={.spec.calicoNetwork.linuxDataplane}{"\t"}{.spec.calicoNetwork.kubeProxyManagement}{"\t"}{.spec.calicoNetwork.nodeAddressAutodetectionV4.kubernetes}{"\t"}{range .spec.calicoNetwork.ipPools[*]}{.name}{"\t"}{.blockSize}{"\t"}{.cidr}{"\t"}{.encapsulation}{"\t"}{.natOutgoing}{"\t"}{.nodeSelector}{"\n"}{end}')"; then
    fail "Calico Installation 계약을 조회하지 못했습니다."
  fi
  expected_installation=$'Iptables\tDisabled\tNodeInternalIP\tdefault-ipv4-ippool\t26\t192.168.0.0/16\tVXLANCrossSubnet\tEnabled\tall()'
  if [[ "${actual_installation}" != "${expected_installation}" ]]; then
    fail "Calico Installation이 project의 exact network 계약과 다릅니다."
  fi
}

require_actual_calico_ippool_matches() {
  local actual_pool
  local expected_pool

  if ! actual_pool="$(kubectl_admin_short \
      get ippools.crd.projectcalico.org \
      -o 'jsonpath={range .items[*]}{.metadata.name}{"\t"}{.spec.cidr}{"\t"}{.spec.blockSize}{"\t"}{.spec.vxlanMode}{"\t"}{.spec.ipipMode}{"\t"}{.spec.natOutgoing}{"\t"}{.spec.nodeSelector}{"\t"}{.spec.disabled}{"\n"}{end}')"; then
    fail "실제 Calico IPPool 계약을 조회하지 못했습니다."
  fi
  expected_pool=$'default-ipv4-ippool\t192.168.0.0/16\t26\tCrossSubnet\tNever\ttrue\tall()'
  case "${actual_pool}" in
    "${expected_pool}"$'\t'|"${expected_pool}"$'\tfalse')
      ;;
    *)
      fail "실제 Calico IPPool이 이름·CIDR·block size·VXLAN·IPIP·NAT·node selector·활성 상태 계약과 다릅니다."
      ;;
  esac
}

verify_cluster_network_postconditions() {
  local node_name

  echo "[검증] Calico network, 단일 Node와 CoreDNS readiness를 확인합니다."
  require_exact_calico_contract

  node_name="$(control_plane_node_name)"
  kubectl_admin wait \
    --for=condition=Ready \
    "node/${node_name}" \
    --timeout="${CALICO_WAIT_TIMEOUT}"
  kubectl_admin \
    -n kube-system \
    rollout status daemonset/kube-proxy \
    --timeout="${CALICO_WAIT_TIMEOUT}"
  kubectl_admin \
    -n kube-system \
    rollout status deployment/coredns \
    --timeout="${CALICO_WAIT_TIMEOUT}"
  require_actual_calico_ippool_matches
}

smoke_namespace_state() {
  local namespace_name="$1"

  kubectl_admin_short \
    get namespace "${namespace_name}" \
    --ignore-not-found \
    -o 'jsonpath={.metadata.name}{"\t"}{.metadata.labels.app\.kubernetes\.io/managed-by}'
}

apply_smoke_namespace() {
  local namespace_name="$1"

  kubectl_admin apply \
    --server-side \
    --field-manager="${CALICO_FIELD_MANAGER}" \
    -f - <<EOF
apiVersion: v1
kind: Namespace
metadata:
  name: ${namespace_name}
  labels:
    app.kubernetes.io/managed-by: ${CALICO_FIELD_MANAGER}
EOF
}

delete_smoke_namespace() {
  local namespace_name="$1"

  kubectl_admin delete namespace "${namespace_name}" \
    --ignore-not-found \
    --wait=true \
    --timeout="${SMOKE_DELETE_TIMEOUT}"
}

prepare_smoke_namespace() {
  local namespace_name="$1"
  local existing_name
  local existing_marker
  local namespace_state

  if ! namespace_state="$(smoke_namespace_state "${namespace_name}")"; then
    fail "기존 smoke namespace 상태를 조회하지 못했습니다: ${namespace_name}"
  fi
  if [[ -n "${namespace_state}" ]]; then
    IFS=$'\t' read -r existing_name existing_marker <<<"${namespace_state}"
    if [[ "${existing_name}" != "${namespace_name}" || "${existing_marker}" != "${CALICO_FIELD_MANAGER}" ]]; then
      fail "bootstrap marker가 일치하지 않는 smoke namespace를 발견해 변경하지 않습니다: ${namespace_name}"
    fi
    if ! delete_smoke_namespace "${namespace_name}"; then
      fail "이전 실행이 남긴 smoke namespace를 정리하지 못했습니다: ${namespace_name}"
    fi
  fi

  if ! apply_smoke_namespace "${namespace_name}"; then
    fail "smoke namespace를 생성하지 못했습니다: ${namespace_name}"
  fi
}

configure_smoke_resources() {
  local namespace_name="$1"

  kubectl_admin \
    -n "${namespace_name}" \
    apply \
    --server-side \
    --field-manager="${CALICO_FIELD_MANAGER}" \
    -f - <<EOF
apiVersion: v1
kind: Pod
metadata:
  name: network-smoke-server
  labels:
    app.kubernetes.io/name: network-smoke-server
    app.kubernetes.io/managed-by: ${CALICO_FIELD_MANAGER}
spec:
  automountServiceAccountToken: false
  containers:
    - name: server
      image: ${SMOKE_IMAGE}
      imagePullPolicy: IfNotPresent
      command: ["/agnhost"]
      args: ["netexec", "--http-port=8080", "--udp-port=-1"]
      ports:
        - name: http
          containerPort: 8080
---
apiVersion: v1
kind: Service
metadata:
  name: network-smoke
  labels:
    app.kubernetes.io/managed-by: ${CALICO_FIELD_MANAGER}
spec:
  type: ClusterIP
  selector:
    app.kubernetes.io/name: network-smoke-server
  ports:
    - name: http
      protocol: TCP
      port: 8080
      targetPort: http
---
apiVersion: v1
kind: Pod
metadata:
  name: network-smoke-client
  labels:
    app.kubernetes.io/name: network-smoke-client
    app.kubernetes.io/managed-by: ${CALICO_FIELD_MANAGER}
spec:
  restartPolicy: Never
  automountServiceAccountToken: false
  containers:
    - name: client
      image: ${SMOKE_IMAGE}
      imagePullPolicy: IfNotPresent
      command: ["/agnhost"]
      args: ["pause"]
EOF
}

wait_for_smoke_resources() {
  local namespace_name="$1"

  kubectl_admin \
    -n "${namespace_name}" \
    wait --for=condition=Ready \
    pod/network-smoke-server \
    --timeout="${SMOKE_WAIT_TIMEOUT}"
  kubectl_admin \
    -n "${namespace_name}" \
    wait --for=condition=Ready \
    pod/network-smoke-client \
    --timeout="${SMOKE_WAIT_TIMEOUT}"
}

verify_smoke_service_connection() {
  local namespace_name="$1"

  kubectl_admin -n "${namespace_name}" exec network-smoke-client -- \
    /agnhost connect --timeout=30s network-smoke:8080
}

print_smoke_diagnostics() {
  local namespace_name="$1"

  echo "Workload smoke 진단: ${namespace_name}의 resource와 event를 출력합니다." >&2
  kubectl_admin_short -n "${namespace_name}" get all -o wide 2>&1 \
    || echo "smoke resource를 조회하지 못했습니다." >&2
  kubectl_admin_short -n "${namespace_name}" get events --sort-by=.lastTimestamp 2>&1 \
    || echo "smoke event를 조회하지 못했습니다." >&2
}

run_workload_smoke_test() (
  local namespace_name="$1"
  local namespace_prepared=0

  # EXIT trap이 간접 호출하며, ShellCheck는 문자열 trap의 함수 참조를 추적하지 못합니다.
  # shellcheck disable=SC2329
  cleanup_failed_smoke() {
    if [[ "${namespace_prepared}" -eq 0 ]]; then
      return
    fi
    if ! delete_smoke_namespace "${namespace_name}"; then
      echo "경고: 실패한 smoke namespace를 정리하지 못했습니다: ${namespace_name}" >&2
    fi
  }
  trap cleanup_failed_smoke EXIT

  if ! prepare_smoke_namespace "${namespace_name}"; then
    return 1
  fi
  namespace_prepared=1

  if ! configure_smoke_resources "${namespace_name}" \
    || ! wait_for_smoke_resources "${namespace_name}" \
    || ! verify_smoke_service_connection "${namespace_name}"; then
    print_smoke_diagnostics "${namespace_name}"
    return 1
  fi

  if ! delete_smoke_namespace "${namespace_name}"; then
    echo "smoke 검증은 통과했지만 임시 namespace 정리에 실패했습니다." >&2
    return 1
  fi
  namespace_prepared=0
)

main() (
  local work_directory=""
  local installation_path

  parse_arguments "$@"
  umask 077
  run_preflight_checks

  work_directory="$(create_work_directory)"
  trap 'cleanup_work_directory "${work_directory}"' EXIT
  installation_path="${work_directory}/calico-installation.yaml"

  echo "[계획] exact Calico artifact와 최소 network 계약으로 cluster를 수렴시킵니다."
  echo "[변경] Calico v${CALICO_VERSION} official manifest를 내려받아 고정 SHA-256으로 확인합니다."
  download_calico_artifacts "${work_directory}"
  render_calico_installation "${installation_path}"

  echo "[변경] Calico CRD와 Tigera operator를 server-side apply로 수렴시킵니다."
  apply_calico_base_resources "${work_directory}"
  echo "[변경] iptables·kube-proxy 유지·VXLANCrossSubnet network 계약을 적용합니다."
  apply_calico_installation "${installation_path}"
  wait_for_calico_network

  echo "[변경] 단일 node에서 일반 Pod를 배치하도록 control-plane taint를 제거합니다."
  remove_control_plane_taint
  verify_cluster_network_postconditions

  echo "[검증] 임시 일반 Pod로 CoreDNS, ClusterIP와 Pod network 통신을 확인합니다."
  if ! run_workload_smoke_test "${SMOKE_NAMESPACE}"; then
    fail "일반 workload DNS·Service network smoke 검증에 실패했습니다."
  fi

  echo "[보고] 500-install-calico.sh 단계가 완료됐습니다."
  echo "Kubernetes v${TARGET_KUBERNETES_VERSION} 단일 node 빈 cluster가 일반 Pod를 실행할 준비를 마쳤습니다."
  echo "AWS Load Balancer Controller와 외부 load balancer 제외 label은 이 단계에서 변경하지 않았습니다."
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
