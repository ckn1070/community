#!/usr/bin/env bash

# Private ECR publish workflow의 신뢰 경계와 immutable image 계약을 검증합니다.
set -euo pipefail

TEST_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly TEST_DIRECTORY
readonly CI_WORKFLOW_PATH="${TEST_DIRECTORY}/../../../.github/workflows/ci.yml"
readonly WORKFLOW_PATH="${TEST_DIRECTORY}/../../../.github/workflows/publish-image.yml"
readonly CLASSIFIER_PATH="${TEST_DIRECTORY}/../classify-image-publishing-change.sh"
readonly VALIDATOR_PATH="${TEST_DIRECTORY}/../validate-image-publishing-decision.sh"
readonly ZERO_SHA="0000000000000000000000000000000000000000"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

require_text() {
  local expected_text="$1"
  local message="$2"

  if ! grep -Fq -- "${expected_text}" "${WORKFLOW_PATH}"; then
    fail "${message}: ${expected_text}"
  fi
}

require_ci_text() {
  local expected_text="$1"
  local message="$2"

  if ! grep -Fq -- "${expected_text}" "${CI_WORKFLOW_PATH}"; then
    fail "${message}: ${expected_text}"
  fi
}

require_classify_job_text() {
  local expected_text="$1"
  local message="$2"
  local classify_job

  classify_job="$(sed -n '/^  classify:/,/^  publish:/p' "${WORKFLOW_PATH}")"
  if ! grep -Fxq -- "    ${expected_text}" <<< "${classify_job}"; then
    fail "${message}: ${expected_text}"
  fi
}

require_publish_job_text() {
  local expected_text="$1"
  local message="$2"
  local publish_job

  publish_job="$(sed -n '/^  publish:/,$p' "${WORKFLOW_PATH}")"
  if ! grep -Fxq -- "    ${expected_text}" <<< "${publish_job}"; then
    fail "${message}: ${expected_text}"
  fi
}

reject_text() {
  local unexpected_text="$1"
  local message="$2"

  if grep -Fq -- "${unexpected_text}" "${WORKFLOW_PATH}"; then
    fail "${message}: ${unexpected_text}"
  fi
}

assert_decision() {
  local decision_path="$1"
  local expected_sha="$2"
  local expected_attempt="$3"
  local expected_publish="$4"
  local expected

  expected="$(printf 'head_sha=%s\nrun_attempt=%s\npublish=%s' \
    "${expected_sha}" "${expected_attempt}" "${expected_publish}")"
  if [[ "$(cat "${decision_path}")" != "${expected}" ]]; then
    fail "분류 결과가 기대한 exact payload와 다릅니다.: ${decision_path}"
  fi
}

assert_classification() {
  local repository_path="$1"
  local before_sha="$2"
  local after_sha="$3"
  local run_attempt="$4"
  local expected_publish="$5"
  local decision_path="$6"

  (
    cd "${repository_path}"
    "${CLASSIFIER_PATH}" "${before_sha}" "${after_sha}" "${run_attempt}" "${decision_path}"
  )
  assert_decision "${decision_path}" "${after_sha}" "${run_attempt}" "${expected_publish}"
}

assert_command_fails() {
  if "$@" >/dev/null 2>&1; then
    fail "실패해야 하는 명령이 성공했습니다.: $*"
  fi
}

commit_all() {
  local repository_path="$1"
  local message="$2"

  git -C "${repository_path}" add --all
  git -C "${repository_path}" commit --quiet -m "${message}"
  git -C "${repository_path}" rev-parse HEAD
}

