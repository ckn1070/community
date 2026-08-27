#!/usr/bin/env bash

# 단일 ARM64 kubeadm cluster에 AWS Load Balancer Controller를 최초 구성합니다.
set -euo pipefail

readonly KUBERNETES_VERSION="v1.36.2"
readonly CLUSTER_NAME="stg-car-community-k8s"
readonly AWS_REGION="ap-northeast-2"
readonly ADMIN_KUBECONFIG="/etc/kubernetes/admin.conf"
readonly HELM_VERSION="3.21.4"
readonly HELM_ARCHIVE_URL="https://get.helm.sh/helm-v${HELM_VERSION}-linux-arm64.tar.gz"
readonly HELM_ARCHIVE_SHA256="b54c04b4e0b2540bbdc08c17a121dab70e9a2ed0de5705528fec68a5fd3b85a7"
readonly CONTROLLER_CHART_VERSION="3.5.0"
readonly CONTROLLER_CHART_URL="https://aws.github.io/eks-charts/aws-load-balancer-controller-${CONTROLLER_CHART_VERSION}.tgz"
readonly CONTROLLER_CHART_SHA256="45051f634b33e10baccb3354d0681b7de787c60445e599fa276e0c9aedd4ccd5"
readonly CONTROLLER_RELEASE="aws-load-balancer-controller"
readonly CONTROLLER_NAMESPACE="kube-system"
readonly CONTROLLER_CHART_LABEL="aws-load-balancer-controller-${CONTROLLER_CHART_VERSION}"
readonly CONTROLLER_IMAGE_REPOSITORY="public.ecr.aws/eks/aws-load-balancer-controller"
readonly CONTROLLER_IMAGE_DIGEST="sha256:1ee3cc54fb62248bd56f2157f7be79baba2be8f0bbf2d926e2650d7ba710ee5f"
readonly CONTROLLER_IMAGE_TAG_WITH_DIGEST="v${CONTROLLER_CHART_VERSION}@${CONTROLLER_IMAGE_DIGEST}"
readonly CONTROLLER_IMAGE="${CONTROLLER_IMAGE_REPOSITORY}:${CONTROLLER_IMAGE_TAG_WITH_DIGEST}"
readonly CONTROLLER_SERVICE_ACCOUNT="aws-load-balancer-controller"
readonly CONTROLLER_DEPLOYMENT="deployment/aws-load-balancer-controller"
readonly CONTROLLER_HPA="horizontalpodautoscaler/aws-load-balancer-controller"
readonly EXPECTED_NODE_ROLE_NAME="stg-car-community-k8s-node-role"
readonly EXTERNAL_LOAD_BALANCER_EXCLUSION_LABEL="node.kubernetes.io/exclude-from-external-load-balancers"
readonly IMDS_BASE_URL="http://169.254.169.254/latest"
readonly LOCK_PATH="/run/lock/car-community-aws-load-balancer-controller.lock"
readonly -a REQUIRED_COMMANDS=(
  awk
  cat
  chmod
  curl
  flock
  grep
  kubectl
  mkdir
  mktemp
  mv
  rm
  sha256sum
  tar
  uname
)
readonly -a CONTROLLER_CRD_NAMES=(
  albtargetcontrolconfigs.elbv2.k8s.aws
  globalaccelerators.aga.k8s.aws
  ingressclassparams.elbv2.k8s.aws
  targetgroupbindings.elbv2.k8s.aws
  listenerruleconfigurations.gateway.k8s.aws
  loadbalancerconfigurations.gateway.k8s.aws
  targetgroupconfigurations.gateway.k8s.aws
)

RUNTIME_VPC_ID=""
TARGET_NODE_NAME=""
EXPECTED_NODE_PROVIDER_ID=""
RUNTIME_WORK_DIRECTORY=""

print_usage() {
  cat <<EOF
사용법: sudo /home/ubuntu/car-community-scripts/bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh <VPC_ID>

${CLUSTER_NAME}에 AWS Load Balancer Controller ${CONTROLLER_CHART_VERSION}을 설치합니다.
VPC_ID는 AWS Console에서 확인한 long-form ID를 전달합니다. 예: vpc-0123456789abcdef0

이 script는 controller와 CRD만 구성합니다. IAM·subnet tag·ACM certificate·Ingress·ALB·DNS는
생성하거나 변경하지 않습니다.
EOF
}

fail() {
  echo "오류: $*" >&2
  exit 1
}

validate_vpc_id() {
  local vpc_id="$1"

  if [[ ! "${vpc_id}" =~ ^vpc-[0-9a-f]{17}$ ]]; then
    fail "VPC ID는 vpc- 뒤에 소문자 16진수 17자가 오는 long-form 값이어야 합니다: ${vpc_id}"
  fi
}

