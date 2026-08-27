#!/usr/bin/env bash

# Staging application이 사용할 PostgreSQL role과 schema 권한을 최초 구성합니다.
set -euo pipefail
set +x

readonly RDS_MASTER_USERNAME="carmunity_admin"
readonly RUNTIME_ROLE="carmunity_runtime"
readonly MIGRATOR_ROLE="carmunity_migrator"
readonly POSTGRESQL_CLIENT_PACKAGE="postgresql-client-16"
readonly POSTGRESQL_CLIENT_MAJOR_VERSION="16"
readonly RDS_CA_URL="https://truststore.pki.rds.amazonaws.com/ap-northeast-2/ap-northeast-2-bundle.pem"
readonly RDS_CA_SHA256="913fb5b814f17af79d4c1622584a8d0ceddf5b0d76fe353d0c7d1186cdd6b229"
readonly RDS_CA_MOUNT_PATH="/etc/car-community/rds-ca/ap-northeast-2-bundle.pem"
readonly DATABASE_URL="jdbc:postgresql://stg-car-community-postgres.cjemi62mqsl6.ap-northeast-2.rds.amazonaws.com:5432/carmunity?currentSchema=public&sslmode=verify-full&sslrootcert=/etc/car-community/rds-ca/ap-northeast-2-bundle.pem"

DATABASE_MASTER_PASSWORD=""
RUNTIME_PASSWORD=""
MIGRATOR_PASSWORD=""
RDS_CA_PATH=""
PSQL_URL=""
export -n DATABASE_MASTER_PASSWORD RUNTIME_PASSWORD MIGRATOR_PASSWORD

fail() {
  echo "오류: $*" >&2
  exit 1
}

print_usage() {
  cat <<'EOF'
사용법:
  printf '%s\0' \
    "${CURRENT_RDS_MASTER_PASSWORD}" \
    "${STG_DB_RUNTIME_PASSWORD}" \
    "${STG_DB_MIGRATOR_PASSWORD}" \
  | env -i HOME="${HOME}" PATH="${PATH}" ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
    'sudo -n /home/ubuntu/car-community-scripts/bootstrap/application/100-provision-database-access.sh'

정확히 세 개의 NUL-delimited password field만 받습니다. Staging DB URL과 role 이름은
script에 고정합니다. PostgreSQL 16 client가 없으면 설치하고,
두 application role 중 없는 role만 생성합니다. 기존 role의 password는 변경하지 않습니다.
EOF
}

read_nul_field() {
  local variable_name="$1"
  local field_name="$2"

  IFS= read -r -d '' "${variable_name}" || fail "${field_name} field가 없거나 NUL로 끝나지 않습니다."
}

read_fixed_input() {
  local trailing_byte

  read_nul_field DATABASE_MASTER_PASSWORD CURRENT_RDS_MASTER_PASSWORD
  read_nul_field RUNTIME_PASSWORD STG_DB_RUNTIME_PASSWORD
  read_nul_field MIGRATOR_PASSWORD STG_DB_MIGRATOR_PASSWORD
  if IFS= read -r -n 1 trailing_byte; then
    : "${trailing_byte}"
    fail "마지막 field 뒤에 추가 byte가 있습니다."
  fi
}

require_nonblank_password() {
  local value="$1"
  local field_name="$2"

  if [[ -z "${value}" || "${value}" =~ ^[[:space:]]+$ || "${value}" == *$'\n'* || "${value}" == *$'\r'* ]]; then
    fail "${field_name}는 줄바꿈 없는 non-blank 값이어야 합니다."
  fi
}

validate_inputs() {
  require_nonblank_password "${DATABASE_MASTER_PASSWORD}" CURRENT_RDS_MASTER_PASSWORD
  require_nonblank_password "${RUNTIME_PASSWORD}" STG_DB_RUNTIME_PASSWORD
  require_nonblank_password "${MIGRATOR_PASSWORD}" STG_DB_MIGRATOR_PASSWORD
  if [[ "${DATABASE_MASTER_PASSWORD}" == "${RUNTIME_PASSWORD}" \
      || "${DATABASE_MASTER_PASSWORD}" == "${MIGRATOR_PASSWORD}" \
      || "${RUNTIME_PASSWORD}" == "${MIGRATOR_PASSWORD}" ]]; then
    fail "Master·runtime·migrator password는 서로 달라야 합니다."
  fi
}

require_commands() {
  local command_name

  for command_name in cat curl mktemp sha256sum; do
    command -v "${command_name}" >/dev/null 2>&1 \
      || fail "필수 command를 찾을 수 없습니다: ${command_name}"
  done
}

ensure_postgresql_client() {
  local version_output=""

  if command -v psql >/dev/null 2>&1; then
    echo "[변경 없음] PostgreSQL client가 이미 설치되어 있습니다."
  else
    command -v apt-get >/dev/null 2>&1 || fail "apt-get command를 찾을 수 없습니다."
    echo "[변경] PostgreSQL 16 client를 설치합니다."
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install \
      --yes \
      --no-install-recommends \
      "${POSTGRESQL_CLIENT_PACKAGE}"
  fi

  version_output="$(psql --version)" || fail "PostgreSQL client version을 확인하지 못했습니다."
  [[ "${version_output}" =~ ^psql\ \(PostgreSQL\)\ ${POSTGRESQL_CLIENT_MAJOR_VERSION}([.][0-9]+)+([[:space:]]|$) ]] \
    || fail "PostgreSQL ${POSTGRESQL_CLIENT_MAJOR_VERSION} client가 아닙니다: ${version_output}"
}

