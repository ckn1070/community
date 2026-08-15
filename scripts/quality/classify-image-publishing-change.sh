#!/usr/bin/env bash

# 한 main push의 전체 Git 변경 범위를 image 게시 여부와 exact CI attempt에 결속합니다.
set -euo pipefail

readonly ZERO_SHA="0000000000000000000000000000000000000000"
TEMP_CHANGED_PATHS=""

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "필수 명령을 찾을 수 없습니다.: $1"
}

cleanup() {
  if [[ -n "${TEMP_CHANGED_PATHS}" ]]; then
    rm -f -- "${TEMP_CHANGED_PATHS}"
  fi
}

main() {
  if [[ "$#" -ne 4 ]]; then
    fail "사용법: $0 <before-sha> <after-sha> <run-attempt> <decision-path>"
  fi

  local before_sha="$1"
  local after_sha="$2"
  local run_attempt="$3"
  local decision_path="$4"
  local base_object changed_path publish decision_directory

  require_command dirname
  require_command git
  require_command mkdir
  require_command mktemp
  require_command rm

  [[ "${before_sha}" =~ ^[0-9a-f]{40}$ ]] || fail "before SHA 형식이 올바르지 않습니다."
  [[ "${after_sha}" =~ ^[0-9a-f]{40}$ ]] || fail "after SHA 형식이 올바르지 않습니다."
  [[ "${run_attempt}" =~ ^[1-9][0-9]*$ ]] || fail "run attempt는 양의 정수여야 합니다."
  [[ -n "${decision_path}" ]] || fail "decision path가 비어 있습니다."

  git cat-file -e "${after_sha}^{commit}"
  if [[ "${before_sha}" == "${ZERO_SHA}" ]]; then
    base_object="$(git hash-object -t tree /dev/null)"
  else
    git cat-file -e "${before_sha}^{commit}"
    base_object="${before_sha}"
  fi

  TEMP_CHANGED_PATHS="$(mktemp "${TMPDIR:-/tmp}/car-community-image-paths.XXXXXX")"
  trap cleanup EXIT
  git diff --no-renames --name-only -z "${base_object}" "${after_sha}" -- >"${TEMP_CHANGED_PATHS}"

  publish=false
  while IFS= read -r -d '' changed_path; do
    if [[ "${changed_path}" != deploy/* ]]; then
      publish=true
      break
    fi
  done <"${TEMP_CHANGED_PATHS}"

  decision_directory="$(dirname -- "${decision_path}")"
  mkdir -p -- "${decision_directory}"
  {
    printf 'head_sha=%s\n' "${after_sha}"
    printf 'run_attempt=%s\n' "${run_attempt}"
    printf 'publish=%s\n' "${publish}"
  } >"${decision_path}"
}

main "$@"
