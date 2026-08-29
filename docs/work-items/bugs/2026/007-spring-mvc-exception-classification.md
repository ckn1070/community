# Spring MVC framework 예외 분류 누락

## 상태

Proposed

## 달성할 결과

Spring MVC가 controller 호출 전에 발생시키는 client 입력·route·method 예외를 공통 API 계약의 4xx로 변환하고 예상하지 못한 5xx와 분리합니다.

## 기대 동작

path variable type 불일치는 `400`, 존재하지 않는 route는 `404`, 지원하지 않는 HTTP method는 `405` Problem Details로 응답합니다. 공통 request id와 안전한 오류 code를 유지하며 client 오류를 `INTERNAL_ERROR`로 기록하지 않습니다.

## 실제 동작과 근거

- 관리자 초대 endpoint의 `invitationId`는 UUID로 직접 binding됩니다.
- `ApiExceptionHandler`는 `MethodArgumentTypeMismatchException`을 `400 INVALID_REQUEST`,
  `HttpRequestMethodNotSupportedException`을 `405 METHOD_NOT_ALLOWED`로 전용 처리합니다.
- 카탈로그와 공통 MVC test가 malformed UUID·enum·날짜와 unsupported method 계약을 고정합니다.
- `NoResourceFoundException` 전용 처리는 아직 없어 존재하지 않는 route가 generic
  `500 INTERNAL_ERROR`로 변환될 수 있습니다.

## 영향

클라이언트 입력 오류가 서버 장애로 보고돼 5xx 지표와 ERROR log를 오염시키고, 공개 API의 400·405·500 의미가 endpoint별로 달라질 수 있습니다.

## 수정 계획

1. Spring MVC가 controller 전에 발생시키는 framework exception을 전수 조사하고 400·404·405·415 owner를 확정합니다.
2. 완료된 path type mismatch와 unsupported method 분류를 유지하면서 unknown route를 공통
   `404` Problem Details로 변환합니다.
3. unknown route와 실제 generic exception을 각각 Web MVC test로 고정합니다.
4. 4xx는 기본 ERROR stack trace를 남기지 않고 5xx만 공통 logging 계약을 따르는지 확인합니다.

## 완료 조건

- malformed UUID path가 `400`과 안정된 오류 code를 반환합니다.
- 존재하지 않는 route가 `404`와 안정된 오류 code를 반환합니다.
- 지원하지 않는 method가 `405`와 안정된 오류 code를 반환합니다.
- response의 request id와 Problem Details 형식이 공통 계약과 일치합니다.
- generic 500 처리와 [5xx ApiException logging](004-api-exception-5xx-logging.md) 계약이 약화되지 않습니다.
- 관련 Web MVC test와 `./gradlew check`가 통과합니다.

## 관련 문서

- **현재 API 계약과 gap**: [REST API 설계](../../../architecture/230-rest-api-design.md)
- **선행 공통 기반**: [API 공통 기반](../../features/2026/003-api-common-foundation.md)
- **재현 endpoint 기능**: [관리자 초대](../../features/2026/011-admin-invitations.md)
- **현재 handler source**: `ApiExceptionHandler`