parse_arguments() {
  if [[ "$#" -eq 1 && ( "$1" == "--help" || "$1" == "-h" ) ]]; then
    print_usage
    exit 0
  fi
  if [[ "$#" -ne 1 ]]; then
    print_usage >&2
    fail "검증한 VPC ID 하나를 positional argument로 전달해야 합니다."
  fi

  validate_vpc_id "$1"
  RUNTIME_VPC_ID="$1"
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    fail "sudo로 실행해야 합니다."
  fi
}

require_commands() {
  local command_name

  for command_name in "${REQUIRED_COMMANDS[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      fail "필수 command를 찾을 수 없습니다: ${command_name}"
    fi
  done
}

require_supported_host() {
  local ID=""
  local VERSION_ID=""
  local operating_system_id
  local operating_system_version
  local machine_architecture

  if [[ ! -r /etc/os-release ]]; then
    fail "지원 OS 정보를 읽을 수 없습니다: /etc/os-release"
  fi

  # Local macOS 검증 환경에는 target의 absolute OS metadata가 없으므로 source 추적만 제외합니다.
  # shellcheck disable=SC1091
  . /etc/os-release
  operating_system_id="${ID}"
  operating_system_version="${VERSION_ID}"
  machine_architecture="$(uname -m)"

  if [[ "${operating_system_id}" != "ubuntu" || "${operating_system_version}" != "24.04" ]]; then
    fail "Ubuntu 24.04만 지원합니다: ${operating_system_id:-없음} ${operating_system_version:-없음}"
  fi
  if [[ "${machine_architecture}" != "aarch64" ]]; then
    fail "Linux ARM64 host만 지원합니다: ${machine_architecture:-없음}"
  fi
}

acquire_execution_lock() {
  exec 9>"${LOCK_PATH}"
  if ! flock --nonblock 9; then
    fail "다른 AWS Load Balancer Controller bootstrap이 진행 중입니다: ${LOCK_PATH}"
  fi
}

kubectl_admin() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=30s "$@"
}

kubectl_admin_long() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=330s "$@"
}

require_admin_kubeconfig_readable() {
  if [[ ! -r "${ADMIN_KUBECONFIG}" ]]; then
    fail "Kubernetes admin kubeconfig를 읽을 수 없습니다: ${ADMIN_KUBECONFIG}"
  fi
}

require_exact_kubeadm_cluster_identity() {
  local cluster_configuration
  local actual_cluster_name
  local actual_kubernetes_version

  if ! cluster_configuration="$(kubectl_admin \
      -n kube-system \
      get configmap/kubeadm-config \
      --allow-missing-template-keys=false \
      -o 'jsonpath={.data.ClusterConfiguration}')"; then
    fail "kubeadm cluster identity를 조회하지 못했습니다."
  fi
  if [[ -z "${cluster_configuration}" ]]; then
    fail "kubeadm ClusterConfiguration이 비어 있습니다."
  fi

  actual_cluster_name="$(awk '$1 == "clusterName:" { print $2 }' \
    <<<"${cluster_configuration}")"
  actual_kubernetes_version="$(awk '$1 == "kubernetesVersion:" { print $2 }' \
    <<<"${cluster_configuration}")"
  if [[ "${actual_cluster_name}" != "${CLUSTER_NAME}" \
      || "${actual_kubernetes_version}" != "${KUBERNETES_VERSION}" ]]; then
    fail "kubeadm cluster identity가 확정 계약과 다릅니다: cluster=${actual_cluster_name:-없음}, version=${actual_kubernetes_version:-없음}"
  fi
}

require_ready_single_node_cluster() {
  local node_record
  local node_count
  local node_name
  local node_architecture
  local kubelet_version
  local ready_status

  require_admin_kubeconfig_readable
  if [[ "$(kubectl_admin get --raw=/readyz 2>/dev/null)" != "ok" ]]; then
    fail "Kubernetes API server readyz를 확인하지 못했습니다."
  fi
  require_exact_kubeadm_cluster_identity
  if ! node_record="$(kubectl_admin get nodes \
      --allow-missing-template-keys=false \
      -o 'jsonpath={range .items[*]}{.metadata.name}|{.status.nodeInfo.architecture}|{.status.nodeInfo.kubeletVersion}|{.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}')"; then
    fail "Kubernetes node 상태를 조회하지 못했습니다."
  fi

  node_count="$(awk 'NF { count += 1 } END { print count + 0 }' <<<"${node_record}")"
  if [[ "${node_count}" -ne 1 ]]; then
    fail "이 bootstrap은 Ready 상태의 단일 ARM64 node만 지원합니다: node ${node_count}개"
  fi

  IFS='|' read -r node_name node_architecture kubelet_version ready_status <<<"${node_record}"
  if [[ -z "${node_name}" || "${node_architecture}" != "arm64" \
      || "${kubelet_version}" != "${KUBERNETES_VERSION}" || "${ready_status}" != "True" ]]; then
    fail "지원 cluster 계약과 node 상태가 다릅니다: name=${node_name:-없음}, arch=${node_architecture:-없음}, kubelet=${kubelet_version:-없음}, Ready=${ready_status:-없음}"
  fi

  TARGET_NODE_NAME="${node_name}"
}

