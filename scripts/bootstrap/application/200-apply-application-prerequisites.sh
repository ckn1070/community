#!/usr/bin/env bash

# Git에 저장하지 않는 staging application 입력을 Kubernetes resource로 구성합니다.
set -euo pipefail
set +x

APPLICATION_SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly APPLICATION_SCRIPT_DIRECTORY
# shellcheck source=../../lib/application-secret.sh
source "${APPLICATION_SCRIPT_DIRECTORY}/../../lib/application-secret.sh"

readonly ADMIN_KUBECONFIG="/etc/kubernetes/admin.conf"
readonly APPLICATION_NAMESPACE="car-community"
readonly RUNTIME_SECRET="car-community-db-runtime"
readonly MIGRATOR_SECRET="car-community-db-migrator"
readonly AUTH_SECRET="car-community-auth"
readonly COMMUNITY_SECRET="car-community-community"
readonly NOTIFICATION_SECRET="car-community-notification"
readonly FIELD_MANAGER="car-community-application-bootstrap"
readonly SECRET_FIELD_MANAGER="car-community-application-secrets"
readonly RUNTIME_USERNAME="carmunity_runtime"
readonly MIGRATOR_USERNAME="carmunity_migrator"
readonly LOCK_PATH="/run/lock/car-community-application-prerequisites.lock"

RUNTIME_PASSWORD=""
MIGRATOR_PASSWORD=""
AUTH_JWT_HMAC_SECRET=""
AUTH_LOGIN_IDENTIFIER_HMAC_SECRET=""
COMMUNITY_CURSOR_HMAC_SECRET=""
RESEND_API_KEY=""

print_usage() {
  cat <<'EOF'
사용법:
  printf '%s\0' \
    "${STG_DB_RUNTIME_PASSWORD}" \
    "${STG_DB_MIGRATOR_PASSWORD}" \
    "${STG_AUTH_JWT_HMAC_SECRET}" \
    "${STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET}" \
    "${STG_COMMUNITY_CURSOR_HMAC_SECRET}" \
    "${STG_RESEND_API_KEY}" \
  | env -i HOME="${HOME}" PATH="${PATH}" ssh ubuntu@<EC2> \
    'sudo -n /home/ubuntu/car-community-scripts/bootstrap/application/200-apply-application-prerequisites.sh'

정확히 여섯 개의 NUL-delimited Secret 값만 stdin으로 받습니다. DB username은
carmunity_runtime과 carmunity_migrator로 고정합니다. .env 파일 자체와 실제 값은
argument·log·temporary file에 기록하지 않습니다.
EOF
}

fail() {
  echo "오류: $*" >&2
  exit 1
}

read_nul_field() {
  local variable_name="$1"
  local field_name="$2"

  if ! IFS= read -r -d '' "${variable_name}"; then
    fail "stdin field가 누락되었거나 NUL로 끝나지 않습니다: ${field_name}"
  fi
}

read_fixed_input() {
  local trailing_input=""

  export -n \
    RUNTIME_PASSWORD \
    MIGRATOR_PASSWORD \
    AUTH_JWT_HMAC_SECRET \
    AUTH_LOGIN_IDENTIFIER_HMAC_SECRET \
    COMMUNITY_CURSOR_HMAC_SECRET \
    RESEND_API_KEY
  if [[ -t 0 ]]; then
    fail "Secret은 terminal이 아니라 NUL-delimited stdin으로 전달해야 합니다."
  fi

  read_nul_field RUNTIME_PASSWORD STG_DB_RUNTIME_PASSWORD
  read_nul_field MIGRATOR_PASSWORD STG_DB_MIGRATOR_PASSWORD
  read_nul_field AUTH_JWT_HMAC_SECRET STG_AUTH_JWT_HMAC_SECRET
  read_nul_field AUTH_LOGIN_IDENTIFIER_HMAC_SECRET STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET
  read_nul_field COMMUNITY_CURSOR_HMAC_SECRET STG_COMMUNITY_CURSOR_HMAC_SECRET
  read_nul_field RESEND_API_KEY STG_RESEND_API_KEY

  if IFS= read -r -d '' trailing_input || [[ -n "${trailing_input}" ]]; then
    fail "stdin에는 정확히 여섯 field만 있어야 합니다."
  fi
}

