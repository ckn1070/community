#!/usr/bin/env bash

# Community cursor HMAC Secret을 생성·수렴시키고 새 runtime Pod에 반영합니다.
set -euo pipefail
set +x

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIRECTORY
# shellcheck source=../../lib/application-secret.sh
source "${SCRIPT_DIRECTORY}/../../lib/application-secret.sh"

readonly ADMIN_KUBECONFIG="/etc/kubernetes/admin.conf"
readonly APPLICATION_NAMESPACE="car-community"
readonly APPLICATION_DEPLOYMENT="car-community"
readonly APPLICATION_OWNER="car-community-application-bootstrap"
readonly ARGOCD_OWNER="argocd"
readonly SECRET_FIELD_MANAGER="car-community-application-bootstrap"
readonly ROLLOUT_ANNOTATION="car-community.io/community-secret-resource-version"
readonly LOCK_PATH="/run/lock/car-community-application-prerequisites.lock"

COMMUNITY_CURSOR_HMAC_SECRET=""

print_usage() {
  cat <<'EOF'
사용법:
  printf '%s\0' "${STG_COMMUNITY_CURSOR_HMAC_SECRET}" \
  | env -i HOME="${HOME}" PATH="${PATH}" ssh ubuntu@<EC2> \
    'sudo -n /home/ubuntu/car-community-scripts/operations/application/reconcile-community-secret.sh'

Community cursor HMAC Secret 하나를 생성하거나 수렴시킵니다. 실제 값은 argument·log·영구 file에
기록하지 않으며 인증 HMAC Secret과 다른 32 bytes 이상의 값만 허용합니다.
EOF
}

fail() {
  echo "오류: $*" >&2
  exit 1
}

read_community_input() {
  local trailing_input=""

  export -n COMMUNITY_CURSOR_HMAC_SECRET
  if [[ -t 0 ]]; then
    fail "Secret은 terminal이 아니라 NUL-delimited stdin으로 전달해야 합니다."
  fi
  if ! IFS= read -r -d '' COMMUNITY_CURSOR_HMAC_SECRET; then
    fail "STG_COMMUNITY_CURSOR_HMAC_SECRET이 누락되었거나 NUL로 끝나지 않습니다."
  fi
  if IFS= read -r -d '' trailing_input || [[ -n "${trailing_input}" ]]; then
    fail "stdin에는 정확히 한 field만 있어야 합니다."
  fi
}

validate_community_input() {
  local byte_length

  byte_length="$(LC_ALL=C printf '%s' "${COMMUNITY_CURSOR_HMAC_SECRET}" | wc -c | tr -d '[:space:]')"
  if [[ -z "${COMMUNITY_CURSOR_HMAC_SECRET//[[:space:]]/}" \
      || "${COMMUNITY_CURSOR_HMAC_SECRET}" == *$'\n'* \
      || "${COMMUNITY_CURSOR_HMAC_SECRET}" == *$'\r'* \
      || "${byte_length}" -lt 32 ]]; then
    fail "Community cursor HMAC secret은 줄바꿈 없는 32 bytes 이상의 값이어야 합니다."
  fi
}

kubectl_admin() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=30s -n "${APPLICATION_NAMESPACE}" "$@"
}

kubectl_cluster() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=30s "$@"
}

kubectl_admin_long() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=330s -n "${APPLICATION_NAMESPACE}" "$@"
}

require_commands() {
  local command_name
  local -a required_commands=(base64 flock kubectl tr wc)

  for command_name in "${required_commands[@]}"; do
    command -v "${command_name}" >/dev/null 2>&1 \
      || fail "필수 command를 찾을 수 없습니다: ${command_name}"
  done
}

require_runtime_and_secret_boundary() {
  local deployment_owner
  local namespace_owner
  local secret_state

  if ! namespace_owner="$(kubectl_cluster get "namespace/${APPLICATION_NAMESPACE}" \
      -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')"; then
    fail "Application namespace를 조회하지 못했습니다."
  fi
  [[ "${namespace_owner}" == "${APPLICATION_OWNER}" ]] \
    || fail "Application bootstrap 소유 namespace가 필요합니다."

  if ! secret_state="$(application_secret_community_state "${APPLICATION_NAMESPACE}")"; then
    fail "Community Secret 상태를 조회하지 못했습니다."
  fi
  if [[ -n "${secret_state}" && "${secret_state}" != "${APPLICATION_OWNER}|Opaque|1|"* ]]; then
    fail "다른 owner 또는 schema의 Community Secret을 변경하지 않습니다."
  fi

  if ! deployment_owner="$(kubectl_admin get "deployment/${APPLICATION_DEPLOYMENT}" \
      -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')"; then
    fail "기존 runtime Deployment가 필요합니다."
  fi
  [[ "${deployment_owner}" == "${ARGOCD_OWNER}" ]] \
    || fail "Argo CD 소유 runtime Deployment만 rollout합니다."
}