fetch_imds_with_token() {
  local token="$1"
  local metadata_path="$2"

  printf 'X-aws-ec2-metadata-token: %s\n' "${token}" \
    | curl \
      --fail \
      --silent \
      --show-error \
      --noproxy '*' \
      --header @- \
      --connect-timeout 2 \
      --max-time 5 \
      "${IMDS_BASE_URL}/${metadata_path}"
}

set_expected_node_provider_id_from_imds() {
  local role_names="$1"
  local instance_id="$2"
  local availability_zone="$3"
  local role_count

  role_count="$(awk 'NF { count += 1 } END { print count + 0 }' <<<"${role_names}")"
  if [[ "${role_count}" -ne 1 ]]; then
    fail "EC2 instance profile에 정확히 한 role이 있어야 합니다: ${role_count}개"
  fi
  if [[ "${role_names}" != "${EXPECTED_NODE_ROLE_NAME}" ]]; then
    fail "EC2 instance profile role이 확정 계약과 다릅니다: ${role_names}"
  fi
  if [[ ! "${instance_id}" =~ ^i-[0-9a-f]{17}$ ]]; then
    fail "IMDS가 반환한 EC2 instance ID가 exact long-form 형식이 아닙니다: ${instance_id:-없음}"
  fi
  if [[ ! "${availability_zone}" =~ ^${AWS_REGION}[a-z]$ ]]; then
    fail "IMDS가 반환한 Availability Zone이 ${AWS_REGION} 계약과 다릅니다: ${availability_zone:-없음}"
  fi

  EXPECTED_NODE_PROVIDER_ID="aws:///${availability_zone}/${instance_id}"
}

require_imdsv2_and_instance_profile() {
  local unauthenticated_status
  local token
  local role_names
  local instance_id
  local availability_zone

  if ! unauthenticated_status="$(curl \
      --silent \
      --output /dev/null \
      --write-out '%{http_code}' \
      --noproxy '*' \
      --connect-timeout 2 \
      --max-time 5 \
      "${IMDS_BASE_URL}/meta-data/instance-id")"; then
    fail "EC2 IMDS 상태를 확인하지 못했습니다."
  fi
  if [[ "${unauthenticated_status}" != "401" ]]; then
    fail "IMDSv2 token 필수가 아닌 instance에서는 진행하지 않습니다: HTTP ${unauthenticated_status}"
  fi

  if ! token="$(curl \
      --fail \
      --silent \
      --show-error \
      --request PUT \
      --header 'X-aws-ec2-metadata-token-ttl-seconds: 60' \
      --noproxy '*' \
      --connect-timeout 2 \
      --max-time 5 \
      "${IMDS_BASE_URL}/api/token")"; then
    fail "IMDSv2 token을 발급받지 못했습니다."
  fi
  if [[ -z "${token}" ]]; then
    fail "빈 IMDSv2 token을 받았습니다."
  fi
  if ! role_names="$(fetch_imds_with_token \
      "${token}" 'meta-data/iam/security-credentials/')"; then
    fail "EC2 instance profile role을 확인하지 못했습니다."
  fi
  if ! instance_id="$(fetch_imds_with_token \
      "${token}" 'meta-data/instance-id')"; then
    fail "EC2 instance ID를 확인하지 못했습니다."
  fi
  if ! availability_zone="$(fetch_imds_with_token \
      "${token}" 'meta-data/placement/availability-zone')"; then
    fail "EC2 Availability Zone을 확인하지 못했습니다."
  fi

  set_expected_node_provider_id_from_imds \
    "${role_names}" "${instance_id}" "${availability_zone}"
}

current_node_provider_id() {
  local provider_id

  if ! provider_id="$(kubectl_admin get "node/${TARGET_NODE_NAME}" \
      -o 'jsonpath={.spec.providerID}')"; then
    fail "node providerID를 조회하지 못했습니다: ${TARGET_NODE_NAME}"
  fi
  printf '%s\n' "${provider_id}"
}

require_node_provider_id_mutation_safe() {
  local current_provider_id

  current_provider_id="$(current_node_provider_id)"
  if [[ -n "${current_provider_id}" \
      && "${current_provider_id}" != "${EXPECTED_NODE_PROVIDER_ID}" ]]; then
    fail "다른 nonempty node providerID를 자동 변경하지 않습니다: ${current_provider_id}"
  fi
}

