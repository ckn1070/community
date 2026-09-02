#!/usr/bin/env bash

# Catalog Redis 역할별 credential을 두 consumer Secret으로 생성·수렴시킵니다.
set -euo pipefail
set +x

readonly ADMIN_KUBECONFIG="/etc/kubernetes/admin.conf"
readonly APPLICATION_NAMESPACE="car-community"
readonly APPLICATION_OWNER="car-community-application-bootstrap"
readonly SECRET_FIELD_MANAGER="car-community-catalog-redis-credentials"
readonly LOCK_PATH="/run/lock/car-community-catalog-redis-credentials.lock"

APPLICATION_PASSWORD=""
OPERATION_PASSWORD=""
PROBE_PASSWORD=""

print_usage() {
  cat <<'EOF'
사용법:
  printf '%s\0%s\0%s\0' \
    "${STG_CATALOG_REDIS_APPLICATION_PASSWORD}" \
    "${STG_CATALOG_REDIS_OPERATION_PASSWORD}" \
    "${STG_CATALOG_REDIS_PROBE_PASSWORD}" \
  | env -i HOME="${HOME}" PATH="${PATH}" ssh ubuntu@<EC2> \
    'sudo -n /home/ubuntu/car-community-scripts/operations/application/reconcile-catalog-redis-secrets.sh'

세 credential을 NUL-delimited stdin으로 받아 application Secret과 Redis server ACL Secret을
생성·수렴시킵니다. 실제 값은 argument·log·영구 file에 기록하지 않습니다.
EOF
}

fail() {
  printf '오류: %s\n' "$*" >&2
  exit 1
}

read_inputs() {
  local trailing_input=""

  export -n APPLICATION_PASSWORD OPERATION_PASSWORD PROBE_PASSWORD
  [[ ! -t 0 ]] || fail "Credential은 terminal이 아니라 NUL-delimited stdin으로 전달해야 합니다."
  IFS= read -r -d '' APPLICATION_PASSWORD \
    || fail "Application credential이 누락되었거나 NUL로 끝나지 않습니다."
  IFS= read -r -d '' OPERATION_PASSWORD \
    || fail "Operation credential이 누락되었거나 NUL로 끝나지 않습니다."
  IFS= read -r -d '' PROBE_PASSWORD \
    || fail "Probe credential이 누락되었거나 NUL로 끝나지 않습니다."
  if IFS= read -r -d '' trailing_input || [[ -n "${trailing_input}" ]]; then
    fail "stdin에는 정확히 세 field만 있어야 합니다."
  fi
}

validate_password() {
  local name="$1"
  local value="$2"
  local byte_length

  byte_length="$(LC_ALL=C printf '%s' "${value}" | wc -c | tr -d '[:space:]')"
  if [[ -z "${value//[[:space:]]/}" \
      || "${value}" == *$'\n'* \
      || "${value}" == *$'\r'* \
      || "${byte_length}" -lt 32 ]]; then
    fail "${name} credential은 줄바꿈 없는 32 bytes 이상의 값이어야 합니다."
  fi
}

validate_inputs() {
  validate_password "Application" "${APPLICATION_PASSWORD}"
  validate_password "Operation" "${OPERATION_PASSWORD}"
  validate_password "Probe" "${PROBE_PASSWORD}"
  [[ "${APPLICATION_PASSWORD}" != "${OPERATION_PASSWORD}" \
      && "${APPLICATION_PASSWORD}" != "${PROBE_PASSWORD}" \
      && "${OPERATION_PASSWORD}" != "${PROBE_PASSWORD}" ]] \
    || fail "세 Redis credential은 서로 달라야 합니다."
}

base64_no_wrap() {
  printf '%s' "$1" | base64 | tr -d '\n'
}

sha256_hex() {
  printf '%s' "$1" | sha256sum | awk '{print $1}'
}

render_application_secret() {
  local password_encoded
  local username_encoded

  username_encoded="$(base64_no_wrap application)"
  password_encoded="$(base64_no_wrap "${APPLICATION_PASSWORD}")"
  cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: car-community-catalog-redis-application
  namespace: ${APPLICATION_NAMESPACE}
  labels:
    app.kubernetes.io/managed-by: ${APPLICATION_OWNER}
type: Opaque
data:
  REDIS_USERNAME: ${username_encoded}
  REDIS_PASSWORD: ${password_encoded}
EOF
}

render_server_acl() {
  local application_hash
  local operation_hash
  local probe_hash

  application_hash="$(sha256_hex "${APPLICATION_PASSWORD}")"
  operation_hash="$(sha256_hex "${OPERATION_PASSWORD}")"
  probe_hash="$(sha256_hex "${PROBE_PASSWORD}")"
  printf '%s\n' \
    'user default off' \
    "user application on #${application_hash} ~carmunity:catalog:model-detail:v1:* +get +set +del +ping" \
    "user operation on #${operation_hash} ~carmunity:catalog:model-detail:v1:* +ttl +info|commandstats +info|stats +ping" \
    "user probe on #${probe_hash} ~* +ping"
}

render_server_secret() {
  local acl_encoded
  local probe_password_encoded
  local probe_username_encoded

  acl_encoded="$(render_server_acl | base64 | tr -d '\n')"
  probe_username_encoded="$(base64_no_wrap probe)"
  probe_password_encoded="$(base64_no_wrap "${PROBE_PASSWORD}")"
  cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: car-community-catalog-redis-server
  namespace: ${APPLICATION_NAMESPACE}
  labels:
    app.kubernetes.io/managed-by: ${APPLICATION_OWNER}
type: Opaque
data:
  users.acl: ${acl_encoded}
  REDIS_PROBE_USERNAME: ${probe_username_encoded}
  REDIS_PROBE_PASSWORD: ${probe_password_encoded}
EOF
}

