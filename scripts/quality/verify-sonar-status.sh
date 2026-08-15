#!/usr/bin/env bash

# push가 끝난 exact main revision의 Sonar 분석, Quality Gate와 Open issue를 읽기 전용으로 확인하며 CI 분석 자체는 실행하지 않습니다.
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
사용법: scripts/quality/verify-sonar-status.sh --expected-revision <40자리 Git SHA>

필수 환경변수:
  SONAR_TOKEN        SonarQube Cloud Web API token
  SONAR_PROJECT_KEY  조회할 SonarQube Cloud project key

선택 환경변수:
  SONAR_HOST_URL     기본값: https://sonarcloud.io
EOF
}

not_verified() {
  printf '%-22s%s\n' "Result" "NOT VERIFIED" >&2
  printf '%-22s%s\n' "Reason" "$1" >&2
  return 2
}

# 분석 key·revision·시각을 하나의 identity로 묶어 metric 조회 전후에 같은 분석인지 비교합니다.
analysis_identity() {
  jq -cer '
    if (
      (.analyses | type) == "array"
      and (.analyses | length) > 0
      and (.analyses[0].key | type) == "string"
      and (.analyses[0].revision | type) == "string"
      and (.analyses[0].date | type) == "string"
    )
    then [.analyses[0].key, .analyses[0].revision, .analyses[0].date]
    else error("latest analysis is missing")
    end
  ' <<<"$1" 2>/dev/null
}