ensure_node_provider_id() {
  local current_provider_id
  local verified_provider_id

  current_provider_id="$(current_node_provider_id)"
  if [[ "${current_provider_id}" == "${EXPECTED_NODE_PROVIDER_ID}" ]]; then
    echo "[변경 없음] node providerID가 이미 exact EC2 identity와 일치합니다."
    return
  fi
  if [[ -n "${current_provider_id}" ]]; then
    fail "다른 nonempty node providerID를 자동 변경하지 않습니다: ${current_provider_id}"
  fi

  kubectl_admin patch "node/${TARGET_NODE_NAME}" \
    --type=merge \
    --patch "{\"spec\":{\"providerID\":\"${EXPECTED_NODE_PROVIDER_ID}\"}}"
  verified_provider_id="$(current_node_provider_id)"
  if [[ "${verified_provider_id}" != "${EXPECTED_NODE_PROVIDER_ID}" ]]; then
    fail "node providerID 적용 결과가 exact EC2 identity와 다릅니다: ${verified_provider_id:-없음}"
  fi
}

resource_exists() {
  local resource="$1"
  local namespace="$2"
  local resource_name
  local -a arguments=(get "${resource}")

  if [[ -n "${namespace}" ]]; then
    arguments+=(-n "${namespace}")
  fi
  arguments+=(--ignore-not-found -o name)

  if ! resource_name="$(kubectl_admin "${arguments[@]}")"; then
    fail "기존 Kubernetes resource 상태를 조회하지 못했습니다: ${resource}"
  fi
  [[ -n "${resource_name}" ]]
}

resource_owner_fields() {
  local resource="$1"
  local namespace="$2"
  local -a arguments=(get "${resource}")

  if [[ -n "${namespace}" ]]; then
    arguments+=(-n "${namespace}")
  fi
  arguments+=(-o 'jsonpath={.metadata.labels.app\.kubernetes\.io/managed-by}|{.metadata.annotations.meta\.helm\.sh/release-name}|{.metadata.annotations.meta\.helm\.sh/release-namespace}')
  kubectl_admin "${arguments[@]}"
}

require_helm_owned_if_present() {
  local resource="$1"
  local namespace="$2"
  local owner_fields
  local expected_owner_fields="Helm|${CONTROLLER_RELEASE}|${CONTROLLER_NAMESPACE}"

  if ! resource_exists "${resource}" "${namespace}"; then
    return
  fi
  if ! owner_fields="$(resource_owner_fields "${resource}" "${namespace}")"; then
    fail "기존 Kubernetes resource owner를 조회하지 못했습니다: ${resource}"
  fi
  if [[ "${owner_fields}" != "${expected_owner_fields}" ]]; then
    fail "다른 owner의 Kubernetes resource를 자동 인수하지 않습니다: ${resource} (${owner_fields:-owner 없음})"
  fi
}

require_existing_deployment_matches_target_if_present() {
  local deployment_fields
  local expected_fields="${CONTROLLER_CHART_LABEL}|${CONTROLLER_IMAGE}|1"

  if ! resource_exists "${CONTROLLER_DEPLOYMENT}" "${CONTROLLER_NAMESPACE}"; then
    return
  fi
  require_helm_owned_if_present "${CONTROLLER_DEPLOYMENT}" "${CONTROLLER_NAMESPACE}"
  if ! deployment_fields="$(kubectl_admin get "${CONTROLLER_DEPLOYMENT}" \
      -n "${CONTROLLER_NAMESPACE}" \
      -o 'jsonpath={.metadata.labels.helm\.sh/chart}|{.spec.template.spec.containers[?(@.name=="aws-load-balancer-controller")].image}|{.spec.replicas}')"; then
    fail "기존 controller Deployment version·image·replica를 조회하지 못했습니다."
  fi
  if [[ "${deployment_fields}" != "${expected_fields}" ]]; then
    fail "다른 controller version·image·replica를 자동 upgrade·downgrade하지 않습니다: ${deployment_fields:-없음}"
  fi
}

require_controller_hpa_absent() {
  if resource_exists "${CONTROLLER_HPA}" "${CONTROLLER_NAMESPACE}"; then
    fail "replicaCount=1 고정 계약에서 기존 controller HPA를 자동 인수하지 않습니다."
  fi
}

