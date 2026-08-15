#!/usr/bin/env bash

set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly SCRIPT_PATH="${TEST_DIRECTORY}/../400-initialize-cluster.sh"
TEST_ROOT=""

# shellcheck source=../400-initialize-cluster.sh
source "${SCRIPT_PATH}"

assert_equals() {
  local expected="$1"
  local actual="$2"
  local message="$3"

  if [[ "${expected}" != "${actual}" ]]; then
    echo "FAIL: ${message}" >&2
    echo "expected: ${expected}" >&2
    echo "actual:   ${actual}" >&2
    exit 1
  fi
}

assert_file_contains() {
  local file_path="$1"
  local expected_line="$2"
  local message="$3"

  if ! grep -Fqx -- "${expected_line}" "${file_path}"; then
    echo "FAIL: ${message}" >&2
    exit 1
  fi
}

assert_file_excludes() {
  local file_path="$1"
  local unexpected_text="$2"
  local message="$3"

  if grep -Fq -- "${unexpected_text}" "${file_path}"; then
    echo "FAIL: ${message}" >&2
    exit 1
  fi
}

test_renders_frozen_kubeadm_contract() {
  local test_root="$1"
  local init_configuration_path="${test_root}/kubeadm-init.yaml"
  local cluster_configuration_path="${test_root}/cluster-configuration.yaml"

  render_kubeadm_configuration \
    "${init_configuration_path}" \
    "${cluster_configuration_path}" \
    "10.0.128.25"

  assert_file_contains \
    "${init_configuration_path}" \
    "apiVersion: kubeadm.k8s.io/v1beta4" \
    "kubeadm v1beta4 API를 명시해야 합니다."
  assert_file_contains \
    "${init_configuration_path}" \
    "  advertiseAddress: 10.0.128.25" \
    "현재 node의 private IPv4를 API advertise address로 명시해야 합니다."
  assert_file_contains \
    "${init_configuration_path}" \
    "  criSocket: unix:///run/containerd/containerd.sock" \
    "고정 containerd CRI socket을 명시해야 합니다."
  assert_file_contains \
    "${cluster_configuration_path}" \
    "clusterName: stg-car-community-k8s" \
    "확정한 cluster 이름을 사용해야 합니다."
  assert_file_contains \
    "${cluster_configuration_path}" \
    "kubernetesVersion: v1.36.2" \
    "Kubernetes exact version을 사용해야 합니다."
  assert_file_contains \
    "${cluster_configuration_path}" \
    "  podSubnet: 192.168.0.0/16" \
    "Calico와 공유하는 Pod CIDR을 사용해야 합니다."
  assert_file_contains \
    "${cluster_configuration_path}" \
    "  serviceSubnet: 10.96.0.0/12" \
    "확정한 Service CIDR을 사용해야 합니다."
  assert_file_contains \
    "${cluster_configuration_path}" \
    "  dnsDomain: cluster.local" \
    "확정한 cluster DNS domain을 사용해야 합니다."
  assert_file_excludes \
    "${init_configuration_path}" \
    "controlPlaneEndpoint" \
    "단일 node 계약에 없는 stable controlPlaneEndpoint를 만들면 안 됩니다."
  assert_file_excludes \
    "${init_configuration_path}" \
    "taints:" \
    "control-plane taint 제거를 init 단계로 당기면 안 됩니다."
  assert_file_excludes \
    "${init_configuration_path}" \
    "certificateKey" \
    "인증서 암호화 key를 설정이나 로그에 넣으면 안 됩니다."
}