prepare_rds_ca() {
  local work_directory="$1"

  RDS_CA_PATH="${work_directory}/ap-northeast-2-bundle.pem"
  curl --fail --silent --show-error --location \
    --output "${RDS_CA_PATH}" "${RDS_CA_URL}"
  printf '%s  %s\n' "${RDS_CA_SHA256}" "${RDS_CA_PATH}" \
    | sha256sum --check --status \
    || fail "Seoul RDS CA bundle checksum이 일치하지 않습니다."
}

prepare_psql_url() {
  PSQL_URL="${DATABASE_URL#jdbc:}"
  PSQL_URL="${PSQL_URL/currentSchema=public&/}"
  PSQL_URL="${PSQL_URL/&sslrootcert=${RDS_CA_MOUNT_PATH}/}"
}

run_master_psql() {
  PGPASSWORD="${DATABASE_MASTER_PASSWORD}" \
    PGSSLROOTCERT="${RDS_CA_PATH}" \
    psql "${PSQL_URL}" \
      --username="${RDS_MASTER_USERNAME}" \
      --no-psqlrc \
      --set=ON_ERROR_STOP=on \
      "$@"
}

database_role_exists() {
  local role_name="$1"
  local exists=""

  case "${role_name}" in
    "${RUNTIME_ROLE}"|"${MIGRATOR_ROLE}") ;;
    *) fail "확인할 수 없는 application DB role입니다: ${role_name}" ;;
  esac

  exists="$(run_master_psql \
    --tuples-only \
    --no-align \
    --command="SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${role_name}');")" \
    || fail "Application DB role 존재 여부를 확인하지 못했습니다: ${role_name}"
  case "${exists}" in
    t) return 0 ;;
    f) return 1 ;;
    *) fail "Application DB role 존재 여부 응답이 올바르지 않습니다: ${role_name}" ;;
  esac
}

provision_database_access() {
  local create_runtime=false
  local create_migrator=false

  if database_role_exists "${RUNTIME_ROLE}"; then
    echo "[변경 없음] 기존 ${RUNTIME_ROLE} role과 password를 유지합니다."
  else
    create_runtime=true
  fi
  if database_role_exists "${MIGRATOR_ROLE}"; then
    echo "[변경 없음] 기존 ${MIGRATOR_ROLE} role과 password를 유지합니다."
  else
    create_migrator=true
  fi

  {
    printf '%s\n' 'BEGIN;'

    if [[ "${create_runtime}" == true ]]; then
      cat <<'SQL'
CREATE ROLE carmunity_runtime LOGIN;

\password carmunity_runtime
SQL
      printf '%s\n%s\n' "${RUNTIME_PASSWORD}" "${RUNTIME_PASSWORD}"
    fi
    if [[ "${create_migrator}" == true ]]; then
      cat <<'SQL'
CREATE ROLE carmunity_migrator LOGIN;

\password carmunity_migrator
SQL
      printf '%s\n%s\n' "${MIGRATOR_PASSWORD}" "${MIGRATOR_PASSWORD}"
    fi

    cat <<'SQL'
GRANT CONNECT ON DATABASE carmunity TO carmunity_runtime;
GRANT CONNECT ON DATABASE carmunity TO carmunity_migrator;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL PRIVILEGES ON SCHEMA public FROM carmunity_runtime;
REVOKE ALL PRIVILEGES ON SCHEMA public FROM carmunity_migrator;
GRANT USAGE ON SCHEMA public TO carmunity_runtime;
GRANT USAGE, CREATE ON SCHEMA public TO carmunity_migrator;

COMMIT;
SQL
  } | run_master_psql

  database_role_exists "${RUNTIME_ROLE}" \
    || fail "Runtime role 생성 결과를 확인하지 못했습니다."
  database_role_exists "${MIGRATOR_ROLE}" \
    || fail "Migrator role 생성 결과를 확인하지 못했습니다."
}

clear_sensitive_values() {
  DATABASE_MASTER_PASSWORD=""
  RUNTIME_PASSWORD=""
  MIGRATOR_PASSWORD=""
  unset DATABASE_MASTER_PASSWORD RUNTIME_PASSWORD MIGRATOR_PASSWORD
}

main() (
  local work_directory=""

  trap 'clear_sensitive_values; [[ -z "${work_directory}" ]] || rm -rf "${work_directory}"' EXIT
  if [[ "$#" -eq 1 && ( "$1" == "--help" || "$1" == "-h" ) ]]; then
    print_usage
    return
  fi
  [[ "$#" -eq 0 ]] || fail "허용되지 않은 CLI argument입니다."

  read_fixed_input
  validate_inputs
  require_commands
  ensure_postgresql_client
  work_directory="$(mktemp -d "${TMPDIR:-/tmp}/car-community-db-bootstrap.XXXXXX")"
  chmod 0700 "${work_directory}"
  prepare_rds_ca "${work_directory}"
  prepare_psql_url

  echo "[적용] 없는 Runtime·migrator role을 생성하고 최소 schema 권한을 적용합니다."
  provision_database_access
  echo "[완료] Runtime·migrator role 생성과 권한 분리가 끝났습니다."
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
