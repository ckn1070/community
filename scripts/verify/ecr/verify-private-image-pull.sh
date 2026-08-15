#!/usr/bin/env bash

# Private ECR image를 exact digest로 pull하고 단기 Job 실행까지 검증합니다.
set -euo pipefail

readonly AWS_REGION="ap-northeast-2"
readonly ECR_REPOSITORY="car-community"
readonly KUBECONFIG_PATH="/etc/kubernetes/admin.conf"
readonly SMOKE_NAMESPACE="car-community-ecr-smoke"
readonly SMOKE_JOB="private-image-pull"
readonly KUBECTL_REQUEST_TIMEOUT="30s"
readonly SMOKE_WAIT_TIMEOUT="180s"
readonly SMOKE_WAIT_REQUEST_TIMEOUT="200s"
readonly SMOKE_DELETE_TIMEOUT="120s"
readonly SMOKE_DELETE_REQUEST_TIMEOUT="140s"
readonly LOCK_PATH="/run/lock/car-community-ecr-pull-smoke.lock"

print_usage() {
  cat <<EOF
사용법: sudo /home/ubuntu/car-community-scripts/verify/ecr/verify-private-image-pull.sh \\
  <ACCOUNT_ID>.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}@sha256:<DIGEST>

고정 digest의 private ECR image를 임시 Kubernetes Job으로 pull·실행하고 실제 imageID가
요청 digest와 같은지 검증한 뒤 임시 namespace를 삭제합니다. Mutable tag는 받지 않습니다.
EOF
}

fail() {
  echo "오류: $*" >&2
  return 1
}

parse_arguments() {
  if [[ "$#" -eq 1 && ( "$1" == "--help" || "$1" == "-h" ) ]]; then
    print_usage
    exit 0
  fi
  if [[ "$#" -ne 1 ]]; then
    print_usage >&2
    exit 1
  fi

  require_valid_image_reference "$1"
}

require_valid_image_reference() {
  local image_reference="$1"
  local reference_pattern="^[0-9]{12}\\.dkr\\.ecr\\.${AWS_REGION}\\.amazonaws\\.com/${ECR_REPOSITORY}@sha256:[0-9a-f]{64}$"

  if [[ ! "${image_reference}" =~ ${reference_pattern} ]]; then
    fail "${AWS_REGION}의 ${ECR_REPOSITORY} private ECR image exact digest만 사용할 수 있습니다."
  fi
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    fail "sudo로 실행해야 합니다."
  fi
}

require_commands() {
  local command_name
  local -a required_commands=(awk cat flock grep kubectl)

  for command_name in "${required_commands[@]}"; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      fail "필수 command를 찾을 수 없습니다: ${command_name}"
    fi
  done
}

acquire_execution_lock() {
  exec 9>"${LOCK_PATH}"
  if ! flock --nonblock 9; then
    fail "다른 private image pull smoke 실행이 진행 중입니다: ${LOCK_PATH}"
  fi
}

kubectl_admin() {
  kubectl --kubeconfig "${KUBECONFIG_PATH}" --request-timeout="${KUBECTL_REQUEST_TIMEOUT}" "$@"
}

kubectl_admin_short() {
  kubectl --kubeconfig "${KUBECONFIG_PATH}" --request-timeout=10s "$@"
}

require_cluster_access() {
  if [[ ! -s "${KUBECONFIG_PATH}" ]]; then
    fail "${KUBECONFIG_PATH}가 없습니다. Kubernetes cluster 구축 완료 상태가 아닙니다."
  fi
  if [[ "$(kubectl_admin_short get --raw='/readyz')" != "ok" ]]; then
    fail "Kubernetes API server readyz 확인을 통과하지 못했습니다."
  fi
}

require_smoke_namespace_absent() {
  local existing_namespace

  if ! existing_namespace="$(kubectl_admin_short get namespace "${SMOKE_NAMESPACE}" --ignore-not-found -o name)"; then
    fail "기존 smoke namespace 상태를 조회하지 못했습니다."
  fi
  if [[ -n "${existing_namespace}" ]]; then
    fail "기존 ${SMOKE_NAMESPACE} namespace를 자동 삭제하지 않습니다. 원인을 확인한 뒤 수동 정리하세요."
  fi
}