test_classifier_behavior() {
  local test_root="$1"
  local repository_path="${test_root}/repository"
  local source_root_repository="${test_root}/source-root-repository"
  local decision_path="${test_root}/decision.env"
  local root_deploy_sha root_source_sha source_sha deploy_sha range_start_sha range_end_sha
  local source_to_deploy_sha deploy_rename_sha merge_parent_sha merge_sha

  git init --quiet --initial-branch=main "${repository_path}"
  git -C "${repository_path}" config user.email test@example.com
  git -C "${repository_path}" config user.name "Workflow Contract Test"

  mkdir -p "${repository_path}/deploy/overlays/stg"
  printf 'digest: one\n' >"${repository_path}/deploy/overlays/stg/image.yaml"
  root_deploy_sha="$(commit_all "${repository_path}" "root deploy")"
  assert_classification "${repository_path}" "${ZERO_SHA}" "${root_deploy_sha}" 1 false "${decision_path}"

  git init --quiet --initial-branch=main "${source_root_repository}"
  git -C "${source_root_repository}" config user.email test@example.com
  git -C "${source_root_repository}" config user.name "Workflow Contract Test"
  mkdir -p "${source_root_repository}/src"
  printf 'root source\n' >"${source_root_repository}/src/application.txt"
  root_source_sha="$(commit_all "${source_root_repository}" "root source")"
  assert_classification "${source_root_repository}" "${ZERO_SHA}" "${root_source_sha}" 1 true "${decision_path}"

  mkdir -p "${repository_path}/src"
  printf 'source one\n' >"${repository_path}/src/application.txt"
  source_sha="$(commit_all "${repository_path}" "source")"
  assert_classification "${repository_path}" "${root_deploy_sha}" "${source_sha}" 2 true "${decision_path}"

  printf 'digest: two\n' >"${repository_path}/deploy/overlays/stg/image.yaml"
  deploy_sha="$(commit_all "${repository_path}" "deploy only")"
  assert_classification "${repository_path}" "${source_sha}" "${deploy_sha}" 3 false "${decision_path}"
  assert_classification "${repository_path}" "${deploy_sha}" "${deploy_sha}" 4 false "${decision_path}"

  range_start_sha="${deploy_sha}"
  printf 'source two\n' >>"${repository_path}/src/application.txt"
  commit_all "${repository_path}" "rebase source" >/dev/null
  printf 'digest: three\n' >"${repository_path}/deploy/overlays/stg/image.yaml"
  range_end_sha="$(commit_all "${repository_path}" "rebase deploy tail")"
  assert_classification "${repository_path}" "${range_start_sha}" "${range_end_sha}" 5 true "${decision_path}"

  git -C "${repository_path}" mv src/application.txt deploy/overlays/stg/application.txt
  source_to_deploy_sha="$(commit_all "${repository_path}" "move source into deploy")"
  assert_classification "${repository_path}" "${range_end_sha}" "${source_to_deploy_sha}" 6 true "${decision_path}"

  git -C "${repository_path}" mv \
    deploy/overlays/stg/application.txt deploy/overlays/stg/application-renamed.txt
  deploy_rename_sha="$(commit_all "${repository_path}" "rename inside deploy")"
  assert_classification "${repository_path}" "${source_to_deploy_sha}" "${deploy_rename_sha}" 7 false "${decision_path}"

  git -C "${repository_path}" switch --quiet -c feature
  printf 'feature source\n' >"${repository_path}/src/feature.txt"
  commit_all "${repository_path}" "feature source" >/dev/null
  git -C "${repository_path}" switch --quiet main
  printf 'digest: four\n' >"${repository_path}/deploy/overlays/stg/image.yaml"
  merge_parent_sha="$(commit_all "${repository_path}" "main deploy")"
  git -C "${repository_path}" merge --quiet --no-ff feature -m "merge feature"
  merge_sha="$(git -C "${repository_path}" rev-parse HEAD)"
  assert_classification "${repository_path}" "${merge_parent_sha}" "${merge_sha}" 8 true "${decision_path}"

  assert_command_fails \
    "${CLASSIFIER_PATH}" "${merge_parent_sha}" "${merge_sha}" 0 "${decision_path}"
}

test_decision_validator_behavior() {
  local test_root="$1"
  local decision_path="${test_root}/validator-decision.env"
  local output_path="${test_root}/github-output"
  local expected_sha="0123456789abcdef0123456789abcdef01234567"

  printf 'head_sha=%s\nrun_attempt=9\npublish=false\n' "${expected_sha}" >"${decision_path}"
  "${VALIDATOR_PATH}" "${expected_sha}" 9 "${decision_path}" "${output_path}"
  grep -Fqx -- 'publish=false' "${output_path}" || fail "검증한 publish 값을 output으로 전달해야 합니다."

  printf 'head_sha=%s\nrun_attempt=8\npublish=false\n' "${expected_sha}" >"${decision_path}"
  assert_command_fails \
    "${VALIDATOR_PATH}" "${expected_sha}" 9 "${decision_path}" "${output_path}"

  printf 'head_sha=ffffffffffffffffffffffffffffffffffffffff\nrun_attempt=9\npublish=false\n' >"${decision_path}"
  assert_command_fails \
    "${VALIDATOR_PATH}" "${expected_sha}" 9 "${decision_path}" "${output_path}"

  printf 'head_sha=%s\nrun_attempt=9\npublish=false\nextra=true\n' "${expected_sha}" >"${decision_path}"
  assert_command_fails \
    "${VALIDATOR_PATH}" "${expected_sha}" 9 "${decision_path}" "${output_path}"

  printf 'head_sha=%s\nrun_attempt=9\npublish=false\nextra-without-newline' "${expected_sha}" >"${decision_path}"
  assert_command_fails \
    "${VALIDATOR_PATH}" "${expected_sha}" 9 "${decision_path}" "${output_path}"

  ln -s "${decision_path}" "${test_root}/decision-link.env"
  assert_command_fails \
    "${VALIDATOR_PATH}" "${expected_sha}" 9 "${test_root}/decision-link.env" "${output_path}"
}