test_typed_host_route_collision_is_rejected() (
  local execution_status

  ip() {
    case "$*" in
      "-4 route show table main root 192.168.0.0/16")
        printf '%s\n' "blackhole 192.168.0.0/16 proto static"
        ;;
      *" match "*)
        printf '%s\n' "default via 10.0.128.1 dev ens5"
        ;;
    esac
  }

  set +e
  (require_pristine_host_routes_do_not_overlap_cluster_networks >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: typed blackhole route와 Pod CIDR 충돌을 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_covering_host_route_collision_is_rejected() (
  local execution_status

  ip() {
    case "$*" in
      "-4 route show table main match 192.168.0.0/16")
        printf '%s\n' "192.0.0.0/8 via 10.0.128.1 dev ens5"
        ;;
      *" match "*)
        printf '%s\n' "default via 10.0.128.1 dev ens5"
        ;;
    esac
  }

  set +e
  (require_pristine_host_routes_do_not_overlap_cluster_networks >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: Pod CIDR을 포함하는 상위 host route를 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_default_route_is_not_a_cluster_cidr_collision() (
  ip() {
    if [[ "$*" == *" match "* ]]; then
      printf '%s\n' "default via 10.0.128.1 dev ens5"
    fi
  }

  require_pristine_host_routes_do_not_overlap_cluster_networks
)

test_route_inventory_failure_is_rejected() (
  local execution_status

  ip() {
    return 75
  }

  set +e
  (require_pristine_host_routes_do_not_overlap_cluster_networks >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: host route 조회 실패를 충돌 없음으로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_primary_private_ipv4_must_belong_to_staging_vpc() (
  local execution_status

  ip() {
    printf '%s\n' "1.1.1.1 via 172.16.0.1 dev ens5 src 172.16.0.25 uid 0"
  }

  set +e
  (detect_primary_private_ipv4 >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: staging VPC 밖의 route source IP를 advertise address로 사용하면 안 됩니다." >&2
    exit 1
  fi
)

test_primary_private_ipv4_is_detected_from_route_source() (
  ip() {
    printf '%s\n' "1.1.1.1 via 10.0.0.1 dev ens5 src 10.0.8.159 uid 0"
  }

  assert_equals \
    "10.0.8.159" \
    "$(detect_primary_private_ipv4)" \
    "정상 route의 source IP를 API advertise address로 추출해야 합니다."
)

test_cluster_configuration_canonicalization_excludes_generated_token_but_preserves_drift() (
  local test_root="${TEST_ROOT}/canonicalization"
  local first_source_path="${test_root}/first-source.yaml"
  local second_source_path="${test_root}/second-source.yaml"
  local first_canonical_path="${test_root}/first-canonical.yaml"
  local second_canonical_path="${test_root}/second-canonical.yaml"
  local migration_count=0

  mkdir -p "${test_root}"
  printf '%s\n' \
    "kind: ClusterConfiguration" \
    "clusterName: stg-car-community-k8s" \
    >"${first_source_path}"
  cp -- "${first_source_path}" "${second_source_path}"

  kubeadm() {
    local source_path=""
    local destination_path=""
    local cluster_name

    while [[ "$#" -gt 0 ]]; do
      case "$1" in
        --old-config)
          source_path="$2"
          shift 2
          ;;
        --new-config)
          destination_path="$2"
          shift 2
          ;;
        *)
          shift
          ;;
      esac
    done

    cluster_name="$(awk '$1 == "clusterName:" { print $2; exit }' "${source_path}")"
    migration_count=$((migration_count + 1))
    cat >"${destination_path}" <<EOF
apiVersion: kubeadm.k8s.io/v1beta4
kind: InitConfiguration
bootstrapTokens:
- token: generated-${migration_count}
---
apiVersion: kubeadm.k8s.io/v1beta4
kind: ClusterConfiguration
clusterName: ${cluster_name}
EOF
  }

  canonicalize_cluster_configuration "${first_source_path}" "${first_canonical_path}"
  canonicalize_cluster_configuration "${second_source_path}" "${second_canonical_path}"

  if ! cmp --silent "${first_canonical_path}" "${second_canonical_path}"; then
    echo "FAIL: migrate가 매번 생성한 bootstrap token이 ClusterConfiguration 비교에 들어가면 안 됩니다." >&2
    exit 1
  fi
  assert_file_contains \
    "${first_canonical_path}" \
    "kind: ClusterConfiguration" \
    "정규화 결과에는 ClusterConfiguration이 있어야 합니다."
  assert_file_excludes \
    "${first_canonical_path}" \
    "kind: InitConfiguration" \
    "정규화 결과에서 비교 대상이 아닌 InitConfiguration을 제외해야 합니다."
  assert_file_excludes \
    "${first_canonical_path}" \
    "bootstrapTokens:" \
    "임의 bootstrap token은 ClusterConfiguration 비교 대상이 아닙니다."

  printf '%s\n' \
    "kind: ClusterConfiguration" \
    "clusterName: other-cluster" \
    >"${second_source_path}"
  canonicalize_cluster_configuration "${second_source_path}" "${second_canonical_path}"

  if cmp --silent "${first_canonical_path}" "${second_canonical_path}"; then
    echo "FAIL: ClusterConfiguration field drift는 strict 비교 결과에 남아야 합니다." >&2
    exit 1
  fi
  assert_file_contains \
    "${second_canonical_path}" \
    "clusterName: other-cluster" \
    "정규화가 ClusterConfiguration의 실제 field drift를 보존해야 합니다."
)

test_cluster_configuration_canonicalization_rejects_missing_document() (
  local test_root="${TEST_ROOT}/missing-cluster-configuration"
  local source_path="${test_root}/source.yaml"
  local canonical_path="${test_root}/canonical.yaml"
  local execution_status

  mkdir -p "${test_root}"
  printf '%s\n' "kind: ClusterConfiguration" >"${source_path}"

  kubeadm() {
    local destination_path=""

    while [[ "$#" -gt 0 ]]; do
      case "$1" in
        --new-config)
          destination_path="$2"
          shift 2
          ;;
        *)
          shift
          ;;
      esac
    done

    cat >"${destination_path}" <<'EOF'
apiVersion: kubeadm.k8s.io/v1beta4
kind: InitConfiguration
EOF
  }

  set +e
  (canonicalize_cluster_configuration "${source_path}" "${canonical_path}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: migrate 결과에 ClusterConfiguration이 없으면 비교를 계속하면 안 됩니다." >&2
    exit 1
  fi
)