metric_value() {
  jq -er --arg metric "$2" '
    [
      .component.measures[]?
      | select(.metric == $metric)
      | (.value // .period.value // empty)
    ][0] // empty
  ' <<<"$1" 2>/dev/null
}

print_optional_metric() {
  local label="$1"
  local value="$2"

  if [[ -n "${value}" ]]; then
    printf '%-22s%s%%\n' "${label}" "${value}"
  else
    printf '%-22s%s\n' "${label}" "N/A"
  fi
}

# revision 결속을 먼저 확인한 뒤 필수 Gate·issue와 참고 metric을 서로 다른 완료 의미로 출력합니다.
evaluate_sonar_status() {
  local before_json="$1"
  local measures_json="$2"
  local after_json="$3"
  local expected_revision
  local before_identity
  local after_identity
  local actual_revision
  local analyzed_at
  local gate
  local open_issues
  local coverage
  local new_coverage
  local duplication
  local new_duplication
  local hotspots_reviewed
  local new_hotspots_reviewed

  expected_revision="$(printf '%s' "$4" | tr '[:upper:]' '[:lower:]')"

  if ! before_identity="$(analysis_identity "${before_json}")"; then
    not_verified "최신 Sonar 분석 식별자를 읽을 수 없습니다."
    return 2
  fi
  if ! after_identity="$(analysis_identity "${after_json}")"; then
    not_verified "metric 조회 뒤 최신 Sonar 분석 식별자를 읽을 수 없습니다."
    return 2
  fi

  if [[ "${before_identity}" != "${after_identity}" ]]; then
    not_verified "Sonar 분석이 조회 도중 변경됐습니다. 완료된 최신 분석에서 다시 실행해 주세요."
    return
  fi

  if ! actual_revision="$(jq -er '.[1]' <<<"${before_identity}" 2>/dev/null)"; then
    not_verified "Sonar analysis revision을 읽을 수 없습니다."
    return 2
  fi
  actual_revision="$(printf '%s' "${actual_revision}" | tr '[:upper:]' '[:lower:]')"
  if ! analyzed_at="$(jq -er '.[2]' <<<"${before_identity}" 2>/dev/null)"; then
    not_verified "Sonar 분석 시각을 읽을 수 없습니다."
    return 2
  fi

  if [[ "${actual_revision}" != "${expected_revision}" ]]; then
    not_verified "Sonar 최신 revision ${actual_revision}이 기대 SHA ${expected_revision}와 다릅니다."
    return
  fi

  if ! gate="$(metric_value "${measures_json}" "alert_status")"; then
    not_verified "필수 metric을 찾을 수 없습니다: alert_status"
    return 2
  fi
  if ! open_issues="$(metric_value "${measures_json}" "open_issues")"; then
    not_verified "필수 metric을 찾을 수 없습니다: open_issues"
    return 2
  fi

  if [[ ! "${open_issues}" =~ ^[0-9]+$ ]]; then
    not_verified "open_issues 값이 0 이상의 정수가 아닙니다: ${open_issues}"
    return
  fi

  coverage="$(metric_value "${measures_json}" "coverage" || true)"
  new_coverage="$(metric_value "${measures_json}" "new_coverage" || true)"
  duplication="$(metric_value "${measures_json}" "duplicated_lines_density" || true)"
  new_duplication="$(metric_value "${measures_json}" "new_duplicated_lines_density" || true)"
  hotspots_reviewed="$(metric_value "${measures_json}" "security_hotspots_reviewed" || true)"
  new_hotspots_reviewed="$(metric_value "${measures_json}" "new_security_hotspots_reviewed" || true)"

  printf '%-22s%s\n' "Sonar branch" "main"
  printf '%-22s%s (exact match)\n' "Sonar revision" "${actual_revision}"
  printf '%-22s%s\n' "Analyzed at" "${analyzed_at}"
  printf '%-22s%s\n' "Quality Gate" "${gate}"
  printf '%-22s%s\n' "Open issues" "${open_issues}"
  print_optional_metric "Coverage" "${coverage}"
  print_optional_metric "New coverage" "${new_coverage}"
  print_optional_metric "Duplication" "${duplication}"
  print_optional_metric "New duplication" "${new_duplication}"
  print_optional_metric "Hotspots reviewed" "${hotspots_reviewed}"
  print_optional_metric "New hotspots reviewed" "${new_hotspots_reviewed}"

  if [[ "${gate}" == "OK" && "${open_issues}" == "0" ]]; then
    printf '%-22s%s\n' "Result" "PASS"
    return 0
  fi

  printf '%-22s%s\n' "Result" "FAIL"
  return 1
}

# token은 Authorization header로만 전달하고 URL·인자·출력에 노출하지 않습니다.
sonar_get() {
  local path="$1"
  shift

  curl \
    --fail \
    --silent \
    --show-error \
    --location \
    --header "Authorization: Bearer ${SONAR_TOKEN}" \
    --get \
    "${SONAR_HOST_URL%/}${path}" \
    "$@"
}

main() {
  local expected_revision=""
  local before_json
  local measures_json
  local after_json
  local encoded_project
  local evaluation_status

  # 입력·credential·필수 도구 오류는 품질 실패가 아니라 판정 불가인 exit 2로 구분합니다.
  case "${1:-}" in
    --help|-h)
      usage
      return 0
      ;;
    --expected-revision)
      [[ "$#" -eq 2 ]] || {
        usage
        return 2
      }
      expected_revision="$2"
      ;;
    *)
      usage
      return 2
      ;;
  esac

  if [[ ! "${expected_revision}" =~ ^[0-9a-fA-F]{40}$ ]]; then
    not_verified "expected revision은 40자리 Git SHA여야 합니다."
    return 2
  fi
  if [[ -z "${SONAR_TOKEN:-}" ]]; then
    not_verified "SONAR_TOKEN이 설정되지 않았습니다."
    return 2
  fi
  if [[ -z "${SONAR_PROJECT_KEY:-}" ]]; then
    not_verified "SONAR_PROJECT_KEY가 설정되지 않았습니다."
    return 2
  fi
  SONAR_HOST_URL="${SONAR_HOST_URL:-https://sonarcloud.io}"

  if ! command -v curl >/dev/null 2>&1; then
    not_verified "필수 명령을 찾을 수 없습니다: curl"
    return 2
  fi
  if ! command -v jq >/dev/null 2>&1; then
    not_verified "필수 명령을 찾을 수 없습니다: jq"
    return 2
  fi

  # metric 조회 사이에 최신 분석이 바뀌면 서로 다른 분석의 값을 섞지 않도록 identity를 앞뒤로 다시 읽습니다.
  if ! before_json="$(
    sonar_get \
      "/api/project_analyses/search" \
      --data-urlencode "project=${SONAR_PROJECT_KEY}" \
      --data-urlencode "branch=main" \
      --data-urlencode "p=1" \
      --data-urlencode "ps=1"
  )"; then
    not_verified "Sonar 최신 분석 조회에 실패했습니다."
    return
  fi

  if ! measures_json="$(
    sonar_get \
      "/api/measures/component" \
      --data-urlencode "component=${SONAR_PROJECT_KEY}" \
      --data-urlencode "branch=main" \
      --data-urlencode "metricKeys=alert_status,open_issues,coverage,new_coverage,duplicated_lines_density,new_duplicated_lines_density,security_hotspots_reviewed,new_security_hotspots_reviewed"
  )"; then
    not_verified "Sonar metric 조회에 실패했습니다."
    return
  fi

  if ! after_json="$(
    sonar_get \
      "/api/project_analyses/search" \
      --data-urlencode "project=${SONAR_PROJECT_KEY}" \
      --data-urlencode "branch=main" \
      --data-urlencode "p=1" \
      --data-urlencode "ps=1"
  )"; then
    not_verified "metric 조회 뒤 Sonar 최신 분석 재확인에 실패했습니다."
    return
  fi

  if evaluate_sonar_status \
    "${before_json}" \
    "${measures_json}" \
    "${after_json}" \
    "${expected_revision}"; then
    return 0
  else
    evaluation_status="$?"
  fi

  if [[ "${evaluation_status}" -eq 1 ]]; then
    encoded_project="$(jq -rn --arg project "${SONAR_PROJECT_KEY}" '$project | @uri')"
    printf '%-22s%s\n' \
      "Dashboard" \
      "${SONAR_HOST_URL%/}/dashboard?id=${encoded_project}" >&2
  fi
  return "${evaluation_status}"
}

# test는 함수를 source해 외부 API 없이 판정 로직만 검증하므로 직접 실행할 때만 Web API 조회를 시작합니다.
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
