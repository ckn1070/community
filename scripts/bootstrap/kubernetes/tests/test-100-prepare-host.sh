#!/usr/bin/env bash

set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly SCRIPT_PATH="${TEST_DIRECTORY}/../100-prepare-host.sh"
TEST_ROOT=""

# shellcheck source=../100-prepare-host.sh
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

  if ! grep -Fqx "${expected_line}" "${file_path}"; then
    echo "FAIL: ${message}" >&2
    exit 1
  fi
}

test_backup_file_once_publishes_only_complete_backup() (
  local test_root="$1"
  local source_path="${test_root}/backup-source"
  local backup_path="${test_root}/backup-target"

  printf '%s\n' "original" >"${source_path}"
  backup_file_once "${source_path}" "${backup_path}"
  assert_equals "original" "$(<"${backup_path}")" "완성된 backup만 최종 경로에 게시해야 합니다."

  printf '%s\n' "changed" >"${source_path}"
  backup_file_once "${source_path}" "${backup_path}"
  assert_equals "original" "$(<"${backup_path}")" "완성된 최초 backup을 재실행으로 덮어쓰면 안 됩니다."

  rm -- "${backup_path}"

  cp() {
    local destination_path

    for destination_path in "$@"; do
      :
    done
    printf '%s\n' "partial" >"${destination_path}"
    return 1
  }

  if backup_file_once "${source_path}" "${backup_path}" 2>/dev/null; then
    echo "FAIL: 일부만 복사한 뒤 실패한 backup을 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi

  if [[ -e "${backup_path}" ]]; then
    echo "FAIL: 일부만 복사한 뒤 실패하면 최종 backup 경로를 남기면 안 됩니다." >&2
    exit 1
  fi
)

test_fstab_temporary_files_are_cleaned_when_backup_fails() {
  local test_root="$1"
  local fstab_path="${test_root}/fstab-with-backup-failure"
  local execution_status
  local -a temporary_files

  printf '%s\n' '/swapfile none swap sw 0 0' >"${fstab_path}"

  set +e
  (
    set -e
    cp() {
      local destination_path

      for destination_path in "$@"; do
        :
      done
      printf '%s\n' "partial" >"${destination_path}"
      return 1
    }

    disable_persistent_fstab_swap "${fstab_path}" >/dev/null 2>&1
  )
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: backup 실패를 fstab 변경 성공으로 처리하면 안 됩니다." >&2
    exit 1
  fi

  shopt -s nullglob
  temporary_files=("${test_root}"/.100-prepare-host.*)
  shopt -u nullglob
  if [[ "${#temporary_files[@]}" -ne 0 ]]; then
    echo "FAIL: backup 실패 뒤 fstab 변경용 임시 file을 남기면 안 됩니다." >&2
    exit 1
  fi
}

test_disable_persistent_fstab_swap() {
  local test_root="$1"
  local fstab_path="${test_root}/fstab"
  local first_backup_checksum

  cat >"${fstab_path}" <<'EOF'
UUID=root / ext4 defaults 0 1
/swap.img none swap sw 0 0
# /old-swap none swap sw 0 0
EOF

  disable_persistent_fstab_swap "${fstab_path}" >/dev/null

  if [[ -n "$(persistent_fstab_swap_entries "${fstab_path}")" ]]; then
    echo "FAIL: 활성 fstab swap 설정이 남았습니다." >&2
    exit 1
  fi
  assert_file_contains \
    "${fstab_path}" \
    "# Kubernetes bootstrap disabled swap: /swap.img none swap sw 0 0" \
    "활성 swap 줄을 설명과 함께 주석 처리해야 합니다."
  assert_file_contains \
    "${fstab_path}" \
    "# /old-swap none swap sw 0 0" \
    "기존 주석은 보존해야 합니다."

  first_backup_checksum="$(cksum "${fstab_path}.before-kubernetes-bootstrap")"
  disable_persistent_fstab_swap "${fstab_path}" >/dev/null
  assert_equals \
    "${first_backup_checksum}" \
    "$(cksum "${fstab_path}.before-kubernetes-bootstrap")" \
    "재실행 시 최초 backup을 덮어쓰면 안 됩니다."
}

test_write_ipv4_forwarding_configuration() {
  local test_root="$1"
  local configuration_path="${test_root}/kubernetes.conf"
  local first_backup_checksum

  printf '%s\n' "net.ipv4.ip_forward = 0" >"${configuration_path}"
  write_ipv4_forwarding_configuration "${configuration_path}" >/dev/null

  assert_equals \
    "${IPV4_FORWARDING_CONFIGURATION}" \
    "$(<"${configuration_path}")" \
    "IPv4 forwarding 영구 설정이 정확해야 합니다."

  first_backup_checksum="$(cksum "${configuration_path}.before-kubernetes-bootstrap")"
  write_ipv4_forwarding_configuration "${configuration_path}" >/dev/null
  assert_equals \
    "${first_backup_checksum}" \
    "$(cksum "${configuration_path}.before-kubernetes-bootstrap")" \
    "재실행 시 최초 sysctl backup을 덮어쓰면 안 됩니다."
}