secret_byte_length() {
  LC_ALL=C printf '%s' "$1" | wc -c | tr -d '[:space:]'
}

validate_inputs() {
  if [[ -z "${RUNTIME_PASSWORD//[[:space:]]/}" \
      || -z "${MIGRATOR_PASSWORD//[[:space:]]/}" \
      || "${RUNTIME_PASSWORD}" == *$'\n'* \
      || "${RUNTIME_PASSWORD}" == *$'\r'* \
      || "${MIGRATOR_PASSWORD}" == *$'\n'* \
      || "${MIGRATOR_PASSWORD}" == *$'\r'* ]]; then
    fail "Runtime과 migrator password는 줄바꿈 없는 non-blank 값이어야 합니다."
  fi
  if [[ "${RUNTIME_PASSWORD}" == "${MIGRATOR_PASSWORD}" ]]; then
    fail "Runtime과 migrator password는 서로 달라야 합니다."
  fi
  if [[ "$(secret_byte_length "${AUTH_JWT_HMAC_SECRET}")" -lt 32 \
      || "$(secret_byte_length "${AUTH_LOGIN_IDENTIFIER_HMAC_SECRET}")" -lt 32 ]]; then
    fail "두 HMAC secret은 각각 32 bytes 이상이어야 합니다."
  fi
  if [[ "${AUTH_JWT_HMAC_SECRET}" == "${AUTH_LOGIN_IDENTIFIER_HMAC_SECRET}" ]]; then
    fail "두 HMAC secret은 서로 달라야 합니다."
  fi
  if [[ "$(secret_byte_length "${COMMUNITY_CURSOR_HMAC_SECRET}")" -lt 32 ]]; then
    fail "Community cursor HMAC secret은 32 bytes 이상이어야 합니다."
  fi
  if [[ "${COMMUNITY_CURSOR_HMAC_SECRET}" == "${AUTH_JWT_HMAC_SECRET}" \
      || "${COMMUNITY_CURSOR_HMAC_SECRET}" == "${AUTH_LOGIN_IDENTIFIER_HMAC_SECRET}" ]]; then
    fail "Community cursor HMAC secret은 인증 HMAC secret과 달라야 합니다."
  fi
  if [[ -z "${RESEND_API_KEY//[[:space:]]/}" \
      || "${RESEND_API_KEY}" == *$'\n'* \
      || "${RESEND_API_KEY}" == *$'\r'* ]]; then
    fail "Resend API key는 줄바꿈 없는 non-blank 값이어야 합니다."
  fi
}

require_commands() {
  local command_name
  local -a required_commands=(base64 cat flock kubectl tr wc)

  for command_name in "${required_commands[@]}"; do
    command -v "${command_name}" >/dev/null 2>&1 \
      || fail "필수 command를 찾을 수 없습니다: ${command_name}"
  done
}

kubectl_admin() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=30s "$@"
}

run_preflight() {
  if [[ "${EUID}" -ne 0 ]]; then
    fail "sudo로 실행해야 합니다."
  fi
  require_commands
  if [[ ! -r "${ADMIN_KUBECONFIG}" ]]; then
    fail "Kubernetes admin kubeconfig를 읽을 수 없습니다."
  fi
  if [[ "$(kubectl_admin get --raw=/readyz 2>/dev/null)" != "ok" ]]; then
    fail "Kubernetes API server가 ready 상태가 아닙니다."
  fi
  exec 9>"${LOCK_PATH}"
  flock --nonblock 9 || fail "다른 application prerequisite bootstrap이 실행 중입니다."
}

base64_no_wrap() {
  printf '%s' "$1" | base64 | tr -d '\n'
}

apply_yaml_stream() {
  if ! kubectl_admin apply \
      --server-side \
      "--field-manager=${FIELD_MANAGER}" \
      -f - \
      >/dev/null 2>/dev/null; then
    fail "Application prerequisite resource를 적용하지 못했습니다."
  fi
}

