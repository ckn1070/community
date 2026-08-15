#!/usr/bin/env bash

set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly SCRIPT_PATH="${TEST_DIRECTORY}/../500-install-calico.sh"
TEST_ROOT=""

# shellcheck source=../500-install-calico.sh
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

assert_log_precedes() {
  local action_log="$1"
  local first_action="$2"
  local second_action="$3"
  local message="$4"

  if ! awk -v first="${first_action}" -v second="${second_action}" '
      $0 == first && first_line == 0 { first_line = NR }
      $0 == second && second_line == 0 { second_line = NR }
      END { exit(first_line > 0 && second_line > first_line ? 0 : 1) }
    ' "${action_log}"; then
    echo "FAIL: ${message}" >&2
    exit 1
  fi
}

test_renders_frozen_calico_contract() {
  local test_root="$1"
  local installation_path="${test_root}/calico-installation.yaml"

  render_calico_installation "${installation_path}"

  assert_file_contains \
    "${installation_path}" \
    "    linuxDataplane: Iptables" \
    "일반 iptables dataplane을 명시해야 합니다."
  assert_file_contains \
    "${installation_path}" \
    "    kubeProxyManagement: Disabled" \
    "Calico가 kube-proxy를 대체하지 않도록 명시해야 합니다."
  assert_file_contains \
    "${installation_path}" \
    "      kubernetes: NodeInternalIP" \
    "Calico node address는 Kubernetes InternalIP를 사용해야 합니다."
  assert_file_contains \
    "${installation_path}" \
    "        cidr: 192.168.0.0/16" \
    "kubeadm과 공유하는 Pod CIDR을 사용해야 합니다."
  assert_file_contains \
    "${installation_path}" \
    "        encapsulation: VXLANCrossSubnet" \
    "확정한 cross-subnet VXLAN 정책을 사용해야 합니다."
  assert_file_contains \
    "${installation_path}" \
    "        natOutgoing: Enabled" \
    "Pod outbound NAT를 활성화해야 합니다."
}