require_distinct_from_auth_hmac_secrets() {
  local auth_state
  local auth_identifier_encoded
  local auth_jwt_encoded
  local auth_owner
  local auth_type
  local key_count
  local expected_encoded_value

  expected_encoded_value="$(application_secret_base64_no_wrap "${COMMUNITY_CURSOR_HMAC_SECRET}")"
  if ! auth_state="$(kubectl_admin get secret/car-community-auth \
      -o 'go-template={{index .metadata.labels "app.kubernetes.io/managed-by"}}|{{.type}}|{{len .data}}|{{index .data "AUTH_JWT_HMAC_SECRET"}}|{{index .data "AUTH_LOGIN_IDENTIFIER_HMAC_SECRET"}}{{"\n"}}')"; then
    fail "기존 Auth Secret 상태를 조회하지 못했습니다."
  fi
  IFS='|' read -r \
    auth_owner auth_type key_count auth_jwt_encoded auth_identifier_encoded \
    <<<"${auth_state}"
  [[ "${auth_owner}" == "${APPLICATION_OWNER}" \
      && "${auth_type}" == "Opaque" \
      && "${key_count}" == "2" \
      && -n "${auth_jwt_encoded}" \
      && -n "${auth_identifier_encoded}" ]] \
    || fail "Auth Secret의 owner, type 또는 key schema가 목표 상태와 다릅니다."
  [[ "${expected_encoded_value}" != "${auth_jwt_encoded}" \
      && "${expected_encoded_value}" != "${auth_identifier_encoded}" ]] \
    || fail "Community cursor HMAC secret은 인증 HMAC secret과 달라야 합니다."
}

deployment_rollout_annotation() {
  kubectl_admin get "deployment/${APPLICATION_DEPLOYMENT}" \
    -o 'jsonpath={.spec.template.metadata.annotations.car-community\.io/community-secret-resource-version}'
}

rollout_current_secret() {
  local resource_version="$1"
  local patch

  patch="{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"${ROLLOUT_ANNOTATION}\":\"${resource_version}\"}}}}}"
  kubectl_admin patch "deployment/${APPLICATION_DEPLOYMENT}" --type=merge -p "${patch}" >/dev/null
  wait_for_runtime_ready
}

wait_for_runtime_ready() {
  kubectl_admin_long rollout status "deployment/${APPLICATION_DEPLOYMENT}" --timeout=300s
  kubectl_admin_long wait \
    --for=condition=Ready \
    pod \
    -l 'app.kubernetes.io/name=car-community,app.kubernetes.io/component=api' \
    --timeout=300s
}

reconcile_community_secret() {
  local current_annotation
  local current_encoded_value
  local expected_encoded_value
  local resource_version
  local secret_state

  expected_encoded_value="$(application_secret_base64_no_wrap "${COMMUNITY_CURSOR_HMAC_SECRET}")"
  secret_state="$(application_secret_community_state "${APPLICATION_NAMESPACE}")"
  IFS='|' read -r _ _ _ current_encoded_value resource_version <<<"${secret_state}"

  if [[ "${current_encoded_value}" != "${expected_encoded_value}" ]]; then
    application_secret_apply_community \
      "${SECRET_FIELD_MANAGER}" \
      "${APPLICATION_NAMESPACE}" \
      "${APPLICATION_OWNER}" \
      "${COMMUNITY_CURSOR_HMAC_SECRET}"
    application_secret_verify_community \
      "${APPLICATION_NAMESPACE}" \
      "${APPLICATION_OWNER}" \
      "${COMMUNITY_CURSOR_HMAC_SECRET}"
    secret_state="$(application_secret_community_state "${APPLICATION_NAMESPACE}")"
    IFS='|' read -r _ _ _ current_encoded_value resource_version <<<"${secret_state}"
  fi

  [[ -n "${resource_version}" ]] || fail "Community Secret resourceVersion이 비어 있습니다."
  if ! current_annotation="$(deployment_rollout_annotation)"; then
    fail "Deployment rollout annotation을 조회하지 못했습니다."
  fi
  if [[ "${current_annotation}" == "${resource_version}" ]]; then
    wait_for_runtime_ready
    echo "[완료] Community Secret과 runtime Pod가 이미 목표 상태입니다."
    return
  fi

  rollout_current_secret "${resource_version}"
  if [[ "$(deployment_rollout_annotation)" != "${resource_version}" ]]; then
    fail "Deployment가 current Community Secret revision을 사용하지 않습니다."
  fi
  echo "[완료] Community Secret을 적용하고 새 runtime Pod Ready를 확인했습니다."
}

main() (
  trap 'COMMUNITY_CURSOR_HMAC_SECRET=""' EXIT
  case "$#" in
    0) ;;
    1)
      if [[ "$1" == "--help" || "$1" == "-h" ]]; then
        print_usage
        return
      fi
      print_usage >&2
      fail "허용되지 않은 CLI argument입니다."
      ;;
    *)
      print_usage >&2
      fail "허용되지 않은 CLI argument입니다."
      ;;
  esac

  echo "[입력] Community credential을 확인합니다."
  read_community_input
  validate_community_input
  [[ "${EUID}" -eq 0 ]] || fail "sudo로 실행해야 합니다."
  require_commands
  [[ -r "${ADMIN_KUBECONFIG}" ]] || fail "Kubernetes admin kubeconfig를 읽을 수 없습니다."
  exec 9>"${LOCK_PATH}"
  flock --nonblock 9 || fail "다른 Community Secret operation이 실행 중입니다."
  require_runtime_and_secret_boundary
  require_distinct_from_auth_hmac_secrets
  reconcile_community_secret
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