run_tests() (
  require_text "workflow_run:" "성공한 CI workflow를 publish trigger로 사용해야 합니다."
  require_text "github.event.workflow_run.conclusion == 'success'" "실패한 CI를 차단해야 합니다."
  require_text "github.event.workflow_run.event == 'push'" "PR workflow_run을 차단해야 합니다."
  require_text "github.event.workflow_run.head_branch == 'main'" "main branch만 허용해야 합니다."
  require_text "github.event.workflow_run.head_repository.full_name == github.repository" "fork source를 차단해야 합니다."
  require_ci_text "BEFORE_SHA: \${{ github.event.before }}" "CI push payload의 전체 변경 시작 SHA를 사용해야 합니다."
  require_ci_text "AFTER_SHA: \${{ github.sha }}" "CI가 검증한 push의 exact 최종 SHA를 사용해야 합니다."
  require_ci_text "RUN_ATTEMPT: \${{ github.run_attempt }}" "CI 재실행 attempt를 분류 결과에 결속해야 합니다."
  require_ci_text "./scripts/quality/classify-image-publishing-change.sh" "실행 가능한 classifier를 CI에서 사용해야 합니다."
  require_ci_text "actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a" "검증한 분류 결과를 고정 action으로 전달해야 합니다."
  require_ci_text "name: publish-image-change-\${{ github.sha }}-\${{ github.run_attempt }}" "분류 artifact를 exact CI SHA와 attempt에 결속해야 합니다."
  require_ci_text "retention-days: 30" "지원하는 CI 재실행 기간 동안 분류 artifact를 보존해야 합니다."
  require_text "classify:" "AWS 권한을 사용하기 전에 변경 경로를 분류해야 합니다."
  require_text "actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c" "성공한 CI의 분류 artifact를 고정 action으로 받아야 합니다."
  require_text "run-id: \${{ github.event.workflow_run.id }}" "Trigger한 exact CI run의 artifact만 받아야 합니다."
  require_text "name: publish-image-change-\${{ github.event.workflow_run.head_sha }}-\${{ github.event.workflow_run.run_attempt }}" "Artifact 이름을 성공한 CI SHA와 attempt에 결속해야 합니다."
  require_text "./scripts/quality/validate-image-publishing-decision.sh" "다운로드한 분류 payload를 실행 가능한 validator로 검사해야 합니다."
  require_text "needs: classify" "Publish job은 변경 경로 분류 결과에 의존해야 합니다."
  require_text "needs.classify.outputs.publish == 'true'" "deploy 전용 변경에는 AWS publish job을 차단해야 합니다."
  require_text "id-token: write" "GitHub OIDC token 발급 권한이 필요합니다."
  require_text "contents: read" "Source checkout 권한을 read로 제한해야 합니다."
  require_text "ref: \${{ github.event.workflow_run.head_sha }}" "성공한 exact CI SHA를 checkout해야 합니다."
  require_classify_job_text "runs-on: ubuntu-24.04" "Architecture와 무관한 분류는 일반 runner에서 실행해야 합니다."
  require_publish_job_text "runs-on: ubuntu-24.04-arm" "ARM64 image는 native ARM64 runner에서 build해야 합니다."
  require_text "docker/setup-buildx-action@bb05f3f5519dd87d3ba754cc423b652a5edd6d2c" "Native BuildKit builder를 고정 Buildx action으로 준비해야 합니다."
  require_text "docker/build-push-action@53b7df96c91f9c12dcc8a07bcb9ccacbed38856a" "Buildx build·push 결과 digest를 고정 action에서 받아야 합니다."
  require_text "platforms: linux/arm64" "현재 ARM64 node용 image만 build해야 합니다."
  require_text "provenance: false" "Runtime manifest digest와 kubelet imageID의 직접 비교를 유지해야 합니다."
  require_text "ECR_REPOSITORY: car-community" "고정 private ECR repository를 사용해야 합니다."
  require_text "--image-ids \"imageTag=\${IMAGE_TAG}\"" "같은 immutable SHA 재실행을 조회해야 합니다."
  require_text "REVISION=\${{ env.IMAGE_TAG }}" "Dockerfile OCI revision에 exact SHA를 전달해야 합니다."
  reject_text "pull_request_target" "Untrusted PR source를 privileged trigger로 사용하면 안 됩니다."
  reject_text "git rev-parse \"\${IMAGE_TAG}^1\"" "마지막 commit 하나만 분류하면 multi-commit push를 누락합니다."
  reject_text "AWS_ACCESS_KEY_ID" "장기 AWS access key를 workflow에 사용하면 안 됩니다."
  reject_text "docker/setup-qemu-action" "Native ARM64 build에 QEMU emulation을 추가하면 안 됩니다."
  reject_text ":latest" "Mutable latest tag를 게시하면 안 됩니다."

  local test_root
  test_root="$(mktemp -d)"
  trap 'rm -rf -- "${test_root}"' EXIT
  test_classifier_behavior "${test_root}"
  test_decision_validator_behavior "${test_root}"

  echo "PASS: publish-image.yml contract tests"
)

run_tests