test_download_rejects_checksum_mismatch() (
  local test_root="$1"
  local artifact_path="${test_root}/bad-download.yaml"
  local execution_status

  curl() {
    local argument
    local output_path=""
    local write_next=0

    for argument in "$@"; do
      if [[ "${write_next}" -eq 1 ]]; then
        output_path="${argument}"
        break
      fi
      if [[ "${argument}" == "--output" ]]; then
        write_next=1
      fi
    done
    printf '%s\n' "changed upstream bytes" >"${output_path}"
  }

  set +e
  (download_verified_artifact \
    "https://example.invalid/manifest.yaml" \
    "$(printf '0%.0s' {1..64})" \
    "${artifact_path}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 || -e "${artifact_path}" ]]; then
    echo "FAIL: checksum이 다른 download를 남기거나 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_network_manager_state() (
  local simulated_load_state="$1"
  local simulated_active_state="$2"
  local simulated_exit="$3"
  local expect_success="$4"
  local execution_status

  systemctl() {
    if [[ "$*" != "show NetworkManager.service --property=LoadState --property=ActiveState --no-pager" ]]; then
      return 78
    fi
    printf 'LoadState=%s\nActiveState=%s\n' \
      "${simulated_load_state}" \
      "${simulated_active_state}"
    return "${simulated_exit}"
  }

  set +e
  (require_network_manager_not_active >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${expect_success}" == "yes" && "${execution_status}" -ne 0 ]]; then
    echo "FAIL: 확정된 NetworkManager 비활성·부재 상태를 허용해야 합니다." >&2
    exit 1
  fi
  if [[ "${expect_success}" == "no" && "${execution_status}" -eq 0 ]]; then
    echo "FAIL: NetworkManager active·비정상·조회 실패 상태를 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_cni_configuration_lookup_matches_containerd_loader() (
  local test_root="$1"
  local configuration_directory="${test_root}/cni-configurations"
  local configuration_paths
  local configuration_count
  local expected_path

  mkdir -p \
    "${configuration_directory}" \
    "${configuration_directory}/40-directory.conf"
  : >"${configuration_directory}/.kubernetes-cni-keep"
  : >"${configuration_directory}/README"
  : >"${configuration_directory}/calico-kubeconfig"
  : >"${configuration_directory}/10-foreign.conf"
  : >"${configuration_directory}/20-foreign.conflist"
  : >"${configuration_directory}/30-foreign.json"
  : >"${configuration_directory}/.35-hidden.json"
  ln -s "${configuration_directory}/20-foreign.conflist" \
    "${configuration_directory}/50-linked.conflist"
  ln -s "${configuration_directory}/missing.conf" \
    "${configuration_directory}/60-broken.conf"

  configuration_paths="$(cni_configuration_paths "${configuration_directory}")"
  configuration_count="$(printf '%s\n' "${configuration_paths}" \
    | awk 'NF { count++ } END { print count + 0 }')"
  if [[ "${configuration_count}" -ne 6 ]]; then
    echo "FAIL: containerd가 읽는 CNI 설정 entry만 조회해야 합니다." >&2
    exit 1
  fi

  for expected_path in \
    "${configuration_directory}/10-foreign.conf" \
    "${configuration_directory}/20-foreign.conflist" \
    "${configuration_directory}/30-foreign.json" \
    "${configuration_directory}/.35-hidden.json" \
    "${configuration_directory}/50-linked.conflist" \
    "${configuration_directory}/60-broken.conf"; do
    if [[ $'\n'"${configuration_paths}"$'\n' != *$'\n'"${expected_path}"$'\n'* ]]; then
      echo "FAIL: containerd가 읽는 CNI 설정 entry를 조회하지 못했습니다: ${expected_path}" >&2
      exit 1
    fi
  done

  if [[ "${configuration_paths}" == *".kubernetes-cni-keep"* \
      || "${configuration_paths}" == *"/README"* \
      || "${configuration_paths}" == *"/calico-kubeconfig"* \
      || "${configuration_paths}" == *"40-directory.conf"* ]]; then
    echo "FAIL: containerd가 CNI 설정으로 읽지 않는 entry를 차단 대상으로 조회하면 안 됩니다." >&2
    exit 1
  fi
)

test_non_cni_entries_do_not_block_preflight() (
  local test_root="$1"
  local configuration_directory="${test_root}/non-cni-entries"

  mkdir -p \
    "${configuration_directory}" \
    "${configuration_directory}/directory.conf"
  : >"${configuration_directory}/.kubernetes-cni-keep"
  : >"${configuration_directory}/README"
  : >"${configuration_directory}/calico-kubeconfig"

  require_no_foreign_cni_configuration "${configuration_directory}"
)

test_ufw_state() (
  local simulated_status="$1"
  local simulated_exit="$2"
  local expect_success="$3"
  local execution_status

  ufw_is_available() { return 0; }
  ufw() {
    printf '%s\n' "${simulated_status}"
    return "${simulated_exit}"
  }

  set +e
  (require_ufw_inactive >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${expect_success}" == "yes" && "${execution_status}" -ne 0 ]]; then
    echo "FAIL: 확인된 inactive UFW를 허용해야 합니다." >&2
    exit 1
  fi
  if [[ "${expect_success}" == "no" && "${execution_status}" -eq 0 ]]; then
    echo "FAIL: active·조회 실패 UFW를 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_foreign_cni_configuration_is_rejected() (
  local test_root="$1"
  local configuration_directory="${test_root}/foreign-cni"
  local execution_status

  mkdir -p "${configuration_directory}"
  : >"${configuration_directory}/10-flannel.conflist"

  set +e
  (require_no_foreign_cni_configuration "${configuration_directory}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 CNI 설정을 자동 덮어쓰면 안 됩니다." >&2
    exit 1
  fi
)

test_hidden_foreign_cni_configuration_is_rejected() (
  local test_root="$1"
  local configuration_directory="${test_root}/hidden-foreign-cni"
  local execution_status

  mkdir -p "${configuration_directory}"
  : >"${configuration_directory}/.10-flannel.conflist"

  set +e
  (require_no_foreign_cni_configuration "${configuration_directory}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 숨김 CNI 설정도 containerd의 후보이므로 자동 통과하면 안 됩니다." >&2
    exit 1
  fi
)

test_calico_cni_configuration_is_reusable() (
  local test_root="$1"
  local configuration_directory="${test_root}/calico-cni"

  mkdir -p "${configuration_directory}"
  : >"${configuration_directory}/10-calico.conflist"
  : >"${configuration_directory}/calico-kubeconfig"

  require_no_foreign_cni_configuration "${configuration_directory}"
)

test_cni_file_lookup_failure_is_rejected() (
  local execution_status

  cni_configuration_paths() {
    return 76
  }

  set +e
  (require_no_foreign_cni_configuration >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: CNI file 조회 실패를 빈 상태로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_cni_configuration_directory_state() (
  local test_root="$1"
  local missing_directory="${test_root}/missing-cni-directory"
  local wrong_type_path="${test_root}/cni-directory-is-a-file"
  local configuration_paths
  local execution_status

  if ! configuration_paths="$(cni_configuration_paths "${missing_directory}")"; then
    echo "FAIL: 존재하지 않는 CNI 설정 directory는 빈 상태로 허용해야 합니다." >&2
    exit 1
  fi
  if [[ -n "${configuration_paths}" ]]; then
    echo "FAIL: 존재하지 않는 CNI 설정 directory에서 entry를 반환하면 안 됩니다." >&2
    exit 1
  fi

  : >"${wrong_type_path}"
  set +e
  (cni_configuration_paths "${wrong_type_path}" >/dev/null 2>&1)
  execution_status=$?
  set -e
  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: CNI 설정 경로가 directory가 아니면 조회 실패여야 합니다." >&2
    exit 1
  fi
)

test_cni_directory_enumeration_failure_is_rejected() (
  local test_root="$1"
  local configuration_directory="${test_root}/unreadable-cni-directory"
  local execution_status

  mkdir -p "${configuration_directory}"
  find() { return 76; }

  set +e
  (require_no_foreign_cni_configuration "${configuration_directory}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: CNI directory 열거 실패를 빈 상태로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_raw_manifest_calico_is_rejected() (
  local execution_status

  kubectl_admin_short() {
    if [[ "$*" == *"daemonset/calico-node"* ]]; then
      printf '%s\n' "daemonset.apps/calico-node"
    fi
  }

  set +e
  (require_no_raw_manifest_calico >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: kube-system raw-manifest Calico를 operator 방식으로 자동 전환하면 안 됩니다." >&2
    exit 1
  fi
)

test_raw_manifest_lookup_failure_is_rejected() (
  local execution_status

  kubectl_admin_short() {
    return 77
  }

  set +e
  (require_no_raw_manifest_calico >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: raw-manifest 조회 실패를 resource 없음으로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_existing_operator_version_is_guarded() (
  local simulated_state="$1"
  local simulated_exit="$2"
  local expect_success="$3"
  local execution_status

  kubectl_admin_short() {
    printf '%s' "${simulated_state}"
    return "${simulated_exit}"
  }

  set +e
  (require_exact_existing_calico_operator >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${expect_success}" == "yes" && "${execution_status}" -ne 0 ]]; then
    echo "FAIL: 없거나 exact version인 Tigera operator는 재실행에서 허용해야 합니다." >&2
    exit 1
  fi
  if [[ "${expect_success}" == "no" && "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 다른 version·구조 또는 조회 실패를 자동 변경 대상으로 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_single_node_target_is_accepted() (
  kubectl_admin_short() {
    case "$*" in
      *"-l node-role.kubernetes.io/control-plane"*)
        printf '%s\n' "node-01"
        ;;
      *)
        printf '%s\n' $'node-01\tv1.36.2'
        ;;
    esac
  }

  assert_equals \
    "node-01" \
    "$(control_plane_node_name)" \
    "Exact Kubernetes version의 유일한 control-plane Node를 선택해야 합니다."
)

test_extra_worker_node_is_rejected() (
  local execution_status

  kubectl_admin_short() {
    case "$*" in
      *"-l node-role.kubernetes.io/control-plane"*)
        printf '%s\n' "node-01"
        ;;
      *)
        printf '%s\n' $'node-01\tv1.36.2' $'node-02\tv1.36.2'
        ;;
    esac
  }

  set +e
  (control_plane_node_name >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: worker가 추가된 cluster를 단일 Node 지원 범위로 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_node_version_drift_is_rejected() (
  local execution_status

  kubectl_admin_short() {
    case "$*" in
      *"-l node-role.kubernetes.io/control-plane"*)
        printf '%s\n' "node-01"
        ;;
      *)
        printf '%s\n' $'node-01\tv1.35.0'
        ;;
    esac
  }

  set +e
  (control_plane_node_name >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 지원 Kubernetes version과 다른 Node를 mutation 대상으로 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_node_without_control_plane_role_is_rejected() (
  local execution_status

  kubectl_admin_short() {
    case "$*" in
      *"-l node-role.kubernetes.io/control-plane"*)
        printf '%s' ""
        ;;
      *)
        printf '%s\n' $'node-01\tv1.36.2'
        ;;
    esac
  }

  set +e
  (control_plane_node_name >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 유일한 Node에 control-plane role이 없으면 mutation 대상으로 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_pod_cidr_source_is_guarded() (
  local simulated_argument="$1"
  local simulated_exit="$2"
  local expect_success="$3"
  local execution_status

  kubectl_admin_short() {
    printf '%s\n' "${simulated_argument}"
    return "${simulated_exit}"
  }

  set +e
  (require_exact_pod_cidr_source >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${expect_success}" == "yes" && "${execution_status}" -ne 0 ]]; then
    echo "FAIL: exact controller-manager Pod CIDR을 허용해야 합니다." >&2
    exit 1
  fi
  if [[ "${expect_success}" == "no" && "${execution_status}" -eq 0 ]]; then
    echo "FAIL: Pod CIDR drift·조회 실패를 mutation 전에 거부해야 합니다." >&2
    exit 1
  fi
)

test_pod_cidr_query_outputs_current_scalar_and_rejects_missing_keys() (
  local execution_status

  kubectl_admin_short() {
    if [[ "$*" != *'--allow-missing-template-keys=false'* \
      || "$*" != *'jsonpath={range .items[*].spec.containers[?(@.name=="kube-controller-manager")].command[*]}{@}{"\n"}{end}'* ]]; then
      return 78
    fi
    printf '%s\n' "--cluster-cidr=192.168.0.0/16"
  }

  set +e
  (require_exact_pod_cidr_source >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -ne 0 ]]; then
    echo "FAIL: command scalar 현재값을 출력하고 missing template key를 오류로 처리해야 합니다." >&2
    exit 1
  fi
)

test_calico_base_apply_does_not_force_field_ownership() (
  local test_root="$1"
  local action_log="${test_root}/base-apply-action-log"

  kubectl_admin() {
    printf '%s\n' "$*" >>"${action_log}"
  }

  apply_calico_base_resources "${test_root}"
  assert_file_excludes \
    "${action_log}" \
    "--force-conflicts" \
    "Calico base apply가 다른 manager의 field ownership을 강제로 인수하면 안 됩니다."
)

test_calico_base_apply_conflict_stops_followup_mutation() (
  local test_root="$1"
  local action_log="${test_root}/base-conflict-action-log"
  local execution_status

  kubectl_admin() {
    printf '%s\n' "$*" >>"${action_log}"
    if [[ "$1" == "apply" ]]; then
      return 1
    fi
    echo "FAIL: 첫 SSA conflict 뒤 후속 command를 실행하면 안 됩니다." >&2
    return 1
  }

  set +e
  (apply_calico_base_resources "${test_root}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 || "$(wc -l <"${action_log}")" -ne 1 ]]; then
    echo "FAIL: 첫 SSA conflict를 즉시 전파해야 합니다." >&2
    exit 1
  fi
)

test_calico_installation_is_applied_on_each_run() (
  local test_root="$1"
  local action_log="${test_root}/installation-apply-action-log"

  kubectl_admin() {
    printf '%s\n' "$*" >>"${action_log}"
  }

  apply_calico_installation "/tmp/calico-installation.yaml"
  apply_calico_installation "/tmp/calico-installation.yaml"

  assert_equals \
    "2" \
    "$(wc -l <"${action_log}" | tr -d ' ')" \
    "동일 Installation의 무해한 SSA를 재실행에서도 수렴시켜야 합니다."
  assert_file_excludes \
    "${action_log}" \
    "--force-conflicts" \
    "Installation apply가 field ownership을 강제로 인수하면 안 됩니다."
)

test_exact_calico_contract_is_accepted() (
  kubectl_admin_short() {
    case "$*" in
      *"deployment/tigera-operator"*)
        printf '%s\n' "quay.io/tigera/operator:v1.42.3"
        ;;
      *"installation.operator.tigera.io/default"*)
        printf '%s\n' $'Iptables\tDisabled\tNodeInternalIP\tdefault-ipv4-ippool\t26\t192.168.0.0/16\tVXLANCrossSubnet\tEnabled\tall()'
        ;;
    esac
  }

  require_exact_calico_contract
)

test_calico_installation_drift_is_rejected() (
  local execution_status

  kubectl_admin_short() {
    case "$*" in
      *"deployment/tigera-operator"*)
        printf '%s\n' "quay.io/tigera/operator:v1.42.3"
        ;;
      *"installation.operator.tigera.io/default"*)
        printf '%s\n' $'Iptables\tDisabled\tNodeInternalIP\tdefault-ipv4-ippool\t26\t10.244.0.0/16\tVXLANCrossSubnet\tEnabled\tall()'
        ;;
    esac
  }

  set +e
  (require_exact_calico_contract >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 적용 후 Calico Installation drift를 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_exact_runtime_ippool_is_accepted() (
  kubectl_admin_short() {
    printf '%s\t' \
      "default-ipv4-ippool" \
      "192.168.0.0/16" \
      "26" \
      "CrossSubnet" \
      "Never" \
      "true" \
      "all()"
  }

  require_actual_calico_ippool_matches
)

test_runtime_ippool_drift_is_rejected() (
  local execution_status

  kubectl_admin_short() {
    printf '%s\n' $'default-ipv4-ippool\t192.168.0.0/16\t26\tAlways\tNever\ttrue\tall()\tfalse'
  }

  set +e
  (require_actual_calico_ippool_matches >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 실제 IPPool의 VXLAN drift를 허용하면 안 됩니다." >&2
    exit 1
  fi
)

test_control_plane_taint_removal_is_state_aware() (
  local action_log="$1"

  control_plane_node_name() { printf '%s\n' "node-01"; }
  control_plane_taint_exists() { return 0; }
  kubectl_admin() { printf '%s\n' "$*" >>"${action_log}"; }

  remove_control_plane_taint >/dev/null
  assert_equals \
    "taint node node-01 node-role.kubernetes.io/control-plane-" \
    "$(<"${action_log}")" \
    "현재 존재하는 control-plane taint만 제거해야 합니다."
)

test_absent_control_plane_taint_is_reused() (
  control_plane_node_name() { printf '%s\n' "node-01"; }
  control_plane_taint_exists() { return 1; }
  kubectl_admin() {
    echo "FAIL: 이미 없는 taint 제거를 반복하면 안 됩니다." >&2
    return 1
  }

  remove_control_plane_taint >/dev/null
)

test_owned_stale_smoke_namespace_is_replaced() (
  local action_log="$1"

  smoke_namespace_state() {
    printf '%s\n' $'car-community-network-smoke\tcar-community-bootstrap'
  }
  delete_smoke_namespace() { printf '%s\n' "delete:$1" >>"${action_log}"; }
  apply_smoke_namespace() { printf '%s\n' "apply:$1" >>"${action_log}"; }

  prepare_smoke_namespace "car-community-network-smoke"
  assert_equals \
    $'delete:car-community-network-smoke\napply:car-community-network-smoke' \
    "$(<"${action_log}")" \
    "이전 실행이 남긴 bootstrap marker namespace를 정리한 뒤 다시 만들어야 합니다."
)

test_unmarked_smoke_namespace_is_rejected() (
  local execution_status

  smoke_namespace_state() {
    printf '%s\n' $'car-community-network-smoke\tother-manager'
  }
  delete_smoke_namespace() {
    echo "FAIL: marker가 다른 namespace를 삭제하면 안 됩니다." >&2
    return 1
  }
  apply_smoke_namespace() {
    echo "FAIL: marker가 다른 namespace를 덮어쓰면 안 됩니다." >&2
    return 1
  }

  set +e
  (prepare_smoke_namespace "car-community-network-smoke" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: bootstrap marker가 다른 고정 smoke namespace를 재사용하면 안 됩니다." >&2
    exit 1
  fi
)

test_smoke_connection_uses_agnhost_client() (
  local action_log="$1"

  kubectl_admin() { printf '%s\n' "$*" >"${action_log}"; }

  verify_smoke_service_connection "car-community-network-smoke"
  assert_equals \
    "-n car-community-network-smoke exec network-smoke-client -- /agnhost connect --timeout=30s network-smoke:8080" \
    "$(<"${action_log}")" \
    "Smoke client는 image에 없는 command를 가정하지 않고 공식 agnhost connect로 같은 namespace의 Service DNS를 확인해야 합니다."
)

test_smoke_failure_cleans_owned_namespace() (
  local action_log="$1"
  local execution_status

  prepare_smoke_namespace() { printf '%s\n' "prepare:$1" >>"${action_log}"; }
  configure_smoke_resources() { printf '%s\n' "configure:$1" >>"${action_log}"; }
  wait_for_smoke_resources() { return 1; }
  print_smoke_diagnostics() { printf '%s\n' "diagnose:$1" >>"${action_log}"; }
  verify_smoke_service_connection() {
    echo "FAIL: readiness 실패 뒤 connection 검증을 실행하면 안 됩니다." >&2
    return 1
  }
  delete_smoke_namespace() { printf '%s\n' "delete:$1" >>"${action_log}"; }

  set +e
  (run_workload_smoke_test "car-community-network-smoke" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: smoke readiness 실패를 전파해야 합니다." >&2
    exit 1
  fi
  assert_equals \
    $'prepare:car-community-network-smoke\nconfigure:car-community-network-smoke\ndiagnose:car-community-network-smoke\ndelete:car-community-network-smoke' \
    "$(<"${action_log}")" \
    "smoke 실패 시 진단 뒤 bootstrap marker namespace를 정리해야 합니다."
)

test_main_preserves_required_network_order() (
  local test_root="$1"
  local action_log="${test_root}/main-action-log"
  local test_work_directory="${test_root}/main-work"

  run_preflight_checks() { printf '%s\n' "preflight" >>"${action_log}"; }
  create_work_directory() {
    mkdir -p "${test_work_directory}"
    printf '%s\n' "${test_work_directory}"
  }
  download_calico_artifacts() { printf '%s\n' "download" >>"${action_log}"; }
  render_calico_installation() { : >"$1"; }
  apply_calico_base_resources() { printf '%s\n' "base" >>"${action_log}"; }
  apply_calico_installation() { printf '%s\n' "installation" >>"${action_log}"; }
  wait_for_calico_network() { printf '%s\n' "calico-ready" >>"${action_log}"; }
  remove_control_plane_taint() { printf '%s\n' "untaint" >>"${action_log}"; }
  verify_cluster_network_postconditions() { printf '%s\n' "cluster-ready" >>"${action_log}"; }
  run_workload_smoke_test() { printf '%s\n' "smoke" >>"${action_log}"; }

  main >/dev/null

  assert_log_precedes "${action_log}" "download" "base" \
    "checksum 대상 artifact를 준비한 뒤에만 apply해야 합니다."
  assert_log_precedes "${action_log}" "base" "installation" \
    "CRD와 operator를 준비한 뒤에만 Installation을 apply해야 합니다."
  assert_log_precedes "${action_log}" "installation" "calico-ready" \
    "Installation을 적용한 뒤 Calico readiness를 확인해야 합니다."
  assert_log_precedes "${action_log}" "calico-ready" "untaint" \
    "Calico Available 뒤에만 일반 workload scheduling을 열어야 합니다."
  assert_log_precedes "${action_log}" "untaint" "smoke" \
    "단일 control-plane Node의 workload scheduling을 연 뒤 smoke Pod를 실행해야 합니다."
  assert_log_precedes "${action_log}" "cluster-ready" "smoke" \
    "cluster network postcondition 뒤 일반 workload smoke를 실행해야 합니다."
)

test_main_stops_before_mutation_on_preflight_failure() (
  local test_root="$1"
  local action_log="${test_root}/preflight-failure-action-log"
  local execution_status

  run_preflight_checks() { return 73; }
  create_work_directory() { printf '%s\n' "mutation" >>"${action_log}"; }

  set +e
  (set -e; main >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 || -e "${action_log}" ]]; then
    echo "FAIL: target preflight 실패 뒤 artifact 준비나 cluster mutation을 시작하면 안 됩니다." >&2
    exit 1
  fi
)

run_tests() {
  TEST_ROOT="$(mktemp -d)"
  trap 'rm -rf -- "${TEST_ROOT}"' EXIT

  test_renders_frozen_calico_contract "${TEST_ROOT}"
  test_download_rejects_checksum_mismatch "${TEST_ROOT}"
  test_network_manager_state "not-found" "inactive" 0 yes
  test_network_manager_state "loaded" "inactive" 0 yes
  test_network_manager_state "loaded" "active" 0 no
  test_network_manager_state "loaded" "failed" 0 no
  test_network_manager_state "loaded" "activating" 0 no
  test_network_manager_state "" "inactive" 0 no
  test_network_manager_state "loaded" "" 0 no
  test_network_manager_state "not-found" "inactive" 75 no
  test_cni_configuration_lookup_matches_containerd_loader "${TEST_ROOT}"
  test_non_cni_entries_do_not_block_preflight "${TEST_ROOT}"
  test_ufw_state "Status: inactive" 0 yes
  test_ufw_state "Status: active" 0 no
  test_ufw_state "" 1 no
  test_foreign_cni_configuration_is_rejected "${TEST_ROOT}"
  test_hidden_foreign_cni_configuration_is_rejected "${TEST_ROOT}"
  test_calico_cni_configuration_is_reusable "${TEST_ROOT}"
  test_cni_configuration_directory_state "${TEST_ROOT}"
  test_cni_directory_enumeration_failure_is_rejected "${TEST_ROOT}"
  test_cni_file_lookup_failure_is_rejected
  test_raw_manifest_calico_is_rejected
  test_raw_manifest_lookup_failure_is_rejected
  test_existing_operator_version_is_guarded "" 0 yes
  test_existing_operator_version_is_guarded $'tigera-operator\tquay.io/tigera/operator:v1.42.3' 0 yes
  test_existing_operator_version_is_guarded $'tigera-operator\tquay.io/tigera/operator:v1.41.0' 0 no
  test_existing_operator_version_is_guarded "" 77 no
  test_single_node_target_is_accepted
  test_extra_worker_node_is_rejected
  test_node_version_drift_is_rejected
  test_node_without_control_plane_role_is_rejected
  test_pod_cidr_query_outputs_current_scalar_and_rejects_missing_keys
  test_pod_cidr_source_is_guarded "--cluster-cidr=192.168.0.0/16" 0 yes
  test_pod_cidr_source_is_guarded "" 0 no
  test_pod_cidr_source_is_guarded $'kube-controller-manager\n--bind-address=127.0.0.1' 0 no
  test_pod_cidr_source_is_guarded "--cluster-cidr=10.244.0.0/16" 0 no
  test_pod_cidr_source_is_guarded $'--cluster-cidr=192.168.0.0/16\n--cluster-cidr=192.168.0.0/16' 0 no
  test_pod_cidr_source_is_guarded $'--cluster-cidr=192.168.0.0/16\n--cluster-cidr=10.244.0.0/16' 0 no
  test_pod_cidr_source_is_guarded "" 76 no
  test_calico_base_apply_does_not_force_field_ownership "${TEST_ROOT}"
  test_calico_base_apply_conflict_stops_followup_mutation "${TEST_ROOT}"
  test_calico_installation_is_applied_on_each_run "${TEST_ROOT}"
  test_exact_calico_contract_is_accepted
  test_calico_installation_drift_is_rejected
  test_exact_runtime_ippool_is_accepted
  test_runtime_ippool_drift_is_rejected
  test_control_plane_taint_removal_is_state_aware "${TEST_ROOT}/taint-action-log"
  test_absent_control_plane_taint_is_reused
  test_owned_stale_smoke_namespace_is_replaced "${TEST_ROOT}/stale-smoke-action-log"
  test_unmarked_smoke_namespace_is_rejected
  test_smoke_connection_uses_agnhost_client "${TEST_ROOT}/smoke-client-action-log"
  test_smoke_failure_cleans_owned_namespace "${TEST_ROOT}/smoke-cleanup-action-log"
  test_main_preserves_required_network_order "${TEST_ROOT}"
  test_main_stops_before_mutation_on_preflight_failure "${TEST_ROOT}"

  echo "PASS: 500-install-calico.sh contract tests"
}

run_tests