kubectl_admin() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=30s \
    -n "${APPLICATION_NAMESPACE}" "$@"
}

kubectl_cluster() {
  KUBECONFIG="${ADMIN_KUBECONFIG}" kubectl --request-timeout=30s "$@"
}

require_commands() {
  local command_name
  local -a required_commands=(awk base64 flock kubectl sha256sum tr wc)

  for command_name in "${required_commands[@]}"; do
    command -v "${command_name}" >/dev/null 2>&1 \
      || fail "필수 command를 찾을 수 없습니다: ${command_name}"
  done
}

require_target_boundary() {
  local namespace_owner

  namespace_owner="$(kubectl_cluster get "namespace/${APPLICATION_NAMESPACE}" \
    -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')" \
    || fail "Application namespace를 조회하지 못했습니다."
  [[ "${namespace_owner}" == "${APPLICATION_OWNER}" ]] \
    || fail "Application bootstrap 소유 namespace가 필요합니다."
}

secret_state() {
  local secret_name="$1"
  local fields="$2"

  kubectl_admin get "secret/${secret_name}" --ignore-not-found \
    -o "go-template={{index .metadata.labels \"app.kubernetes.io/managed-by\"}}|{{.type}}|{{len .data}}|${fields}{{\"\\n\"}}"
}

require_existing_secret_boundary() {
  local secret_name="$1"
  local expected_key_count="$2"
  local fields="$3"
  local state

  state="$(secret_state "${secret_name}" "${fields}")" \
    || fail "기존 ${secret_name} Secret 상태를 조회하지 못했습니다."
  if [[ -n "${state}" \
      && ("${state}" != "${APPLICATION_OWNER}|Opaque|${expected_key_count}|"?* \
        || "${state}" == *'<no value>'* \
        || "${state}" == *'||'* \
        || "${state}" == *'|') ]]; then
    fail "다른 owner, type 또는 schema의 ${secret_name} Secret을 변경하지 않습니다."
  fi
}

apply_secret() {
  local renderer="$1"
  local secret_name="$2"

  if ! "${renderer}" \
    | kubectl_admin apply --server-side "--field-manager=${SECRET_FIELD_MANAGER}" -f - \
      >/dev/null 2>/dev/null; then
    fail "${secret_name} Secret을 적용하지 못했습니다. 같은 입력으로 이 operation을 재실행하세요."
  fi
}

verify_secrets() {
  local application_state
  local expected_acl
  local expected_application_password
  local expected_application_username
  local expected_probe_password
  local expected_probe_username
  local server_state

  expected_application_username="$(base64_no_wrap application)"
  expected_application_password="$(base64_no_wrap "${APPLICATION_PASSWORD}")"
  expected_probe_username="$(base64_no_wrap probe)"
  expected_probe_password="$(base64_no_wrap "${PROBE_PASSWORD}")"
  expected_acl="$(render_server_secret | awk '/^  users\.acl: / {print $2}')"
  application_state="$(secret_state car-community-catalog-redis-application \
    '{{index .data "REDIS_USERNAME"}}|{{index .data "REDIS_PASSWORD"}}')"
  server_state="$(secret_state car-community-catalog-redis-server \
    '{{index .data "users.acl"}}|{{index .data "REDIS_PROBE_USERNAME"}}|{{index .data "REDIS_PROBE_PASSWORD"}}')"

  [[ "${application_state}" == \
      "${APPLICATION_OWNER}|Opaque|2|${expected_application_username}|${expected_application_password}" ]] \
    || fail "Application Secret의 owner, type, key schema 또는 값이 목표 상태와 다릅니다."
  [[ "${server_state}" == \
      "${APPLICATION_OWNER}|Opaque|3|${expected_acl}|${expected_probe_username}|${expected_probe_password}" ]] \
    || fail "Redis server Secret의 owner, type, key schema 또는 값이 목표 상태와 다릅니다."
}

reconcile_secrets() {
  require_existing_secret_boundary \
    car-community-catalog-redis-application \
    2 \
    '{{index .data "REDIS_USERNAME"}}|{{index .data "REDIS_PASSWORD"}}'
  require_existing_secret_boundary \
    car-community-catalog-redis-server \
    3 \
    '{{index .data "users.acl"}}|{{index .data "REDIS_PROBE_USERNAME"}}|{{index .data "REDIS_PROBE_PASSWORD"}}'
  apply_secret render_application_secret car-community-catalog-redis-application
  apply_secret render_server_secret car-community-catalog-redis-server
  verify_secrets
  echo "[완료] Catalog Redis application/server Secret이 역할별 목표 상태로 수렴했습니다."
}

main() (
  trap 'APPLICATION_PASSWORD=""; OPERATION_PASSWORD=""; PROBE_PASSWORD=""' EXIT
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

  echo "[입력] Catalog Redis credential을 확인합니다."
  read_inputs
  validate_inputs
  [[ "${EUID}" -eq 0 ]] || fail "sudo로 실행해야 합니다."
  require_commands
  [[ -r "${ADMIN_KUBECONFIG}" ]] || fail "Kubernetes admin kubeconfig를 읽을 수 없습니다."
  exec 9>"${LOCK_PATH}"
  flock --nonblock 9 || fail "다른 Catalog Redis credential operation이 실행 중입니다."
  require_target_boundary
  reconcile_secrets
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