run_preflight_checks() {
  local image_reference="$1"

  echo "[사전 확인] cluster 접근과 private ECR image reference를 확인합니다."
  require_valid_image_reference "${image_reference}"
  require_root
  require_commands
  acquire_execution_lock
  require_cluster_access
  require_smoke_namespace_absent
}

create_smoke_namespace() {
  local namespace="$1"

  kubectl_admin create namespace "${namespace}"
}

create_smoke_job() {
  local namespace="$1"
  local image_reference="$2"

  cat <<EOF | kubectl_admin create --filename -
apiVersion: batch/v1
kind: Job
metadata:
  name: ${SMOKE_JOB}
  namespace: ${namespace}
spec:
  backoffLimit: 0
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: smoke
          image: ${image_reference}
          imagePullPolicy: Always
          command:
            - java
            - -version
EOF
}

wait_for_smoke_job() {
  local namespace="$1"

  kubectl --kubeconfig "${KUBECONFIG_PATH}" \
    --request-timeout="${SMOKE_WAIT_REQUEST_TIMEOUT}" \
    -n "${namespace}" wait \
    --for=condition=complete \
    --timeout="${SMOKE_WAIT_TIMEOUT}" \
    "job/${SMOKE_JOB}"
}

verify_pulled_image_digest() {
  local namespace="$1"
  local expected_digest="$2"
  local actual_image_id

  actual_image_id="$(kubectl_admin -n "${namespace}" get pods \
    -l "job-name=${SMOKE_JOB}" \
    -o 'jsonpath={.items[?(@.status.phase=="Succeeded")].status.containerStatuses[0].imageID}')"
  if [[ "${actual_image_id}" != *@"${expected_digest}" ]]; then
    fail "실제 pulled imageID가 요청 digest와 다릅니다: ${actual_image_id:-확인 불가}"
  fi
}

print_smoke_logs() {
  local namespace="$1"

  kubectl_admin -n "${namespace}" logs "job/${SMOKE_JOB}"
}

print_smoke_diagnostics() {
  local namespace="$1"

  kubectl_admin -n "${namespace}" get job,pod -o wide >&2 || true
  kubectl_admin -n "${namespace}" describe "job/${SMOKE_JOB}" >&2 || true
  kubectl_admin -n "${namespace}" get events --sort-by=.lastTimestamp >&2 || true
}

delete_smoke_namespace() {
  local namespace="$1"

  kubectl --kubeconfig "${KUBECONFIG_PATH}" \
    --request-timeout="${SMOKE_DELETE_REQUEST_TIMEOUT}" \
    delete namespace "${namespace}" \
    --wait=true \
    --timeout="${SMOKE_DELETE_TIMEOUT}"
}

run_private_image_pull_smoke() {
  local image_reference="$1"
  local expected_digest="sha256:${image_reference##*@sha256:}"
  local operation_status=0

  create_smoke_namespace "${SMOKE_NAMESPACE}"

  if ! create_smoke_job "${SMOKE_NAMESPACE}" "${image_reference}"; then
    operation_status=1
  elif ! wait_for_smoke_job "${SMOKE_NAMESPACE}"; then
    operation_status=1
  elif ! verify_pulled_image_digest "${SMOKE_NAMESPACE}" "${expected_digest}"; then
    operation_status=1
  elif ! print_smoke_logs "${SMOKE_NAMESPACE}"; then
    operation_status=1
  fi

  if [[ "${operation_status}" -ne 0 ]]; then
    print_smoke_diagnostics "${SMOKE_NAMESPACE}"
  fi
  if ! delete_smoke_namespace "${SMOKE_NAMESPACE}"; then
    fail "smoke namespace 삭제를 완료하지 못했습니다: ${SMOKE_NAMESPACE}"
  fi
  if [[ "${operation_status}" -ne 0 ]]; then
    fail "private ECR image pull smoke 검증에 실패했습니다."
    return 1
  fi

  echo "[완료] Private ECR image의 pull·실행·exact digest 검증을 통과했습니다."
}

main() {
  local image_reference="$1"

  run_preflight_checks "${image_reference}"
  run_private_image_pull_smoke "${image_reference}"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  parse_arguments "$@"
  main "$1"
fi
