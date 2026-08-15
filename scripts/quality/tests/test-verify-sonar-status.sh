#!/usr/bin/env bash

# Sonar Web API 응답 fixture로 PASS·FAIL·NOT VERIFIED의 판정 경계와 분석 identity 재확인을 검증합니다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERIFY_SCRIPT="${SCRIPT_DIR}/../verify-sonar-status.sh"
EXPECTED_REVISION="0123456789abcdef0123456789abcdef01234567"

# shellcheck source=scripts/quality/verify-sonar-status.sh
source "${VERIFY_SCRIPT}"

# 인자로 받은 analysis key·revision과 고정 시각을 포함하는 최신 분석 API fixture를 생성합니다.
analysis_json() {
  local key="$1"
  local revision="$2"

  jq -cn \
    --arg key "${key}" \
    --arg revision "${revision}" \
    '{analyses: [{key: $key, revision: $revision, date: "2026-07-23T06:10:00+0000"}]}'
}

# 필수 Gate·Open issue와 참고 metric이 함께 있는 component 응답을 생성합니다.
measures_json() {
  local gate="$1"
  local open_issues="$2"
  local include_open_issues="${3:-true}"

  jq -cn \
    --arg gate "${gate}" \
    --arg open_issues "${open_issues}" \
    --argjson include_open_issues "${include_open_issues}" \
    '{
      component: {
        measures: (
          [
            {metric: "alert_status", value: $gate},
            {metric: "coverage", value: "86.8"},
            {metric: "new_coverage", period: {value: "91.2"}},
            {metric: "duplicated_lines_density", value: "1.4"},
            {metric: "new_duplicated_lines_density", period: {value: "0.0"}},
            {metric: "security_hotspots_reviewed", value: "100"},
            {metric: "new_security_hotspots_reviewed", period: {value: "100"}}
          ]
          + if $include_open_issues
            then [{metric: "open_issues", value: $open_issues}]
            else []
            end
        )
      }
    }'
}

# 한 판정 사례의 exit code와 핵심 진단 문구를 함께 고정합니다.
assert_case() {
  local name="$1"
  local expected_status="$2"
  local expected_text="$3"
  local before="$4"
  local measures="$5"
  local after="$6"
  local expected_revision="$7"
  local output
  local actual_status

  set +e
  output="$(
    evaluate_sonar_status \
      "${before}" \
      "${measures}" \
      "${after}" \
      "${expected_revision}" 2>&1
  )"
  actual_status="$?"
  set -e

  if [[ "${actual_status}" -ne "${expected_status}" ]]; then
    echo "FAIL ${name}: expected exit ${expected_status}, got ${actual_status}" >&2
    echo "${output}" >&2
    exit 1
  fi

  if ! grep -Fq "${expected_text}" <<<"${output}"; then
    echo "FAIL ${name}: output does not contain '${expected_text}'" >&2
    echo "${output}" >&2
    exit 1
  fi

  echo "PASS ${name}"
}

# PASS, Quality Gate 실패, Open issue, revision 불일치, 조회 중 변경과 응답 결손을 독립 사례로 검증합니다.
stable_analysis="$(analysis_json "analysis-1" "${EXPECTED_REVISION}")"

assert_case \
  "exact revision and clean project" \
  0 \
  "Result                PASS" \
  "${stable_analysis}" \
  "$(measures_json "OK" "0")" \
  "${stable_analysis}" \
  "${EXPECTED_REVISION}"

assert_case \
  "quality gate failure" \
  1 \
  "Quality Gate          ERROR" \
  "${stable_analysis}" \
  "$(measures_json "ERROR" "0")" \
  "${stable_analysis}" \
  "${EXPECTED_REVISION}"

assert_case \
  "open issues remain" \
  1 \
  "Open issues           3" \
  "${stable_analysis}" \
  "$(measures_json "OK" "3")" \
  "${stable_analysis}" \
  "${EXPECTED_REVISION}"

assert_case \
  "different analyzed revision" \
  2 \
  "Result                NOT VERIFIED" \
  "$(analysis_json "analysis-1" "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")" \
  "$(measures_json "OK" "0")" \
  "$(analysis_json "analysis-1" "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")" \
  "${EXPECTED_REVISION}"

assert_case \
  "analysis changes during metric lookup" \
  2 \
  "분석이 조회 도중 변경됐습니다" \
  "${stable_analysis}" \
  "$(measures_json "OK" "0")" \
  "$(analysis_json "analysis-2" "${EXPECTED_REVISION}")" \
  "${EXPECTED_REVISION}"

assert_case \
  "required metric is missing" \
  2 \
  "필수 metric을 찾을 수 없습니다" \
  "${stable_analysis}" \
  "$(measures_json "OK" "0" "false")" \
  "${stable_analysis}" \
  "${EXPECTED_REVISION}"

assert_case \
  "latest analysis is missing" \
  2 \
  "최신 Sonar 분석 식별자를 읽을 수 없습니다" \
  '{"analyses":[]}' \
  "$(measures_json "OK" "0")" \
  '{"analyses":[]}' \
  "${EXPECTED_REVISION}"

echo "Sonar status verifier tests passed"
