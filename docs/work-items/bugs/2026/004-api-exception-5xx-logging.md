# 5xx ApiException 공통 logging 누락

## 상태

Proposed

## 달성할 결과

공통 API 예외 경계가 모든 예상하지 못한 5xx 응답을 민감정보 없이 한 번만 `ERROR`로 기록하고, 예상 가능한 4xx 업무 오류는 stack trace 없이 응답하는 계약을 실제 status 기준으로 강제합니다.

## 기대 동작

`ApiException`인지 일반 `Exception`인지와 무관하게 최종 HTTP status가 5xx인 예상 밖 실패는 request id, method, path, error code와 안전한 exception type을 기록합니다. 4xx는 기존처럼 기본 ERROR logging 대상이 아닙니다.

## 실제 동작과 근거

- `ApiExceptionHandler.handleApiException`은 `ErrorCode`의 status를 확인하지 않고 응답만 만듭니다.
- `handleUnexpectedException`만 `logUnexpectedException`을 호출합니다.
- persistence와 application 경계에서 `CommonErrorCode.INTERNAL_ERROR`를 담은 `ApiException`을 던지는 경로가 있어 실제 500 응답이 로그 없이 반환될 수 있습니다.

## 영향

같은 500 응답도 exception class에 따라 운영 로그 유무가 달라지고, persistence 조회 실패나 내부 상태 불일치의 request context를 잃을 수 있습니다.

## 수정 계획

1. 공통 handler가 `ApiException.errorCode()`의 HTTP status를 판정해 5xx만 안전한 공통 ERROR 경로로 보냅니다.
2. 또는 내부 오류를 예상 가능한 업무 `ApiException`과 다른 exception 경계로 분리하되, 최종 status 기반 계약은 동일하게 유지합니다.
3. 4xx ApiException 무로그, 5xx ApiException ERROR 1회, 일반 Exception ERROR 1회를 log capture test로 검증합니다.
4. 원문 exception message, request body와 민감값이 기록되지 않는지 확인합니다.

## 완료 조건

- `CommonErrorCode.INTERNAL_ERROR` ApiException이 공통 구조화 ERROR field를 남깁니다.
- 4xx 업무 ApiException은 ERROR stack trace를 남기지 않습니다.
- 같은 실패가 handler와 하위 계층에서 중복 ERROR로 기록되지 않습니다.
- request id가 응답과 로그에서 일치합니다.
- 관련 Web MVC/log capture test와 `./gradlew check`가 통과합니다.

## 관련 문서

- **현재 logging 계약과 gap**: [로깅 기준](../../../tech-stack/280-logging.md)
- **선행 기반 결과**: [API 로깅 기반](../../features/2026/004-api-logging-foundation.md)
- **현재 공통 handler source**: `ApiExceptionHandler`
