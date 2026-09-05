#!/usr/bin/env bash

# Staging DB role bootstrap의 필수 설치·입력·SQL·secret 비노출 계약을 검증합니다.
set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly TARGET_SCRIPT="${TEST_DIRECTORY}/../100-provision-database-access.sh"
readonly STAGING_DATABASE_CONFIG="${TEST_DIRECTORY}/../../../../deploy/overlays/stg/pre-sync/kustomization.yaml"

fail_test() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local path="$1"
  local expected="$2"
  local message="$3"

  grep -Fq -- "${expected}" "${path}" || fail_test "${message}"
}

assert_excludes() {
  local path="$1"
  local unexpected="$2"
  local message="$3"

  if grep -Fq -- "${unexpected}" "${path}"; then
    fail_test "${message}"
  fi
}

# shellcheck source=../100-provision-database-access.sh
source "${TARGET_SCRIPT}"

test_uses_existing_postgresql_16_client_without_installing() (
  psql() {
    printf '%s\n' 'psql (PostgreSQL) 16.10'
  }
  apt-get() {
    fail_test "이미 PostgreSQL 16 client가 있으면 apt-get을 실행하면 안 됩니다."
  }

  ensure_postgresql_client >/dev/null
)

test_installs_postgresql_16_client_when_missing() (
  local test_root="$1"
  local fake_bin="${test_root}/postgresql-client-bin"
  local apt_log="${test_root}/apt.log"

  mkdir -p "${fake_bin}"
  cat >"${fake_bin}/apt-get" <<'EOF'
#!/bin/bash
set -euo pipefail
printf '%s\n' "$*" >>"${TEST_APT_LOG}"
if [[ "$1" == "install" ]]; then
  cat >"${TEST_FAKE_BIN}/psql" <<'PSQL'
#!/bin/bash
printf '%s\n' 'psql (PostgreSQL) 16.10'
PSQL
  chmod 0755 "${TEST_FAKE_BIN}/psql"
fi
EOF
  chmod 0755 "${fake_bin}/apt-get"

  PATH="${fake_bin}:/usr/bin:/bin"
  TEST_APT_LOG="${apt_log}"
  TEST_FAKE_BIN="${fake_bin}"
  export PATH TEST_APT_LOG TEST_FAKE_BIN

  ensure_postgresql_client >/dev/null

  assert_contains "${apt_log}" 'update' \
    "PostgreSQL client가 없으면 package 색인을 갱신해야 합니다."
  assert_contains "${apt_log}" 'install --yes --no-install-recommends postgresql-client-16' \
    "PostgreSQL 16 client package를 최소 dependency로 설치해야 합니다."
)

test_rejects_non_postgresql_16_client() (
  local execution_status

  psql() {
    printf '%s\n' 'psql (PostgreSQL) 15.14'
  }

  set +e
  (ensure_postgresql_client) >/dev/null 2>&1
  execution_status=$?
  set -e
  [[ "${execution_status}" -ne 0 ]] \
    || fail_test "PostgreSQL 16이 아닌 client를 허용하면 안 됩니다."
)

test_reads_exact_three_nul_fields_and_uses_fixed_database_url() (
  read_fixed_input < <(printf '%s\0' \
    'master-password' \
    'runtime-password' \
    'migrator-password')

  [[ "${DATABASE_URL}" == 'jdbc:postgresql://example-staging-postgres.ap-northeast-2.rds.amazonaws.com:5432/carmunity?currentSchema=public&sslmode=verify-full&sslrootcert=/etc/car-community/rds-ca/ap-northeast-2-bundle.pem' ]] \
    || fail_test "Staging DB URL은 Git에 확정한 exact 값이어야 합니다."
  [[ "${RUNTIME_PASSWORD}" == 'runtime-password' ]] \
    || fail_test "Runtime password field를 읽지 못했습니다."
  [[ "${MIGRATOR_PASSWORD}" == 'migrator-password' ]] \
    || fail_test "Migrator password field를 읽지 못했습니다."
  assert_contains "${STAGING_DATABASE_CONFIG}" "- DB_URL=${DATABASE_URL}" \
    "Bootstrap과 staging manifest가 같은 DB URL을 사용해야 합니다."
)

test_rejects_missing_or_trailing_input() (
  local execution_status

  set +e
  (read_fixed_input < <(printf '%s\0' 'only-one-field')) >/dev/null 2>&1
  execution_status=$?
  set -e
  [[ "${execution_status}" -ne 0 ]] || fail_test "누락된 NUL field를 허용하면 안 됩니다."

  set +e
  (read_fixed_input < <(printf '%s\0%s\0%s\0extra' \
    'master-password' 'runtime-password' 'migrator-password')) >/dev/null 2>&1
  execution_status=$?
  set -e
  [[ "${execution_status}" -ne 0 ]] || fail_test "마지막 field 뒤 byte를 허용하면 안 됩니다."
)

test_rejects_reused_passwords() (
  local execution_status

  DATABASE_MASTER_PASSWORD='master-password'
  RUNTIME_PASSWORD='runtime-password'
  MIGRATOR_PASSWORD='migrator-password'
  MIGRATOR_PASSWORD='runtime-password'
  set +e
  (validate_inputs) >/dev/null 2>&1
  execution_status=$?
  set -e
  [[ "${execution_status}" -ne 0 ]] || fail_test "DB password 재사용을 허용하면 안 됩니다."
)

