#!/usr/bin/env bash

# UI와 API server가 없는 Argo CD Core를 단일 cluster에 설치합니다.
set -euo pipefail

readonly ARGOCD_VERSION="3.5.0"
readonly ARGOCD_IMAGE_TAG="quay.io/argoproj/argocd:v${ARGOCD_VERSION}"
readonly ARGOCD_IMAGE_DIGEST="sha256:8a14921f7e5dc3408111714f05bff68e337d459653c45f0aea738330d156079b"
readonly ARGOCD_IMAGE="quay.io/argoproj/argocd@${ARGOCD_IMAGE_DIGEST}"
readonly ARGOCD_REDIS_IMAGE_TAG="public.ecr.aws/docker/library/redis:8.2.3-alpine"
readonly ARGOCD_REDIS_IMAGE_DIGEST="sha256:2fe148588d93987cea3533873195ec2450f5dbf420c78606aaacad7b78bb5489"
readonly ARGOCD_REDIS_IMAGE="public.ecr.aws/docker/library/redis@${ARGOCD_REDIS_IMAGE_DIGEST}"
readonly ARGOCD_CORE_MANIFEST_URL="https://raw.githubusercontent.com/argoproj/argo-cd/v${ARGOCD_VERSION}/manifests/core-install.yaml"
readonly ARGOCD_CORE_MANIFEST_SHA256="166bffca5000482f8ae36d73b255610c9bb4c7d8da38fcf62003c2fdd8ab0747"
readonly ARGOCD_NAMESPACE="argocd"
readonly ADMIN_KUBECONFIG="/etc/kubernetes/admin.conf"
readonly LOCK_PATH="/run/lock/car-community-argocd-core.lock"
readonly MANAGED_BY_LABEL="car-community-argocd-core-bootstrap"

print_usage() {
  cat <<EOF
사용법: sudo /home/ubuntu/car-community-scripts/bootstrap/gitops/100-install-argocd-core.sh

Kubernetes 1.36 cluster에 Argo CD Core v${ARGOCD_VERSION}를 설치합니다.
UI·API server·외부 endpoint·Git repository credential은 만들지 않습니다.
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
  local -a required_commands=(awk cat chmod curl flock grep kubectl mktemp mv rm sed sha256sum)

  for command_name in "${required_commands[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      fail "필수 command를 찾을 수 없습니다: ${command_name}"
    fi
  done
}

acquire_execution_lock() {
  exec 9>"${LOCK_PATH}"
  if ! flock --nonblock 9; then
    fail "다른 Argo CD Core 설치가 진행 중입니다: ${LOCK_PATH}"
  fi
}

kubectl_admin() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=30s "$@"
}

kubectl_admin_long() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=330s "$@"
}

require_ready_cluster() {
  local node_architectures

  if [[ ! -r "${ADMIN_KUBECONFIG}" ]]; then
    fail "Kubernetes admin kubeconfig를 읽을 수 없습니다: ${ADMIN_KUBECONFIG}"
  fi
  if [[ "$(kubectl_admin get --raw=/readyz 2>/dev/null)" != "ok" ]]; then
    fail "Kubernetes API server readyz를 확인하지 못했습니다."
  fi
  if ! node_architectures="$(kubectl_admin get nodes \
      -o jsonpath='{range .items[*]}{.status.nodeInfo.architecture}{"\n"}{end}')"; then
    fail "Kubernetes node architecture를 조회하지 못했습니다."
  fi
  if [[ "${node_architectures}" != "arm64" ]]; then
    fail "이 bootstrap은 단일 ARM64 node만 지원합니다: ${node_architectures:-없음}"
  fi
}

resource_exists() {
  local resource="$1"
  local resource_name

  if ! resource_name="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" \
      "${resource}" --ignore-not-found -o name)"; then
    fail "기존 Argo CD resource 상태를 조회하지 못했습니다: ${resource}"
  fi
  [[ -n "${resource_name}" ]]
}

require_not_full_installation() {
  if resource_exists "deployment/argocd-server" || resource_exists "service/argocd-server"; then
    fail "argocd-server를 포함한 기존 설치를 Core로 자동 전환하지 않습니다."
  fi
}

require_managed_or_absent_namespace() {
  local namespace_name
  local managed_by

  if ! namespace_name="$(kubectl_admin get "namespace/${ARGOCD_NAMESPACE}" \
      --ignore-not-found -o name)"; then
    fail "기존 Argo CD namespace 상태를 조회하지 못했습니다."
  fi
  if [[ -z "${namespace_name}" ]]; then
    return
  fi

  if ! managed_by="$(kubectl_admin get "namespace/${ARGOCD_NAMESPACE}" \
      -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')"; then
    fail "기존 Argo CD namespace owner를 조회하지 못했습니다."
  fi
  if [[ "${managed_by}" != "${MANAGED_BY_LABEL}" ]]; then
    fail "다른 owner의 argocd namespace를 자동 인수하지 않습니다."
  fi
}