test_kubernetes_binary_version_drift_is_rejected() (
  local execution_status

  kubeadm() {
    printf '%s\n' "v1.36.2"
  }
  kubelet() {
    printf '%s\n' "Kubernetes v1.35.0"
  }

  set +e
  (require_exact_kubernetes_versions >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 목표와 다른 kubelet version으로 cluster를 초기화하면 안 됩니다." >&2
    exit 1
  fi
)

test_cluster_state_classification() (
  local test_root="$1"
  local admin_path="${test_root}/state/admin.conf"
  local partial_marker="${test_root}/state/etcd/member"

  partial_cluster_state_marker_paths() {
    printf '%s\n' "${partial_marker}"
  }

  assert_equals \
    "pristine" \
    "$(classify_cluster_state "${admin_path}")" \
    "kubeadm 흔적이 없으면 최초 init 가능한 상태여야 합니다."

  mkdir -p "${partial_marker%/*}"
  : >"${partial_marker}"
  assert_equals \
    "partial" \
    "$(classify_cluster_state "${admin_path}")" \
    "admin kubeconfig 없이 kubeadm 흔적이 있으면 부분 초기화 상태여야 합니다."

  rm -- "${partial_marker}"
  ln -s "${test_root}/missing-target" "${admin_path}"
  assert_equals \
    "partial" \
    "$(classify_cluster_state "${admin_path}")" \
    "깨진 admin kubeconfig symlink도 부분 상태로 차단해야 합니다."

  rm -- "${admin_path}"
  printf '%s\n' "kubeconfig" >"${admin_path}"
  assert_equals \
    "complete" \
    "$(classify_cluster_state "${admin_path}")" \
    "admin kubeconfig가 있으면 API postcondition을 검증할 완료 후보여야 합니다."
)

test_initialize_cluster_hides_bootstrap_token() (
  kubeadm() {
    printf '%s\n' "$*"
  }

  assert_equals \
    "init --config /tmp/kubeadm-init.yaml --skip-token-print" \
    "$(initialize_cluster /tmp/kubeadm-init.yaml)" \
    "kubeadm init은 bootstrap token을 terminal에 출력하지 않아야 합니다."
)

test_main_initializes_pristine_cluster_once() (
  local test_root="$1"
  local action_log="${test_root}/pristine-action-log"
  local test_work_directory="${test_root}/pristine-work"

  run_preflight_checks() {
    PRIMARY_PRIVATE_IPV4="10.0.128.25"
    CLUSTER_STATE="pristine"
  }
  create_work_directory() {
    mkdir -p "${test_work_directory}"
    printf '%s\n' "${test_work_directory}"
  }
  render_kubeadm_configuration() { : >"$1"; : >"$2"; }
  validate_kubeadm_configuration() { printf '%s\n' "validate" >>"${action_log}"; }
  pull_control_plane_images() { printf '%s\n' "pull" >>"${action_log}"; }
  initialize_cluster() { printf '%s\n' "init" >>"${action_log}"; }
  verify_cluster_postconditions() { printf '%s\n' "verify" >>"${action_log}"; }

  main >/dev/null

  assert_equals \
    $'validate\npull\ninit\nverify' \
    "$(<"${action_log}")" \
    "최초 상태는 config·image를 확인한 뒤 init하고 결과를 검증해야 합니다."
)

test_main_reuses_complete_cluster() (
  local test_root="$1"
  local action_log="${test_root}/complete-action-log"
  local test_work_directory="${test_root}/complete-work"

  run_preflight_checks() {
    PRIMARY_PRIVATE_IPV4="10.0.128.25"
    CLUSTER_STATE="complete"
  }
  create_work_directory() {
    mkdir -p "${test_work_directory}"
    printf '%s\n' "${test_work_directory}"
  }
  render_kubeadm_configuration() { : >"$1"; : >"$2"; }
  validate_kubeadm_configuration() { printf '%s\n' "validate" >>"${action_log}"; }
  pull_control_plane_images() {
    echo "FAIL: 완료 cluster 재실행에서 image pull을 강제하면 안 됩니다." >&2
    return 1
  }
  initialize_cluster() {
    echo "FAIL: 완료 cluster에 kubeadm init을 반복하면 안 됩니다." >&2
    return 1
  }
  verify_cluster_postconditions() { printf '%s\n' "verify" >>"${action_log}"; }

  main >/dev/null

  assert_equals \
    $'validate\nverify' \
    "$(<"${action_log}")" \
    "완료 후보의 재실행은 init 없이 desired config와 postcondition만 검증해야 합니다."
)

test_main_rejects_partial_cluster_before_mutation() (
  local execution_status

  run_preflight_checks() {
    PRIMARY_PRIVATE_IPV4="10.0.128.25"
    CLUSTER_STATE="partial"
  }
  create_work_directory() {
    echo "FAIL: 부분 cluster에서 임시 init 설정도 만들면 안 됩니다." >&2
    return 1
  }
  kubeadm() {
    echo "FAIL: 부분 cluster에서 kubeadm command를 호출하면 안 됩니다." >&2
    return 1
  }

  set +e
  (main >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 부분 초기화 상태를 자동 복구하거나 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_validation_failure_stops_before_cluster_mutation() (
  local test_root="$1"
  local test_work_directory="${test_root}/validation-failure-work"
  local execution_status

  run_preflight_checks() {
    PRIMARY_PRIVATE_IPV4="10.0.128.25"
    CLUSTER_STATE="pristine"
  }
  create_work_directory() {
    mkdir -p "${test_work_directory}"
    printf '%s\n' "${test_work_directory}"
  }
  render_kubeadm_configuration() { : >"$1"; : >"$2"; }
  validate_kubeadm_configuration() { return 1; }
  pull_control_plane_images() {
    echo "FAIL: config validation 실패 뒤 image pull을 실행하면 안 됩니다." >&2
    return 1
  }
  initialize_cluster() {
    echo "FAIL: config validation 실패 뒤 init을 실행하면 안 됩니다." >&2
    return 1
  }

  set +e
  (main >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: kubeadm config validation 실패를 전파해야 합니다." >&2
    exit 1
  fi
)

test_image_pull_failure_stops_before_cluster_initialization() (
  local test_root="$1"
  local test_work_directory="${test_root}/image-pull-failure-work"
  local execution_status

  run_preflight_checks() {
    PRIMARY_PRIVATE_IPV4="10.0.128.25"
    CLUSTER_STATE="pristine"
  }
  create_work_directory() {
    mkdir -p "${test_work_directory}"
    printf '%s\n' "${test_work_directory}"
  }
  render_kubeadm_configuration() { : >"$1"; : >"$2"; }
  validate_kubeadm_configuration() { :; }
  pull_control_plane_images() { return 1; }
  initialize_cluster() {
    echo "FAIL: image pull 실패 뒤 kubeadm init을 실행하면 안 됩니다." >&2
    return 1
  }

  set +e
  (main >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: control-plane image pull 실패를 전파해야 합니다." >&2
    exit 1
  fi
)

run_tests() {
  TEST_ROOT="$(mktemp -d)"
  trap 'rm -rf -- "${TEST_ROOT}"' EXIT

  test_renders_frozen_kubeadm_contract "${TEST_ROOT}"
  test_typed_host_route_collision_is_rejected
  test_covering_host_route_collision_is_rejected
  test_default_route_is_not_a_cluster_cidr_collision
  test_route_inventory_failure_is_rejected
  test_primary_private_ipv4_must_belong_to_staging_vpc
  test_primary_private_ipv4_is_detected_from_route_source
  test_cluster_configuration_canonicalization_excludes_generated_token_but_preserves_drift
  test_cluster_configuration_canonicalization_rejects_missing_document
  test_kubernetes_binary_version_drift_is_rejected
  test_cluster_state_classification "${TEST_ROOT}"
  test_initialize_cluster_hides_bootstrap_token
  test_main_initializes_pristine_cluster_once "${TEST_ROOT}"
  test_main_reuses_complete_cluster "${TEST_ROOT}"
  test_main_rejects_partial_cluster_before_mutation
  test_validation_failure_stops_before_cluster_mutation "${TEST_ROOT}"
  test_image_pull_failure_stops_before_cluster_initialization "${TEST_ROOT}"

  echo "PASS: 400-initialize-cluster.sh contract tests"
}

run_tests