apply_resources() {
  local runtime_username_encoded
  local runtime_password_encoded
  local migrator_username_encoded
  local migrator_password_encoded
  local jwt_secret_encoded
  local identifier_secret_encoded
  local community_cursor_secret_encoded

  cat <<EOF | apply_yaml_stream
apiVersion: v1
kind: Namespace
metadata:
  name: ${APPLICATION_NAMESPACE}
  labels:
    app.kubernetes.io/managed-by: ${FIELD_MANAGER}
EOF

  runtime_username_encoded="$(base64_no_wrap "${RUNTIME_USERNAME}")"
  runtime_password_encoded="$(base64_no_wrap "${RUNTIME_PASSWORD}")"
  migrator_username_encoded="$(base64_no_wrap "${MIGRATOR_USERNAME}")"
  migrator_password_encoded="$(base64_no_wrap "${MIGRATOR_PASSWORD}")"
  jwt_secret_encoded="$(base64_no_wrap "${AUTH_JWT_HMAC_SECRET}")"
  identifier_secret_encoded="$(base64_no_wrap "${AUTH_LOGIN_IDENTIFIER_HMAC_SECRET}")"
  community_cursor_secret_encoded="$(base64_no_wrap "${COMMUNITY_CURSOR_HMAC_SECRET}")"

  cat <<EOF | apply_yaml_stream
apiVersion: v1
kind: Secret
metadata:
  name: ${RUNTIME_SECRET}
  namespace: ${APPLICATION_NAMESPACE}
  labels:
    app.kubernetes.io/managed-by: ${FIELD_MANAGER}
type: Opaque
data:
  DB_USERNAME: ${runtime_username_encoded}
  DB_PASSWORD: ${runtime_password_encoded}
---
apiVersion: v1
kind: Secret
metadata:
  name: ${MIGRATOR_SECRET}
  namespace: ${APPLICATION_NAMESPACE}
  labels:
    app.kubernetes.io/managed-by: ${FIELD_MANAGER}
type: Opaque
data:
  DB_MIGRATOR_USERNAME: ${migrator_username_encoded}
  DB_MIGRATOR_PASSWORD: ${migrator_password_encoded}
---
apiVersion: v1
kind: Secret
metadata:
  name: ${AUTH_SECRET}
  namespace: ${APPLICATION_NAMESPACE}
  labels:
    app.kubernetes.io/managed-by: ${FIELD_MANAGER}
type: Opaque
data:
  AUTH_JWT_HMAC_SECRET: ${jwt_secret_encoded}
  AUTH_LOGIN_IDENTIFIER_HMAC_SECRET: ${identifier_secret_encoded}
---
apiVersion: v1
kind: Secret
metadata:
  name: ${COMMUNITY_SECRET}
  namespace: ${APPLICATION_NAMESPACE}
  labels:
    app.kubernetes.io/managed-by: ${FIELD_MANAGER}
type: Opaque
data:
  COMMUNITY_CURSOR_HMAC_SECRET: ${community_cursor_secret_encoded}
EOF

  application_secret_apply_notification \
    "${SECRET_FIELD_MANAGER}" \
    "${APPLICATION_NAMESPACE}" \
    "${FIELD_MANAGER}" \
    "${RESEND_API_KEY}"
}

verify_resources() {
  kubectl_admin get "namespace/${APPLICATION_NAMESPACE}" >/dev/null
  kubectl_admin get -n "${APPLICATION_NAMESPACE}" \
    "secret/${RUNTIME_SECRET}" \
    "secret/${MIGRATOR_SECRET}" \
    "secret/${AUTH_SECRET}" \
    "secret/${COMMUNITY_SECRET}" \
    "secret/${NOTIFICATION_SECRET}" \
    >/dev/null
  application_secret_verify_notification \
    "${APPLICATION_NAMESPACE}" \
    "${FIELD_MANAGER}" \
    "${RESEND_API_KEY}"
}

cleanup() {
  RUNTIME_PASSWORD=""
  MIGRATOR_PASSWORD=""
  AUTH_JWT_HMAC_SECRET=""
  AUTH_LOGIN_IDENTIFIER_HMAC_SECRET=""
  COMMUNITY_CURSOR_HMAC_SECRET=""
  RESEND_API_KEY=""
}

main() (
  trap cleanup EXIT
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

  echo "[입력] 여섯 개의 application Secret 값을 확인합니다."
  read_fixed_input
  validate_inputs
  run_preflight
  echo "[적용] Namespace와 Secret을 적용합니다."
  apply_resources
  verify_resources
  echo "[완료] Application prerequisite resource 6개를 확인했습니다."
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
