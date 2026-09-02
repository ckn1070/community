#!/usr/bin/env bash

# Application Secret의 YAML 표현과 API postcondition을 두 생애주기 entrypoint가 공유합니다.
# 이 파일은 source할 때 shell option이나 외부 상태를 변경하지 않습니다.

application_secret_base64_no_wrap() {
  printf '%s' "$1" | base64 | tr -d '\n'
}

application_secret_render_community() {
  local namespace="$1"
  local owner_label="$2"
  local cursor_hmac_secret="$3"
  local encoded_value

  encoded_value="$(application_secret_base64_no_wrap "${cursor_hmac_secret}")"
  cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: car-community-community
  namespace: ${namespace}
  labels:
    app.kubernetes.io/managed-by: ${owner_label}
type: Opaque
data:
  COMMUNITY_CURSOR_HMAC_SECRET: ${encoded_value}
EOF
}

application_secret_apply_community() {
  local field_manager="$1"
  local namespace="$2"
  local owner_label="$3"
  local cursor_hmac_secret="$4"

  if ! application_secret_render_community \
      "${namespace}" \
      "${owner_label}" \
      "${cursor_hmac_secret}" \
    | kubectl_admin apply \
        --server-side \
        "--field-manager=${field_manager}" \
        -f - \
        >/dev/null 2>/dev/null; then
    fail "Community Secret을 적용하지 못했습니다."
  fi
}

application_secret_community_state() {
  local namespace="$1"

  kubectl_admin get -n "${namespace}" secret/car-community-community \
    --ignore-not-found \
    -o 'go-template={{index .metadata.labels "app.kubernetes.io/managed-by"}}|{{.type}}|{{len .data}}|{{index .data "COMMUNITY_CURSOR_HMAC_SECRET"}}|{{.metadata.resourceVersion}}{{"\n"}}'
}

application_secret_verify_community() {
  local namespace="$1"
  local owner_label="$2"
  local cursor_hmac_secret="$3"
  local expected_encoded_value
  local state

  expected_encoded_value="$(application_secret_base64_no_wrap "${cursor_hmac_secret}")"
  if ! state="$(application_secret_community_state "${namespace}")"; then
    fail "Community Secret 상태를 조회하지 못했습니다."
  fi
  case "${state}" in
    "${owner_label}|Opaque|1|${expected_encoded_value}|"*) ;;
    *) fail "Community Secret의 owner, type, key 또는 값이 목표 상태와 다릅니다." ;;
  esac
}

application_secret_render_notification() {
  local namespace="$1"
  local owner_label="$2"
  local resend_api_key="$3"
  local encoded_value

  encoded_value="$(application_secret_base64_no_wrap "${resend_api_key}")"
  cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: car-community-notification
  namespace: ${namespace}
  labels:
    app.kubernetes.io/managed-by: ${owner_label}
type: Opaque
data:
  RESEND_API_KEY: ${encoded_value}
EOF
}

application_secret_apply_notification() {
  local field_manager="$1"
  local namespace="$2"
  local owner_label="$3"
  local resend_api_key="$4"

  if ! application_secret_render_notification \
      "${namespace}" \
      "${owner_label}" \
      "${resend_api_key}" \
    | kubectl_admin apply \
        --server-side \
        "--field-manager=${field_manager}" \
        -f - \
        >/dev/null 2>/dev/null; then
    fail "Notification Secret을 적용하지 못했습니다."
  fi
}

application_secret_notification_state() {
  local namespace="$1"

  kubectl_admin get -n "${namespace}" secret/car-community-notification \
    -o 'go-template={{index .metadata.labels "app.kubernetes.io/managed-by"}}|{{.type}}|{{len .data}}|{{index .data "RESEND_API_KEY"}}|{{.metadata.resourceVersion}}{{"\n"}}'
}

application_secret_verify_notification() {
  local namespace="$1"
  local owner_label="$2"
  local resend_api_key="$3"
  local expected_encoded_value
  local state

  expected_encoded_value="$(application_secret_base64_no_wrap "${resend_api_key}")"
  if ! state="$(application_secret_notification_state "${namespace}")"; then
    fail "Notification Secret 상태를 조회하지 못했습니다."
  fi
  case "${state}" in
    "${owner_label}|Opaque|1|${expected_encoded_value}|"*) ;;
    *) fail "Notification Secret의 owner, type, key 또는 값이 목표 상태와 다릅니다." ;;
  esac
}