expected_component_images() {
  local resource="$1"

  case "${resource}" in
    deployment/argocd-applicationset-controller | statefulset/argocd-application-controller)
      printf '%s\n' "${ARGOCD_IMAGE}"
      ;;
    deployment/argocd-redis)
      printf '%s\n' "${ARGOCD_REDIS_IMAGE}" "${ARGOCD_IMAGE}"
      ;;
    deployment/argocd-repo-server)
      printf '%s\n' "${ARGOCD_IMAGE}" "${ARGOCD_IMAGE}"
      ;;
    *)
      fail "지원하지 않는 Argo CD Core component입니다: ${resource}"
      ;;
  esac
}

require_component_uses_target_images_if_present() {
  local resource="$1"
  local image_list
  local expected_images

  if ! resource_exists "${resource}"; then
    return
  fi

  if ! image_list="$(kubectl_admin get -n "${ARGOCD_NAMESPACE}" "${resource}" \
      -o jsonpath='{range .spec.template.spec.containers[*]}{.image}{"\n"}{end}{range .spec.template.spec.initContainers[*]}{.image}{"\n"}{end}')"; then
    fail "기존 Argo CD component image를 조회하지 못했습니다: ${resource}"
  fi
  if [[ -z "${image_list}" ]]; then
    fail "기존 Argo CD component에 container image가 없습니다: ${resource}"
  fi

  expected_images="$(expected_component_images "${resource}")"
  if [[ "${image_list}" != "${expected_images}" ]]; then
    fail "다른 Argo CD Core component image 구성을 자동 upgrade·downgrade하지 않습니다: ${resource}"
  fi
}

require_safe_existing_installation() {
  local resource
  local -a core_resources=(
    deployment/argocd-applicationset-controller
    deployment/argocd-redis
    deployment/argocd-repo-server
    statefulset/argocd-application-controller
  )

  require_not_full_installation
  require_managed_or_absent_namespace
  for resource in "${core_resources[@]}"; do
    require_component_uses_target_images_if_present "${resource}"
  done
}

pin_runtime_images() {
  local source_path="$1"
  local destination_path="$2"
  local temporary_path="${destination_path}.tmp"

  if ! grep -Fq -- "image: ${ARGOCD_IMAGE_TAG}" "${source_path}"; then
    fail "Official manifest에서 목표 Argo CD image tag를 찾지 못했습니다."
  fi
  if ! grep -Fq -- "image: ${ARGOCD_REDIS_IMAGE_TAG}" "${source_path}"; then
    fail "Official manifest에서 목표 Redis image tag를 찾지 못했습니다."
  fi

  sed \
    -e "s#image: ${ARGOCD_IMAGE_TAG}#image: ${ARGOCD_IMAGE}#g" \
    -e "s#image: ${ARGOCD_REDIS_IMAGE_TAG}#image: ${ARGOCD_REDIS_IMAGE}#g" \
    "${source_path}" >"${temporary_path}"

  if grep -Fq -- "image: ${ARGOCD_IMAGE_TAG}" "${temporary_path}" \
      || grep -Fq -- "image: ${ARGOCD_REDIS_IMAGE_TAG}" "${temporary_path}"; then
    rm -f -- "${temporary_path}"
    fail "Runtime image tag를 immutable digest로 모두 바꾸지 못했습니다."
  fi
  if ! grep -Fq -- "image: ${ARGOCD_IMAGE}" "${temporary_path}" \
      || ! grep -Fq -- "image: ${ARGOCD_REDIS_IMAGE}" "${temporary_path}"; then
    rm -f -- "${temporary_path}"
    fail "Digest-pinned runtime image를 rendered manifest에서 확인하지 못했습니다."
  fi

  mv "${temporary_path}" "${destination_path}"
}

apply_argocd_namespace() {
  cat <<EOF | kubectl_admin apply --server-side --field-manager=car-community-bootstrap -f -
apiVersion: v1
kind: Namespace
metadata:
  name: ${ARGOCD_NAMESPACE}
  labels:
    app.kubernetes.io/managed-by: ${MANAGED_BY_LABEL}
EOF
}

run_preflight_checks() {
  echo "[사전 확인] Cluster와 기존 Argo CD 설치 경계를 확인합니다."
  require_root
  require_commands
  acquire_execution_lock
  require_ready_cluster
  require_safe_existing_installation
}

create_work_directory() {
  local work_directory

  work_directory="$(mktemp -d /tmp/car-community-argocd-core.XXXXXX)"
  chmod 0700 "${work_directory}"
  printf '%s\n' "${work_directory}"
}

cleanup_work_directory() {
  local work_directory="$1"

  if [[ -n "${work_directory}" && -d "${work_directory}" ]]; then
    rm -rf -- "${work_directory}"
  fi
}