require_existing_resource_owners() {
  local resource
  local -a namespaced_resources=(
    deployment/aws-load-balancer-controller
    serviceaccount/aws-load-balancer-controller
    service/aws-load-balancer-webhook-service
    secret/aws-load-balancer-tls
    role/aws-load-balancer-controller-leader-election-role
    rolebinding/aws-load-balancer-controller-leader-election-rolebinding
  )
  local -a cluster_resources=(
    clusterrole/aws-load-balancer-controller-role
    clusterrolebinding/aws-load-balancer-controller-rolebinding
    mutatingwebhookconfiguration/aws-load-balancer-webhook
    validatingwebhookconfiguration/aws-load-balancer-webhook
    ingressclass/alb
  )

  for resource in "${namespaced_resources[@]}"; do
    require_helm_owned_if_present "${resource}" "${CONTROLLER_NAMESPACE}"
  done
  for resource in "${cluster_resources[@]}"; do
    require_helm_owned_if_present "${resource}" ""
  done
  if resource_exists \
      customresourcedefinition/ingressclassparams.elbv2.k8s.aws ""; then
    require_helm_owned_if_present ingressclassparams/alb ""
  fi
  require_controller_hpa_absent
  require_existing_deployment_matches_target_if_present
}

helm_admin() {
  local helm_binary="$1"
  local helm_runtime_directory
  shift

  helm_runtime_directory="$(helm_runtime_directory_for_binary "${helm_binary}")"
  KUBECONFIG="${ADMIN_KUBECONFIG}" \
    HELM_CACHE_HOME="${helm_runtime_directory}/cache" \
    HELM_CONFIG_HOME="${helm_runtime_directory}/config" \
    HELM_DATA_HOME="${helm_runtime_directory}/data" \
    "${helm_binary}" "$@" --kubeconfig "${ADMIN_KUBECONFIG}"
}

require_release_and_deployment_consistent() {
  local helm_binary="$1"
  local release_inventory
  local release_exists=0
  local deployment_exists=0

  if ! release_inventory="$(helm_admin "${helm_binary}" list \
      --namespace "${CONTROLLER_NAMESPACE}" \
      --all \
      --filter "^${CONTROLLER_RELEASE}$" \
      --output json)"; then
    fail "기존 Helm release를 조회하지 못했습니다."
  fi
  if [[ -z "${release_inventory}" ]]; then
    fail "Helm release inventory가 빈 응답을 반환했습니다."
  fi
  if [[ "${release_inventory}" != "[]" ]]; then
    if [[ "${release_inventory}" != *"\"name\":\"${CONTROLLER_RELEASE}\""* \
        || "${release_inventory}" != *"\"namespace\":\"${CONTROLLER_NAMESPACE}\""* \
        || "${release_inventory}" != *'"status":"deployed"'* \
        || "${release_inventory}" != *"\"chart\":\"${CONTROLLER_CHART_LABEL}\""* \
        || "${release_inventory}" != *"\"app_version\":\"v${CONTROLLER_CHART_VERSION}\""* ]]; then
      fail "동일 이름의 Helm release owner·status·version이 exact 계약과 다릅니다."
    fi
    release_exists=1
  fi
  if resource_exists "${CONTROLLER_DEPLOYMENT}" "${CONTROLLER_NAMESPACE}"; then
    deployment_exists=1
  fi

  if [[ "${release_exists}" -ne "${deployment_exists}" ]]; then
    fail "Helm release와 controller Deployment가 불일치하는 부분 상태를 자동 인수하지 않습니다."
  fi
}

require_helm_release_status() {
  local helm_binary="$1"

  if ! helm_admin "${helm_binary}" status "${CONTROLLER_RELEASE}" \
      --namespace "${CONTROLLER_NAMESPACE}" >/dev/null; then
    fail "AWS Load Balancer Controller Helm release가 deployed 상태가 아닙니다."
  fi
}

require_safe_existing_installation() {
  local helm_binary="$1"

  require_existing_resource_owners
  require_release_and_deployment_consistent "${helm_binary}"
}

run_preflight_checks() {
  echo "[사전 확인] 지원 host·cluster·IMDS와 node role credential source를 확인합니다."
  require_root
  require_commands
  acquire_execution_lock
  require_supported_host
  require_ready_single_node_cluster
  require_imdsv2_and_instance_profile
  require_node_provider_id_mutation_safe
}

create_work_directory() {
  local work_directory

  if ! work_directory="$(mktemp -d \
      /tmp/car-community-aws-load-balancer-controller.XXXXXX)" \
      || [[ -z "${work_directory}" || ! -d "${work_directory}" ]]; then
    fail "임시 작업 directory를 생성하지 못했습니다."
  fi
  if ! chmod 0700 "${work_directory}"; then
    rm -rf -- "${work_directory}" || :
    fail "임시 작업 directory 권한을 0700으로 제한하지 못했습니다."
  fi
  printf '%s\n' "${work_directory}"
}

