#!/usr/bin/env bash

# 구축 완료 뒤 notification provider credential을 적용하고 새 runtime Pod에 반영합니다.
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
readonly SECRET_FIELD_MANAGER="car-community-application-secrets"
readonly ROLLOUT_ANNOTATION="car-community.io/notification-secret-resource-version"
readonly LOCK_PATH="/run/lock/car-community-notification-secret.lock"

RESEND_API_KEY=""

print_usage() {
  cat <<'EOF'
사용법:
  printf '%s\0' "${STG_RESEND_API_KEY}" \
  | env -i HOME="${HOME}" PATH="${PATH}" ssh ubuntu@<EC2> \
    'sudo -n /home/ubuntu/car-community-scripts/operations/application/reconcile-notification-secret.sh'

기존 car-community-notification Secret의 current approved schema만 수렴시킵니다. 최초 resource를
생성하지 않으며 실제 값은 argument·log·영구 file에 기록하지 않습니다.
EOF
}

fail() {
  echo "오류: $*" >&2
  exit 1
}

read_notification_input() {
  local trailing_input=""

  export -n RESEND_API_KEY
  if [[ -t 0 ]]; then
    fail "Secret은 terminal이 아니라 NUL-delimited stdin으로 전달해야 합니다."
  fi
  if ! IFS= read -r -d '' RESEND_API_KEY; then
    fail "STG_RESEND_API_KEY가 누락되었거나 NUL로 끝나지 않습니다."
  fi
  if IFS= read -r -d '' trailing_input || [[ -n "${trailing_input}" ]]; then
    fail "stdin에는 정확히 한 field만 있어야 합니다."
  fi
}

validate_notification_input() {
  if [[ -z "${RESEND_API_KEY//[[:space:]]/}" \
      || "${RESEND_API_KEY}" == *$'\n'* \
      || "${RESEND_API_KEY}" == *$'\r'* ]]; then
    fail "Resend API key는 줄바꿈 없는 non-blank 값이어야 합니다."
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
  local -a required_commands=(base64 flock kubectl tr)

  for command_name in "${required_commands[@]}"; do
    command -v "${command_name}" >/dev/null 2>&1 \
      || fail "필수 command를 찾을 수 없습니다: ${command_name}"
  done
}

require_existing_runtime() {
  local deployment_owner
  local namespace_owner
  local secret_state

  if ! namespace_owner="$(kubectl_cluster get "namespace/${APPLICATION_NAMESPACE}" \
      -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')"; then
    fail "Application namespace를 조회하지 못했습니다."
  fi
  [[ "${namespace_owner}" == "${APPLICATION_OWNER}" ]] \
    || fail "Application bootstrap 소유 namespace가 필요합니다."

  if ! secret_state="$(application_secret_notification_state "${APPLICATION_NAMESPACE}")"; then
    fail "기존 Notification Secret이 필요합니다. 최초 bootstrap을 다시 실행하지 마세요."
  fi
  [[ "${secret_state}" == "${APPLICATION_OWNER}|Opaque|1|"* ]] \
    || fail "다른 owner 또는 schema의 Notification Secret을 변경하지 않습니다."

  if ! deployment_owner="$(kubectl_admin get "deployment/${APPLICATION_DEPLOYMENT}" \
      -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')"; then
    fail "기존 runtime Deployment가 필요합니다."
  fi
  [[ "${deployment_owner}" == "${ARGOCD_OWNER}" ]] \
    || fail "Argo CD 소유 runtime Deployment만 rollout합니다."
}

apply_notification_secret() {
  application_secret_apply_notification \
    "${SECRET_FIELD_MANAGER}" \
    "${APPLICATION_NAMESPACE}" \
    "${APPLICATION_OWNER}" \
    "${RESEND_API_KEY}"
}

deployment_rollout_annotation() {
  kubectl_admin get "deployment/${APPLICATION_DEPLOYMENT}" \
    -o 'jsonpath={.spec.template.metadata.annotations.car-community\.io/notification-secret-resource-version}'
}

rollout_current_secret() {
  local resource_version="$1"
  local patch

  patch="{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"${ROLLOUT_ANNOTATION}\":\"${resource_version}\"}}}}}"
  kubectl_admin patch "deployment/${APPLICATION_DEPLOYMENT}" --type=merge -p "${patch}" >/dev/null
  kubectl_admin_long rollout status "deployment/${APPLICATION_DEPLOYMENT}" --timeout=300s
  kubectl_admin_long wait \
    --for=condition=Ready \
    pod \
    -l 'app.kubernetes.io/name=car-community,app.kubernetes.io/component=api' \
    --timeout=300s
}

reconcile_notification_secret() {
  local current_annotation
  local current_encoded_value
  local expected_encoded_value
  local resource_version
  local secret_state

  expected_encoded_value="$(application_secret_base64_no_wrap "${RESEND_API_KEY}")"
  if ! secret_state="$(application_secret_notification_state "${APPLICATION_NAMESPACE}")"; then
    fail "Notification Secret 상태를 조회하지 못했습니다."
  fi
  IFS='|' read -r _ _ _ current_encoded_value resource_version <<<"${secret_state}"

  if [[ "${current_encoded_value}" != "${expected_encoded_value}" ]]; then
    apply_notification_secret
    application_secret_verify_notification \
      "${APPLICATION_NAMESPACE}" \
      "${APPLICATION_OWNER}" \
      "${RESEND_API_KEY}"
    secret_state="$(application_secret_notification_state "${APPLICATION_NAMESPACE}")"
    IFS='|' read -r _ _ _ current_encoded_value resource_version <<<"${secret_state}"
  fi

  [[ -n "${resource_version}" ]] || fail "Notification Secret resourceVersion이 비어 있습니다."
  if ! current_annotation="$(deployment_rollout_annotation)"; then
    fail "Deployment rollout annotation을 조회하지 못했습니다."
  fi
  if [[ "${current_annotation}" == "${resource_version}" ]]; then
    echo "[완료] Notification Secret과 runtime Pod가 이미 목표 상태입니다."
    return
  fi

  rollout_current_secret "${resource_version}"
  if [[ "$(deployment_rollout_annotation)" != "${resource_version}" ]]; then
    fail "Deployment가 current Notification Secret revision을 사용하지 않습니다."
  fi
  echo "[완료] Notification Secret을 적용하고 새 runtime Pod Ready를 확인했습니다."
}

main() (
  trap 'RESEND_API_KEY=""' EXIT
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

  echo "[입력] Notification credential을 확인합니다."
  read_notification_input
  validate_notification_input
  [[ "${EUID}" -eq 0 ]] || fail "sudo로 실행해야 합니다."
  require_commands
  [[ -r "${ADMIN_KUBECONFIG}" ]] || fail "Kubernetes admin kubeconfig를 읽을 수 없습니다."
  exec 9>"${LOCK_PATH}"
  flock --nonblock 9 || fail "다른 notification Secret operation이 실행 중입니다."
  require_existing_runtime
  reconcile_notification_secret
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