download_verified_manifest() {
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
    fail "Argo CD Core manifest를 내려받지 못했습니다."
  fi

  actual_checksum="$(sha256sum "${temporary_path}" | awk '{print $1}')"
  if [[ "${actual_checksum}" != "${expected_checksum}" ]]; then
    rm -f -- "${temporary_path}"
    fail "Argo CD Core manifest SHA-256이 repository 고정값과 다릅니다."
  fi

  mv "${temporary_path}" "${destination_path}"
}

apply_argocd_core() {
  local manifest_path="$1"

  apply_argocd_namespace
  kubectl_admin apply \
    --server-side \
    --force-conflicts \
    --field-manager=car-community-bootstrap \
    -n "${ARGOCD_NAMESPACE}" \
    -f "${manifest_path}"
}

verify_running_image_ids() {
  local image_id_list
  local image_id
  local saw_argocd=0
  local saw_redis=0

  if ! image_id_list="$(kubectl_admin get pods -n "${ARGOCD_NAMESPACE}" \
      -o jsonpath='{range .items[*]}{range .status.containerStatuses[*]}{.imageID}{"\n"}{end}{range .status.initContainerStatuses[*]}{.imageID}{"\n"}{end}{end}')"; then
    fail "Argo CD Core Pod imageID를 조회하지 못했습니다."
  fi
  if [[ -z "${image_id_list}" ]]; then
    fail "Argo CD Core Pod의 실행 imageID가 없습니다."
  fi

  while IFS= read -r image_id; do
    case "${image_id}" in
      "${ARGOCD_IMAGE}")
        saw_argocd=1
        ;;
      "${ARGOCD_REDIS_IMAGE}")
        saw_redis=1
        ;;
      *)
        fail "고정하지 않은 runtime imageID를 발견했습니다: ${image_id}"
        ;;
    esac
  done <<<"${image_id_list}"

  if [[ "${saw_argocd}" -ne 1 || "${saw_redis}" -ne 1 ]]; then
    fail "Argo CD와 Redis의 고정 runtime imageID를 모두 확인하지 못했습니다."
  fi
}

wait_for_core_components() {
  local resource
  local -a core_resources=(
    deployment/argocd-applicationset-controller
    deployment/argocd-redis
    deployment/argocd-repo-server
    statefulset/argocd-application-controller
  )

  for resource in "${core_resources[@]}"; do
    kubectl_admin_long rollout status -n "${ARGOCD_NAMESPACE}" "${resource}" --timeout=300s
  done

  if [[ "$(kubectl_admin get "namespace/${ARGOCD_NAMESPACE}" \
      -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')" != "${MANAGED_BY_LABEL}" ]]; then
    fail "argocd namespace owner label이 목표와 다릅니다."
  fi
}

verify_postconditions() {
  local resource
  local -a core_resources=(
    deployment/argocd-applicationset-controller
    deployment/argocd-redis
    deployment/argocd-repo-server
    statefulset/argocd-application-controller
  )

  for resource in "${core_resources[@]}"; do
    if ! resource_exists "${resource}"; then
      fail "필수 Argo CD Core component가 없습니다: ${resource}"
    fi
    require_component_uses_target_images_if_present "${resource}"
  done

  if resource_exists "deployment/argocd-server" || resource_exists "service/argocd-server"; then
    fail "Core 설치 완료 뒤에도 argocd-server가 없어야 합니다."
  fi
  kubectl_admin get customresourcedefinition/applications.argoproj.io >/dev/null
  kubectl_admin get customresourcedefinition/appprojects.argoproj.io >/dev/null
  verify_running_image_ids

  echo "[완료] Argo CD Core v${ARGOCD_VERSION} component와 CRD를 확인했습니다."
}

main() {
  local work_directory=""
  local downloaded_manifest_path
  local pinned_manifest_path

  parse_arguments "$@"
  run_preflight_checks
  work_directory="$(create_work_directory)"
  trap 'cleanup_work_directory "${work_directory:-}"' EXIT
  downloaded_manifest_path="${work_directory}/core-install.yaml"
  pinned_manifest_path="${work_directory}/core-install-pinned.yaml"

  echo "[다운로드] Argo CD Core v${ARGOCD_VERSION} official manifest를 검증합니다."
  download_verified_manifest \
    "${ARGOCD_CORE_MANIFEST_URL}" \
    "${ARGOCD_CORE_MANIFEST_SHA256}" \
    "${downloaded_manifest_path}"
  echo "[고정] Runtime image tag를 검증한 ARM64 manifest digest로 교체합니다."
  pin_runtime_images "${downloaded_manifest_path}" "${pinned_manifest_path}"
  echo "[적용] Argo CD Core resource를 server-side apply합니다."
  apply_argocd_core "${pinned_manifest_path}"
  echo "[대기] Argo CD Core component rollout을 확인합니다."
  wait_for_core_components
  verify_postconditions
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