cleanup_work_directory() {
  local work_directory="$1"

  if [[ -n "${work_directory}" && -d "${work_directory}" ]]; then
    rm -rf -- "${work_directory}"
  fi
}

download_verified_artifact() {
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
      --retry-max-time 300 \
      --output "${temporary_path}" \
      "${source_url}"; then
    rm -f -- "${temporary_path}"
    fail "고정한 official artifact를 내려받지 못했습니다: ${source_url}"
  fi

  actual_checksum="$(sha256sum "${temporary_path}" | awk '{print $1}')"
  if [[ "${actual_checksum}" != "${expected_checksum}" ]]; then
    rm -f -- "${temporary_path}"
    fail "Official artifact SHA-256이 repository 고정값과 다릅니다: ${source_url}"
  fi

  mv "${temporary_path}" "${destination_path}"
}

helm_runtime_directory_for_binary() {
  local helm_binary="$1"
  local helm_binary_directory="${helm_binary%/*}"
  local helm_extraction_directory="${helm_binary_directory%/*}"

  printf '%s\n' "${helm_extraction_directory}/runtime"
}

extract_verified_helm_client() {
  local archive_path="$1"
  local extraction_directory="$2"
  local helm_binary="${extraction_directory}/linux-arm64/helm"
  local helm_runtime_directory="${extraction_directory}/runtime"
  local helm_output

  if ! mkdir -p "${extraction_directory}"; then
    fail "Helm extraction directory를 생성하지 못했습니다."
  fi
  if ! tar -xzf "${archive_path}" -C "${extraction_directory}"; then
    fail "검증한 Helm archive를 추출하지 못했습니다."
  fi
  if [[ ! -x "${helm_binary}" ]]; then
    fail "검증한 Helm archive에서 Linux ARM64 executable을 찾지 못했습니다."
  fi
  if ! mkdir -p \
      "${helm_runtime_directory}/cache" \
      "${helm_runtime_directory}/config" \
      "${helm_runtime_directory}/data"; then
    fail "임시 Helm runtime directory를 생성하지 못했습니다."
  fi
  if ! helm_output="$(HELM_CACHE_HOME="${helm_runtime_directory}/cache" \
      HELM_CONFIG_HOME="${helm_runtime_directory}/config" \
      HELM_DATA_HOME="${helm_runtime_directory}/data" \
      "${helm_binary}" version --short)"; then
    fail "검증한 임시 Helm client를 실행하지 못했습니다."
  fi
  if [[ "${helm_output}" != "v${HELM_VERSION}+"* ]]; then
    fail "임시 Helm client version이 목표와 다릅니다: ${helm_output:-없음}"
  fi

  printf '%s\n' "${helm_binary}"
}

extract_controller_chart() {
  local archive_path="$1"
  local extraction_directory="$2"
  local chart_directory="${extraction_directory}/aws-load-balancer-controller"

  if ! mkdir -p "${extraction_directory}"; then
    fail "Controller chart extraction directory를 생성하지 못했습니다."
  fi
  if ! tar -xzf "${archive_path}" -C "${extraction_directory}"; then
    fail "검증한 controller chart archive를 추출하지 못했습니다."
  fi
  if [[ ! -r "${chart_directory}/Chart.yaml" \
      || ! -r "${chart_directory}/crds/crds.yaml" \
      || ! -r "${chart_directory}/crds/gateway-crds.yaml" ]]; then
    fail "검증한 chart archive에서 Chart.yaml과 두 CRD manifest를 찾지 못했습니다."
  fi

  printf '%s\n' "${chart_directory}"
}

apply_controller_crds() {
  local chart_directory="$1"
  local crd_name

  if ! kubectl_admin_long apply \
      --server-side \
      --field-manager=car-community-load-balancer-bootstrap \
      -f "${chart_directory}/crds/crds.yaml" \
      -f "${chart_directory}/crds/gateway-crds.yaml"; then
    fail "Controller CRD server-side apply에 실패했습니다."
  fi
  for crd_name in "${CONTROLLER_CRD_NAMES[@]}"; do
    if ! kubectl_admin_long wait \
        --for=condition=Established \
        "customresourcedefinition/${crd_name}" \
        --timeout=120s; then
      fail "Controller CRD Established를 확인하지 못했습니다: ${crd_name}"
    fi
  done
}

