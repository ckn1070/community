# API 운영 추적성

## 상태

Done

## 결과

- 모든 `/api/**` 요청이 성공·거부·실패 여부와 관계없이 공통 `INFO` 완료 이벤트 한 건을 남깁니다.
- request id, method, path, route, operation, status, result, duration을 구조화 field로 남깁니다.
- 인증 principal이 UUID public id이면 `actorId`를, 공통 오류 응답이면 공개 `errorCode`를 결속합니다.
- Security가 MVC 전에 401/403으로 차단한 요청도 path, 고객 식별자, error code로 추적할 수 있습니다.
- 새 API는 Controller별 시작·완료 로그를 추가하지 않아도 공통 filter와 interceptor 계약을 자동 적용받습니다.
- query string, body, header, token, 이메일과 내부 numeric id는 완료 이벤트에서 수집하지 않습니다.
- 단위 테스트와 MVC/Security 통합 테스트가 성공·거부·실패 분류와 field 결속을 보호합니다.

## 범위

- 애플리케이션의 API 요청 완료 로그와 구조화 field
- UUID account public id와 공개 error code의 요청 상관관계
- Security 사전 차단 경계와 처리되지 않은 예외 경계
- 로컬 plain text 검색성과 production ECS structured logging 호환

중앙 로그 수집 제품, 보존 기간, 접근 권한, 비용·sampling 정책과 distributed tracing 도입은
이 작업에서 결정하거나 구축하지 않았습니다.

## 영구 SOT 승격

- [애플리케이션 로깅 기준](../../../tech-stack/280-logging.md)

## 관련 문서

- **선행 기반**: [004 API 로깅 기반](004-api-logging-foundation.md)
- **구현 source**: `ApiRequestContextFilter`, `ApiRequestActorContextInterceptor`, `ApiRequestLogContext`, `ApiErrorResponseFactory`, `ApiSecurityProblemHandler`