test_classify_ufw_status() {
  assert_equals "inactive" "$(classify_ufw_status 'Status: inactive')" "비활성 UFW를 판정해야 합니다."
  assert_equals "active" "$(classify_ufw_status 'Status: active')" "활성 UFW를 판정해야 합니다."
  assert_equals "unknown" "$(classify_ufw_status 'unexpected')" "알 수 없는 UFW 출력을 통과시키면 안 됩니다."
}

test_empty_systemd_swap_inventory_is_not_a_query_failure() (
  local enabled_swap_units

  systemctl() {
    if [[ " $* " == *" --type=swap "* ]]; then
      return 1
    fi

    printf '%s\n' \
      "systemd-networkd.service enabled enabled" \
      "systemd-tmpfiles-clean.timer static -"
  }

  if ! enabled_swap_units="$(find_enabled_systemd_swap_units)"; then
    echo "FAIL: swap unit이 없는 정상 inventory를 조회 실패로 처리하면 안 됩니다." >&2
    exit 1
  fi
  assert_equals "" "${enabled_swap_units}" "swap unit이 없으면 빈 정상 결과여야 합니다."
)

test_find_enabled_systemd_swap_units_filters_full_inventory() (
  local enabled_swap_units

  systemctl() {
    printf '%s\n' \
      "systemd-networkd.service enabled enabled" \
      "dev-zram0.swap enabled enabled" \
      "dev-zram1.swap disabled enabled" \
      "temporary.swap enabled-runtime enabled"
  }

  enabled_swap_units="$(find_enabled_systemd_swap_units)"
  assert_equals \
    $'dev-zram0.swap\ntemporary.swap' \
    "${enabled_swap_units}" \
    "전체 unit file inventory에서 enabled swap unit만 찾아야 합니다."
)

test_systemd_unit_inventory_failure_is_propagated() (
  systemctl() {
    return 75
  }

  if find_enabled_systemd_swap_units >/dev/null 2>&1; then
    echo "FAIL: systemd unit file inventory 조회 실패를 빈 목록으로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_disable_swap_rejects_inventory_failure() (
  local execution_status

  swapon() {
    return 73
  }
  disable_persistent_fstab_swap() {
    echo "FAIL: 현재 swap 조회 실패 뒤 fstab을 변경하면 안 됩니다." >&2
    return 1
  }

  set +e
  (disable_swap >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: 현재 swap 조회 실패를 활성 swap 없음으로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_fstab_inventory_failure_stops_before_mutation() (
  local test_root="$1"
  local fstab_path="${test_root}/fstab-query-failure"
  local execution_status

  : >"${fstab_path}"
  persistent_fstab_swap_entries() {
    return 74
  }
  backup_file_once() {
    echo "FAIL: fstab 조회 실패 뒤 backup을 만들면 안 됩니다." >&2
    return 1
  }

  set +e
  (disable_persistent_fstab_swap "${fstab_path}" >/dev/null 2>&1)
  execution_status=$?
  set -e

  if [[ "${execution_status}" -eq 0 ]]; then
    echo "FAIL: fstab 조회 실패를 영구 swap 없음으로 처리하면 안 됩니다." >&2
    exit 1
  fi
)

test_install_host_operator_packages_installs_jq() (
  apt-get() {
    printf '%s\n' "$*"
  }

  assert_equals \
    "install --yes jq" \
    "$(install_host_operator_packages)" \
    "Host 준비 단계가 Super Admin 운영 절차에 필요한 jq를 설치해야 합니다."
)

test_verify_host_operator_commands_rejects_missing_jq() (
  command() {
    if [[ "$1" == "-v" && "$2" == "jq" ]]; then
      return 1
    fi
    builtin command "$@"
  }

  if (verify_host_operator_commands >/dev/null 2>&1); then
    echo "FAIL: jq가 없는데 host 준비 postcondition을 통과시키면 안 됩니다." >&2
    exit 1
  fi
)

cleanup_test_root() {
  if [[ -n "${TEST_ROOT}" ]]; then
    rm -rf -- "${TEST_ROOT}"
  fi
}

main() {
  TEST_ROOT="$(mktemp -d)"
  trap cleanup_test_root EXIT

  test_backup_file_once_publishes_only_complete_backup "${TEST_ROOT}"
  test_fstab_temporary_files_are_cleaned_when_backup_fails "${TEST_ROOT}"
  test_disable_persistent_fstab_swap "${TEST_ROOT}"
  test_write_ipv4_forwarding_configuration "${TEST_ROOT}"
  test_classify_ufw_status
  test_empty_systemd_swap_inventory_is_not_a_query_failure
  test_find_enabled_systemd_swap_units_filters_full_inventory
  test_systemd_unit_inventory_failure_is_propagated
  test_disable_swap_rejects_inventory_failure
  test_fstab_inventory_failure_stops_before_mutation "${TEST_ROOT}"
  test_install_host_operator_packages_installs_jq
  test_verify_host_operator_commands_rejects_missing_jq

  echo "PASS: 100-prepare-host.sh contract tests"
}

main "$@"