install_controller() {
  local helm_binary="$1"
  local chart_directory="$2"
  local vpc_id="$3"

  helm_admin "${helm_binary}" upgrade --install "${CONTROLLER_RELEASE}" "${chart_directory}" \
    --namespace "${CONTROLLER_NAMESPACE}" \
    --set-string "clusterName=${CLUSTER_NAME}" \
    --set-string "region=${AWS_REGION}" \
    --set-string "vpcId=${vpc_id}" \
    --set "replicaCount=1" \
    --set "autoscaling.enabled=false" \
    --set "serviceAccount.create=true" \
    --set-string "serviceAccount.name=${CONTROLLER_SERVICE_ACCOUNT}" \
    --set "rbac.create=true" \
    --set "enableCertManager=false" \
    --set "keepTLSSecret=true" \
    --set "createIngressClassResource=true" \
    --set-string "ingressClass=alb" \
    --set-string "defaultTargetType=instance" \
    --set-string "image.repository=${CONTROLLER_IMAGE_REPOSITORY}" \
    --set-string "image.tag=${CONTROLLER_IMAGE_TAG_WITH_DIGEST}" \
    --atomic \
    --wait \
    --timeout 5m0s \
    --history-max 10
}

verify_required_crds() {
  local crd_name

  for crd_name in "${CONTROLLER_CRD_NAMES[@]}"; do
    kubectl_admin get "customresourcedefinition/${crd_name}" >/dev/null
  done
}

verify_deployment_arguments() {
  local deployment_arguments
  local expected_argument
  local -a expected_arguments=(
    "--cluster-name=${CLUSTER_NAME}"
    "--aws-region=${AWS_REGION}"
    "--aws-vpc-id=${RUNTIME_VPC_ID}"
    "--ingress-class=alb"
  )

  if ! deployment_arguments="$(kubectl_admin get "${CONTROLLER_DEPLOYMENT}" \
      -n "${CONTROLLER_NAMESPACE}" \
      -o 'jsonpath={range .spec.template.spec.containers[?(@.name=="aws-load-balancer-controller")].args[*]}{@}{"\n"}{end}')"; then
    fail "controller Deployment argument를 조회하지 못했습니다."
  fi
  for expected_argument in "${expected_arguments[@]}"; do
    if ! grep -Fqx -- "${expected_argument}" <<<"${deployment_arguments}"; then
      fail "controller Deployment에 exact argument가 없습니다: ${expected_argument}"
    fi
  done
}

verify_running_controller_image() {
  local pod_images
  local image_ids
  local image_id

  if ! pod_images="$(kubectl_admin get pods -n "${CONTROLLER_NAMESPACE}" \
      -l "app.kubernetes.io/name=aws-load-balancer-controller,app.kubernetes.io/instance=${CONTROLLER_RELEASE}" \
      -o 'jsonpath={range .items[*].spec.containers[?(@.name=="aws-load-balancer-controller")]}{.image}{"\n"}{end}')"; then
    fail "controller Pod image를 조회하지 못했습니다."
  fi
  if [[ "${pod_images}" != "${CONTROLLER_IMAGE}" ]]; then
    fail "controller Pod가 exact digest image를 사용하지 않습니다: ${pod_images:-없음}"
  fi
  if ! image_ids="$(kubectl_admin get pods -n "${CONTROLLER_NAMESPACE}" \
      -l "app.kubernetes.io/name=aws-load-balancer-controller,app.kubernetes.io/instance=${CONTROLLER_RELEASE}" \
      -o 'jsonpath={range .items[*].status.containerStatuses[?(@.name=="aws-load-balancer-controller")]}{.imageID}{"\n"}{end}')"; then
    fail "controller Pod runtime imageID를 조회하지 못했습니다."
  fi
  if [[ -z "${image_ids}" ]]; then
    fail "controller Pod runtime imageID가 없습니다."
  fi
  while IFS= read -r image_id; do
    if [[ "${image_id}" != *"@${CONTROLLER_IMAGE_DIGEST}" ]]; then
      fail "controller Pod runtime imageID가 고정 digest와 다릅니다: ${image_id}"
    fi
  done <<<"${image_ids}"
}

verify_controller_postconditions() {
  local helm_binary="$1"
  local deployment_status

  require_helm_release_status "${helm_binary}"
  kubectl_admin_long rollout status \
    -n "${CONTROLLER_NAMESPACE}" \
    "${CONTROLLER_DEPLOYMENT}" \
    --timeout=300s
  kubectl_admin_long wait \
    -n "${CONTROLLER_NAMESPACE}" \
    --for=condition=Ready \
    pod \
    -l "app.kubernetes.io/name=aws-load-balancer-controller,app.kubernetes.io/instance=${CONTROLLER_RELEASE}" \
    --timeout=300s

  require_existing_resource_owners
  if ! deployment_status="$(kubectl_admin get "${CONTROLLER_DEPLOYMENT}" \
      -n "${CONTROLLER_NAMESPACE}" \
      -o 'jsonpath={.spec.replicas}|{.status.updatedReplicas}|{.status.availableReplicas}')"; then
    fail "controller Deployment rollout 상태를 조회하지 못했습니다."
  fi
  if [[ "${deployment_status}" != "1|1|1" ]]; then
    fail "controller Deployment replica가 모두 available하지 않습니다: ${deployment_status:-없음}"
  fi

  verify_required_crds
  kubectl_admin get service/aws-load-balancer-webhook-service \
    -n "${CONTROLLER_NAMESPACE}" >/dev/null
  kubectl_admin get mutatingwebhookconfiguration/aws-load-balancer-webhook >/dev/null
  kubectl_admin get validatingwebhookconfiguration/aws-load-balancer-webhook >/dev/null
  kubectl_admin get ingressclass/alb >/dev/null
  kubectl_admin get ingressclassparams/alb >/dev/null
  verify_deployment_arguments
  verify_running_controller_image
}

