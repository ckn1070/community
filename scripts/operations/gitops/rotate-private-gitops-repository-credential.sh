#!/usr/bin/env bash

# 이미 연결된 현재 private repository의 GitHub App credential만 교체합니다.
set -euo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIRECTORY
readonly INITIAL_CONNECTION_SCRIPT="${SCRIPT_DIRECTORY}/../../bootstrap/gitops/200-connect-private-gitops-repository.sh"

# shellcheck source=../../bootstrap/gitops/200-connect-private-gitops-repository.sh
source "${INITIAL_CONNECTION_SCRIPT}"

print_usage() {
  cat <<'EOF'
사용법:
  sudo /home/ubuntu/car-community-scripts/operations/gitops/rotate-private-gitops-repository-credential.sh \
    <GITHUB_APP_ID> <GITHUB_APP_INSTALLATION_ID> \
    < /local/path/to/github-app-private-key.pem

최초 repository 연결이 완료된 cluster에서만 사용합니다. GitHub App private key는
positional argument나 environment variable로 받지 않고 stdin으로만 읽습니다.
EOF
}

run_rotation_preflight_checks() {
  echo "[사전 확인] Argo CD Core와 기존 GitOps resource owner를 확인합니다."
  require_root
  require_commands
  acquire_execution_lock
  require_ready_argocd_core
  require_managed_existing "secret/${REPOSITORY_SECRET_NAME}"
  require_managed_existing "appproject/${PROJECT_NAME}"
  require_managed_existing "application/${APPLICATION_NAME}"
}

main() {
  local github_app_id="${1:-}"
  local github_app_installation_id="${2:-}"
  local work_directory=""
  local private_key_path
  local previous_reconciled_at

  parse_arguments "$@"
  run_rotation_preflight_checks
  previous_reconciled_at="$(current_application_reconciled_at)"
  work_directory="$(create_secret_work_directory)"
  trap 'cleanup_secret_work_directory "${work_directory:-}"' EXIT
  private_key_path="${work_directory}/github-app-private-key.pem"

  echo "[입력] 새 GitHub App private key를 stdin에서 임시 runtime 경로로 읽습니다."
  read_and_verify_private_key "${private_key_path}"
  echo "[적용] 기존 repository credential을 새 key로 교체합니다."
  apply_repository_secret "${github_app_id}" "${github_app_installation_id}" "${private_key_path}"
  rm -f -- "${private_key_path}"
  echo "[대기] 새 repository credential을 읽는 application-controller rollout을 확인합니다."
  restart_application_controller_for_current_secret "${work_directory}"
  echo "[대기] 새 credential을 사용한 reconcile 완료를 확인합니다."
  force_and_wait_for_fresh_reconciliation "${previous_reconciled_at}"
  echo "[대기] GitOps 동기화와 workload health를 확인합니다."
  wait_for_application
  verify_postconditions
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