test_usage_does_not_request_fixed_database_url() (
  local help_output="${1}/help-output"

  print_usage >"${help_output}"
  assert_excludes "${help_output}" 'STG_DB_URL' \
    "고정한 staging DB URL을 입력으로 안내하면 안 됩니다."
  assert_contains "${help_output}" '정확히 세 개의 NUL-delimited password field' \
    "세 password 입력 계약을 안내해야 합니다."
)

test_creates_only_missing_roles_and_applies_permissions() (
  local test_root="$1"
  local case_name="$2"
  local runtime_exists="$3"
  local migrator_exists="$4"
  local sql_log="${test_root}/${case_name}.sql"
  local argument_log="${test_root}/${case_name}.arguments"
  local secret='DB-PASSWORD-MUST-NOT-APPEAR-IN-ARGS'
  local runtime_checks=0
  local migrator_checks=0

  DATABASE_MASTER_PASSWORD="${secret}-master"
  RUNTIME_PASSWORD="${secret}-runtime"
  MIGRATOR_PASSWORD="${secret}-migrator"
  RDS_CA_PATH="${test_root}/rds-ca.pem"
  printf '%s\n' 'test-ca' >"${RDS_CA_PATH}"

  database_role_exists() {
    case "$1" in
      carmunity_runtime)
        runtime_checks=$((runtime_checks + 1))
        [[ "${runtime_checks}" -gt 1 || "${runtime_exists}" == true ]]
        ;;
      carmunity_migrator)
        migrator_checks=$((migrator_checks + 1))
        [[ "${migrator_checks}" -gt 1 || "${migrator_exists}" == true ]]
        ;;
      *) fail_test "알 수 없는 role 조회입니다: $1" ;;
    esac
  }
  run_master_psql() {
    printf '%s\n' "$*" >"${argument_log}"
    cat >"${sql_log}"
  }

  provision_database_access

  if [[ "${runtime_exists}" == true ]]; then
    assert_excludes "${sql_log}" 'CREATE ROLE carmunity_runtime LOGIN;' \
      "기존 Runtime role을 다시 생성하면 안 됩니다."
    assert_excludes "${sql_log}" '\password carmunity_runtime' \
      "기존 Runtime role의 password를 바꾸면 안 됩니다."
  else
    assert_contains "${sql_log}" 'CREATE ROLE carmunity_runtime LOGIN;' \
      "없는 Runtime role을 생성해야 합니다."
    assert_contains "${sql_log}" '\password carmunity_runtime' \
      "새 Runtime role의 password를 설정해야 합니다."
  fi

  if [[ "${migrator_exists}" == true ]]; then
    assert_excludes "${sql_log}" 'CREATE ROLE carmunity_migrator LOGIN;' \
      "기존 Migrator role을 다시 생성하면 안 됩니다."
    assert_excludes "${sql_log}" '\password carmunity_migrator' \
      "기존 Migrator role의 password를 바꾸면 안 됩니다."
  else
    assert_contains "${sql_log}" 'CREATE ROLE carmunity_migrator LOGIN;' \
      "없는 Migrator role을 생성해야 합니다."
    assert_contains "${sql_log}" '\password carmunity_migrator' \
      "새 Migrator role의 password를 설정해야 합니다."
  fi

  assert_contains "${sql_log}" 'GRANT USAGE ON SCHEMA public TO carmunity_runtime' \
    "Runtime schema 사용 권한이 없습니다."
  assert_contains "${sql_log}" 'GRANT USAGE, CREATE ON SCHEMA public TO carmunity_migrator' \
    "Migrator schema 변경 권한이 없습니다."
  assert_contains "${sql_log}" 'REVOKE CREATE ON SCHEMA public FROM PUBLIC' \
    "일반 role의 schema 생성 권한을 닫지 않았습니다."
  assert_excludes "${argument_log}" "${secret}" \
    "DB password를 psql argument에 넣으면 안 됩니다."
  assert_excludes "${TARGET_SCRIPT}" "PASSWORD :'" \
    "Plaintext password를 server SQL statement로 보내면 안 됩니다."
)

main() (
  local test_root
  test_root="$(mktemp -d "${TMPDIR:-/tmp}/car-community-db-bootstrap-test.XXXXXX")"
  trap 'rm -rf "${test_root}"' EXIT

  test_uses_existing_postgresql_16_client_without_installing
  test_installs_postgresql_16_client_when_missing "${test_root}"
  test_rejects_non_postgresql_16_client
  test_reads_exact_three_nul_fields_and_uses_fixed_database_url
  test_rejects_missing_or_trailing_input
  test_rejects_reused_passwords
  test_usage_does_not_request_fixed_database_url "${test_root}"
  test_creates_only_missing_roles_and_applies_permissions "${test_root}" neither_exists false false
  test_creates_only_missing_roles_and_applies_permissions "${test_root}" runtime_exists true false
  test_creates_only_missing_roles_and_applies_permissions "${test_root}" migrator_exists false true
  test_creates_only_missing_roles_and_applies_permissions "${test_root}" both_exist true true

  echo 'PASS: 100-provision-database-access.sh contract tests'
)

main