node_has_external_load_balancer_exclusion_label() {
  local node_json

  if ! node_json="$(kubectl_admin get "node/${TARGET_NODE_NAME}" -o json)"; then
    fail "node label 상태를 조회하지 못했습니다: ${TARGET_NODE_NAME}"
  fi
  grep -Fq -- "\"${EXTERNAL_LOAD_BALANCER_EXCLUSION_LABEL}\":" <<<"${node_json}"
}

remove_external_load_balancer_exclusion_label() {
  if ! node_has_external_load_balancer_exclusion_label; then
    echo "[변경 없음] 외부 Load Balancer 제외 node label이 이미 없습니다."
    return
  fi

  kubectl_admin label \
    "node/${TARGET_NODE_NAME}" \
    "${EXTERNAL_LOAD_BALANCER_EXCLUSION_LABEL}-"
}

verify_external_load_balancer_exclusion_label_absent() {
  if node_has_external_load_balancer_exclusion_label; then
    fail "외부 Load Balancer 제외 node label이 남아 있습니다: ${TARGET_NODE_NAME}"
  fi
}

main() {
  local helm_archive_path
  local helm_extraction_directory
  local helm_binary
  local chart_archive_path
  local chart_extraction_directory
  local chart_directory

  parse_arguments "$@"
  run_preflight_checks
  RUNTIME_WORK_DIRECTORY="$(create_work_directory)"
  trap 'cleanup_work_directory "${RUNTIME_WORK_DIRECTORY:-}"' EXIT
  helm_archive_path="${RUNTIME_WORK_DIRECTORY}/helm-v${HELM_VERSION}-linux-arm64.tar.gz"
  helm_extraction_directory="${RUNTIME_WORK_DIRECTORY}/helm"
  chart_archive_path="${RUNTIME_WORK_DIRECTORY}/aws-load-balancer-controller-${CONTROLLER_CHART_VERSION}.tgz"
  chart_extraction_directory="${RUNTIME_WORK_DIRECTORY}/chart"

  echo "[다운로드] Helm v${HELM_VERSION} Linux ARM64 archive를 검증합니다."
  download_verified_artifact \
    "${HELM_ARCHIVE_URL}" "${HELM_ARCHIVE_SHA256}" "${helm_archive_path}"
  helm_binary="$(extract_verified_helm_client \
    "${helm_archive_path}" "${helm_extraction_directory}")"
  echo "[다운로드] AWS Load Balancer Controller chart ${CONTROLLER_CHART_VERSION}을 검증합니다."
  download_verified_artifact \
    "${CONTROLLER_CHART_URL}" "${CONTROLLER_CHART_SHA256}" "${chart_archive_path}"
  chart_directory="$(extract_controller_chart \
    "${chart_archive_path}" "${chart_extraction_directory}")"

  echo "[사전 확인] 기존 controller Helm release와 resource owner를 확인합니다."
  require_safe_existing_installation "${helm_binary}"
  echo "[적용] Instance target registration에 필요한 node providerID를 exact EC2 identity로 구성합니다."
  ensure_node_provider_id
  echo "[적용] Chart CRD를 Helm release보다 먼저 server-side apply합니다."
  apply_controller_crds "${chart_directory}"
  echo "[적용] AWS Load Balancer Controller Helm release를 목표 상태로 구성합니다."
  install_controller "${helm_binary}" "${chart_directory}" "${RUNTIME_VPC_ID}"
  echo "[검증] Controller release·resource·Pod·webhook postcondition을 확인합니다."
  verify_controller_postconditions "${helm_binary}"
  echo "[변경] 검증된 단일 node를 외부 Load Balancer target에서 제외하지 않도록 설정합니다."
  remove_external_load_balancer_exclusion_label
  verify_external_load_balancer_exclusion_label_absent
  echo "[완료] AWS Load Balancer Controller ${CONTROLLER_CHART_VERSION} bootstrap을 확인했습니다."
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
