#!/usr/bin/env bash

# 성공한 exact CI run·attempt가 만든 image 게시 결정만 privileged workflow에 전달합니다.
set -euo pipefail

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

main() {
  if [[ "$#" -ne 4 ]]; then
    fail "사용법: $0 <expected-sha> <expected-attempt> <decision-path> <output-path>"
  fi

  local expected_sha="$1"
  local expected_attempt="$2"
  local decision_path="$3"
  local output_path="$4"
  local head_line attempt_line publish_line extra_line publish

  [[ "${expected_sha}" =~ ^[0-9a-f]{40}$ ]] || fail "expected SHA 형식이 올바르지 않습니다."
  [[ "${expected_attempt}" =~ ^[1-9][0-9]*$ ]] || fail "expected attempt는 양의 정수여야 합니다."
  [[ -f "${decision_path}" && ! -L "${decision_path}" ]] || fail "분류 payload는 일반 파일이어야 합니다."
  [[ -n "${output_path}" ]] || fail "output path가 비어 있습니다."

  exec 3<"${decision_path}"
  IFS= read -r head_line <&3 || fail "분류 payload의 head SHA line이 없습니다."
  IFS= read -r attempt_line <&3 || fail "분류 payload의 run attempt line이 없습니다."
  IFS= read -r publish_line <&3 || fail "분류 payload의 publish line이 없습니다."
  extra_line=""
  if IFS= read -r extra_line <&3 || [[ -n "${extra_line}" ]]; then
    fail "분류 payload에는 세 line만 허용됩니다."
  fi

  [[ "${head_line}" == "head_sha=${expected_sha}" ]] || fail "분류 payload의 head SHA가 다릅니다."
  [[ "${attempt_line}" == "run_attempt=${expected_attempt}" ]] || fail "분류 payload의 run attempt가 다릅니다."
  [[ "${publish_line}" =~ ^publish=(true|false)$ ]] || fail "분류 payload의 publish 값이 올바르지 않습니다."

  publish="${publish_line#publish=}"
  printf 'publish=%s\n' "${publish}" >>"${output_path}"
}

main "$@"
