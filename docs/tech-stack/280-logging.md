# 애플리케이션 로깅 기준

이 문서는 Spring Boot 기반 API 로깅의 현재 프로젝트 기준을 정의합니다.
목표는 운영 추적성과 보안 기준을 함께 만족하면서, 도메인 로직에 logging framework 의존성을 퍼뜨리지 않는 것입니다.

## 목적

- API 요청과 예외를 같은 request id로 추적합니다.
- 로컬 개발 로그와 운영 structured logging을 분리합니다.
- password, token, 개인정보 같은 민감정보를 로그에 남기지 않습니다.
- 감사 이벤트와 애플리케이션 로그의 역할을 구분합니다.
- 다음 기능이 같은 로깅 기반을 재사용하게 합니다.
- 문제가 발생했을 때 원인, 영향 범위, 재현 경로, 후속 조치를 판단할 수 있는 충분한 로그를 남깁니다.
- 과도한 로그로 비용, 노이즈, 개인정보 노출 위험을 만들지 않으면서 운영 진단 가능성을 확보합니다.

## 적용 범위

- `/api/**` 요청 request id와 MDC
- 공통 예외 로그
- Spring Security 401/403 응답 추적
- `application.yaml`과 profile별 logging 설정
- 도메인 계층 logging 의존 금지
- API, application service, adapter, persistence, 외부 연동, 비동기/배치 작업의 로그 판단 기준
- 로그 level, 필수 field, 제외 field, 중복 방지, 검증 기준

## 핵심 원칙

- 애플리케이션 코드는 SLF4J API를 사용하고, 기본 logging system은 Spring Boot 기본 Logback 구성을 따릅니다.
- request id는 `X-Request-Id` header, servlet request attribute, MDC에서 같은 값을 사용합니다.
- 운영 structured logging은 Spring Boot built-in structured logging을 우선 사용합니다.
- 로컬 기본 로그는 사람이 읽기 쉬운 plain text를 유지하되, `logging.pattern.level`로 request id를 함께 출력합니다.
- domain은 `org.slf4j`, Logback, MDC에 직접 의존하지 않습니다.
- 로그는 문제 해결에 필요한 맥락을 남기되, 요청/응답 본문과 민감정보 원문을 수집하지 않습니다.
- 실패를 처리하는 최종 경계에서 한 번만 책임 있게 기록합니다.
- 예상 가능한 4xx 오류는 기본적으로 `ERROR` stack trace를 남기지 않습니다.
- 예상하지 못한 5xx 오류는 공통 예외 핸들러에서 `ERROR`로 기록합니다.
- 현재 일반 `Exception`은 이 계약을 지키지만 5xx `ApiException`은 ERROR logging을 우회합니다. 이 문서가 logging 기준과 현재 예외를 소유하고, 수정 상태는 [004 5xx ApiException 공통 logging 누락](../work-items/bugs/2026/004-api-exception-5xx-logging.md)에서 추적합니다.
- 로그 level은 심각도가 아니라 운영자가 취해야 할 조치와 진단 필요성을 기준으로 정합니다.
- 고객·업무 API인 `/api/**`는 결과와 무관하게 요청당 하나의 완료 이벤트를 `INFO`로 기록합니다.
- 중요한 정상 상태 전이는 API 완료 이벤트와 별도의 `INFO` 업무 이벤트 또는 감사 이벤트로 남깁니다.
- 장애 분석에 필요한 값은 구조화 field로 남기고, 문장 message에만 숨기지 않습니다.

## Request ID 기준

- 적용 대상: `/api/**`
- 응답 header 이름: `X-Request-Id`
- MDC key: `requestId`
- inbound 허용 형식: 영문 대소문자, 숫자, `.`, `_`, `-`
- inbound 최대 길이: 64자
- inbound 값이 비어 있거나 허용 형식이 아니면 서버가 새 UUID를 생성합니다.
- 요청 처리 종료 시 MDC는 반드시 정리합니다.

request id는 다음 상황에서도 유지되어야 합니다.

- public API 성공 응답
- 인증 실패 `401`
- 권한 부족 `403`
- 공통 예외 handler가 처리한 `500`

## API 요청 완료 이벤트 계약

`/api/**` 요청은 성공·거부·실패 여부와 관계없이 공통 web 경계에서 정확히 하나의
`api.request.completed` 이벤트를 `INFO`로 남깁니다. 새 Controller가 개별 완료 로그를
추가하지 않아도 이 계약을 자동 적용받아야 합니다. Health check와 정적 리소스 등
`/api/**` 밖의 요청은 이 이벤트 대상이 아닙니다.

| Field | 계약 |
| --- | --- |
| `event` | 항상 `api.request.completed`입니다. |
| `requestId` | 응답 `X-Request-Id`, request attribute, MDC와 같은 값입니다. |
| `method`, `path` | HTTP method와 query string을 제외한 요청 path입니다. URL path 자체에 token·이메일 등 secret과 개인정보를 설계하지 않습니다. |
| `route` | MVC가 선택한 route template입니다. Security가 MVC 전에 차단했거나 route를 찾지 못하면 `unmatched`입니다. |
| `operation` | `<method> <route>` 형식의 저카디널리티 검색·집계 key입니다. |
| `status` | 최종 HTTP status입니다. 처리되지 않은 하위 예외가 탈출하면 최소 `500`으로 분류합니다. |
| `result` | `400` 미만은 `success`, `4xx`는 `rejected`, `5xx`는 `failure`입니다. |
| `durationMs` | 공통 filter 진입부터 응답 완료까지 걸린 시간입니다. |
| `actorId` | 인증 principal이 UUID public id일 때만 포함합니다. 이메일·내부 numeric id는 포함하지 않습니다. |
| `errorCode` | 공통 오류 응답이 공개한 안정적인 error code가 있을 때만 포함합니다. |

이 완료 이벤트는 요청 검색과 흐름 재구성을 위한 access 성격의 로그입니다. 애플리케이션
단계에서 sampling하지 않으며, 용량·보존·검색 비용은 중앙 수집 계층에서 별도 결정합니다.
완료 이벤트의 `failure`는 예외 stack trace를 중복하는 새 `ERROR`가 아닙니다. 원인 예외는
최종 오류 처리 경계가 기존 중복 방지 기준에 따라 한 번만 기록합니다.

로컬 plain text message에도 위 field의 안전한 요약을 포함해 개발자가 별도 JSON parser 없이
검색할 수 있게 합니다. 운영 ECS 로그에서는 같은 값을 SLF4J key-value field로 제공하며,
검색·집계는 message parsing이 아닌 structured field를 사용합니다.

## 로그 레벨 기준

| Level | 사용 기준 | 금지 기준 |
| --- | --- | --- |
| `ERROR` | 요청 실패, 작업 실패, 데이터 무결성 위험, 외부 시스템 장애처럼 운영자가 원인 확인이나 조치를 해야 하는 실패입니다. | 예상 가능한 4xx, validation 실패, 정상적인 business rejection을 `ERROR`로 남기지 않습니다. |
| `WARN` | 요청은 응답했거나 복구했지만 비정상 징후가 있습니다. 보안 의심, fallback, retry exhausted 후 degraded result, 설정 불일치, 중복 요청 후보에 사용합니다. | 운영자가 볼 필요 없는 정상 분기나 흔한 사용자 입력 오류를 `WARN`으로 남기지 않습니다. |
| `INFO` | API 요청 완료, 서비스 상태 전이, 중요한 업무 결과, 배치 시작/종료처럼 정상 운영 흐름을 이해하는 데 필요한 이벤트입니다. | 공통 완료 이벤트와 같은 Controller 로그, 반복 loop 내부 상태, 민감한 업무 payload를 남기지 않습니다. |
| `DEBUG` | 개발 또는 일시적 진단을 위한 상세 흐름입니다. 운영 기본 level에서는 꺼져 있어도 문제가 없어야 합니다. | 운영 장애 분석에 반드시 필요한 정보를 `DEBUG`에만 남기지 않습니다. |
| `TRACE` | 매우 상세한 내부 흐름입니다. 기본적으로 사용하지 않고, 특정 라이브러리/일시적 조사에서만 제한합니다. | business/persistence/security 판단 근거를 `TRACE`에만 남기지 않습니다. |

## 상황별 로그 기준

| 상황 | 기본 level | 남길 내용 | 남기지 않을 내용 |
| --- | --- | --- | --- |
| API 요청 완료 | `INFO` | event, request id, method, path, route, operation, status, result, duration과 존재하는 actor id/error code | query string, body, header, token, 이메일 등 개인정보 |
| 예상하지 못한 5xx 예외 | `ERROR` | request id, method, path, error code, exception type, 처리 경계, 필요 시 stack trace | 원문 request/response body, token, password, cookie, 원문 개인정보 |
| validation 실패 | 기본 로그 없음 | 오류 응답의 error code와 request id로 추적합니다. 반복 공격이나 abuse 후보일 때만 별도 보안 기준으로 승격합니다. | field value 원문, body 원문, stack trace |
| 예상 가능한 업무 예외 | 기본 로그 없음 또는 `INFO` | 상태 전이 추적이 필요한 경우 public id, operation, result code를 남깁니다. | 내부 도메인 객체 전체, 개인정보 원문, stack trace |
| 인증 실패 | 기본 로그 없음 또는 `WARN` | 반복 실패, credential stuffing 의심, 잠금 후보일 때 actor 후보 식별자 hash, client hint, result code를 남깁니다. | password, credential 원문, token, Authorization header |
| 권한 부족 | `WARN` 후보 | actor id, resource public id, required role/scope, actual role/scope, decision을 남깁니다. | 내부 권한 계산 전체, 민감 리소스 내용 |
| 보안 정책 위반 | `WARN` 또는 `ERROR` | 정책 이름, actor id 또는 익명 식별자 hash, resource public id, decision, source를 남깁니다. | 공격 payload 원문, secret, cookie |
| 외부 시스템 호출 실패 | `ERROR` 또는 `WARN` | system name, operation, endpoint alias, status/error type, duration, retry count, fallback 여부를 남깁니다. | API key, full URL query, request/response body 원문 |
| 외부 시스템 호출 지연 | `WARN` | system name, operation, duration, timeout threshold, retry count를 남깁니다. | payload 원문 |
| persistence 실패 | `ERROR` | operation, aggregate type, public id, constraint/error category, transaction boundary를 남깁니다. | SQL parameter 원문, connection secret, 개인정보 |
| transaction rollback | `ERROR` 또는 `WARN` | use case, rollback reason category, affected aggregate public id, exception type을 남깁니다. | rollback 대상 객체 전체, 민감 field |
| fallback 성공 | `WARN` | primary failure category, fallback name, degraded 여부, affected operation을 남깁니다. | 내부 예외 메시지 원문에 포함된 secret |
| retry 발생 | `DEBUG` 또는 `WARN` | retryable failure, attempt, max attempts, backoff, final result를 남깁니다. | 매 attempt마다 동일 stack trace 반복 |
| 비동기/배치 시작 | `INFO` | job name, run id, trigger, expected scope를 남깁니다. | 대량 대상 목록 전체 |
| 비동기/배치 실패 | `ERROR` | job name, run id, failed step, processed count, failed count, exception type을 남깁니다. | 실패 row 전체 payload, 개인정보 원문 |
| 중요한 업무 상태 전이 | `INFO` | operation, aggregate public id, old/new status, actor id를 남깁니다. | 변경 전후 객체 전체, 개인정보 원문 |
| 설정 오류 또는 안전하지 않은 설정 | `ERROR` | property group, validation result, safe value summary를 남깁니다. | secret property value |
| 애플리케이션 시작/종료 | `INFO` | application name, profile, version/build 정보, graceful shutdown 결과를 남깁니다. | 환경 변수 전체, secret |

`ERROR`와 `WARN`은 운영자가 action을 판단할 수 있어야 합니다.
로그를 읽고도 어떤 요청, 어떤 기능, 어떤 외부 시스템, 어떤 데이터 범위가 영향을 받았는지 모르면 부족한 로그입니다.

## 필수 field 기준

가능하면 SLF4J fluent logging API의 key-value field로 남깁니다.
운영 structured logging에서는 MDC와 key-value field가 검색 가능한 JSON field가 되어야 합니다.

공통 field:

- `requestId`: API 요청 안에서 항상 포함합니다.
- `traceId`, `spanId`: tracing을 도입한 뒤에는 request 처리 로그에 함께 포함합니다.
- `operation`: use case, handler, adapter operation 이름입니다.
- `result`: `success`, `failure`, `rejected`, `degraded`처럼 결과를 분류합니다.
- `errorCode`: 사용자 응답 또는 내부 분류 error code입니다.
- `exceptionType`: 예외 class 이름입니다.
- `durationMs`: 외부 호출, 배치, 중요한 use case의 처리 시간입니다.

HTTP/API field:

- `method`
- `path`
- `route`
- `status`
- `clientIpHash` 또는 안전하게 축약한 client hint
- `userAgentHash` 또는 필요한 경우 안전하게 축약한 user agent

Actor/resource field:

- `actorId`: 내부 numeric id보다 public id 또는 로그용 식별자를 우선합니다.
- `resourceType`
- `resourceId`: public id만 사용합니다.
- `role` 또는 `scope`: 권한 판단에 필요한 최소 범위만 남깁니다.

외부 연동 field:

- `system`
- `endpointAlias`
- `externalStatus`
- `timeoutMs`
- `retryCount`
- `fallback`

배치/비동기 field:

- `jobName`
- `runId`
- `step`
- `processedCount`
- `failedCount`

## 민감정보 기준

애플리케이션 로그에 남기지 않는 값:

- password, password hash
- access token, refresh token, reset token
- `Authorization`, `Cookie`, `Set-Cookie`
- request body, response body
- 원문 이메일, 전화번호, 주소 같은 개인정보
- SQL parameter 원문
- API key, client secret, 인증 code
- session id
- 주민등록번호, 운전면허번호, 결제 정보처럼 규제 대상 식별자
- 업로드 파일 내용과 원본 파일명에 포함된 개인정보
- exception message 원문이 민감값을 포함할 가능성이 있는 경우 해당 원문

필요할 때는 다음 값으로 대체합니다.

- request id
- public id
- error code
- HTTP method
- request path
- 필요 시 hash 처리된 식별자
- 길이, 개수, category, enum, alias 같은 요약값

예상하지 못한 예외를 기록할 때도 원문 예외 메시지는 그대로 남기지 않습니다.
예외 메시지에 민감값이 포함될 수 있기 때문입니다.
민감값을 마스킹하더라도 복구에 필요하지 않으면 수집하지 않는 것이 우선입니다.

현재 이메일 인증과 관리자 초대의 provider 실패 logging은 이 기준을 완전히 충족하지 않습니다.
이 문서가 현재 기준과 알려진 불일치를 소유합니다. notification 회귀의 수정 상태·검증 조건은
[001 Notification provider exception logging 회귀](../work-items/bugs/2026/001-notification-provider-exception-logging.md),
5xx `ApiException`의 공통 logging 수정 상태는
[004 5xx ApiException 공통 logging 누락](../work-items/bugs/2026/004-api-exception-5xx-logging.md)에서
각각 추적합니다.

## 계층별 책임

| 계층 | 책임 |
| --- | --- |
| `config`/공통 web 경계 | request id, MDC lifecycle, 공통 예외 로그, 공통 보안 응답 추적을 소유합니다. |
| Controller | 정상 요청의 시작·완료 로그를 별도로 남기지 않습니다. 공통 web 경계가 요청 완료를, 공통 handler나 보안 handler가 오류 원인을 기록합니다. |
| Application service | 중요한 업무 상태 전이, transaction 경계의 실패, 재시도/fallback 판단을 기록합니다. |
| Adapter | 외부 시스템, persistence, messaging, file I/O처럼 프로세스 밖 경계의 실패와 지연을 기록합니다. |
| Domain | logging framework에 의존하지 않습니다. 도메인 규칙은 상태와 예외, 테스트로 표현합니다. |
| Test | 로그 자체보다 운영 진단 계약을 검증합니다. request id, 민감정보 미노출, 중복 `ERROR` 방지를 확인합니다. |

## 중복 방지 기준

- 같은 실패를 여러 계층에서 `ERROR`로 반복 기록하지 않습니다.
- 공통 `INFO` 완료 이벤트는 결과 분류를 소유하고 `ERROR` 원인 로그를 대체하거나 중복하지 않습니다.
- 예외를 최종 처리해 사용자 응답으로 바꾸는 경계가 `ERROR` 로그의 기본 소유자입니다.
- 하위 계층은 같은 예외를 다시 던질 예정이면 기본적으로 로그를 남기지 않습니다.
- 하위 계층이 추가 context를 남겨야 한다면 `DEBUG` 또는 구조화 field가 포함된 새 예외/결과 타입으로 전달하는 방식을 우선합니다.
- retry loop에서는 매 시도마다 full stack trace를 남기지 않고, 최종 실패 또는 의미 있는 상태 변화만 `WARN`/`ERROR`로 남깁니다.
- 같은 이벤트를 application log와 audit event에 모두 남길 수 있지만, 두 기록의 목적과 field는 분리합니다.

## 진단 가능성 체크리스트

운영 장애나 보안 이슈를 처리하는 로그는 다음 질문에 답할 수 있어야 합니다.

- 언제 발생했는가?
- 어떤 요청 또는 작업인가?
- 어떤 사용자 또는 시스템 행위인가?
- 어떤 기능, aggregate, 외부 시스템이 관련됐는가?
- 성공, 실패, 거부, degraded 중 어떤 결과인가?
- 사용자에게 어떤 error code/status가 반환됐는가?
- 재시도, fallback, rollback이 있었는가?
- 영향 범위를 좁힐 public id나 run id가 있는가?
- 민감정보 없이도 재현과 원인 분석이 가능한가?
- 같은 실패가 중복 로그로 흩어지지 않고 한 경계에서 확인되는가?

답할 수 없는 항목이 있으면 log field, error code, audit event, metric, trace 중 무엇으로 보완할지 결정합니다.
모든 정보를 application log에 넣는 것이 답은 아닙니다.

## structured logging 기준

- 로컬 기본값은 plain text console입니다.
- 운영 profile은 `logging.structured.format.console=ecs`를 사용합니다.
- structured logging을 켜면 MDC의 key-value가 JSON에 함께 포함됩니다.
- custom logback 설정을 새로 도입할 때는 Spring Boot structured logging property와 호환되는지 먼저 확인합니다.
- 사람이 읽는 message와 기계가 검색하는 field를 분리합니다.
- 검색, 집계, alert 대상은 message parsing이 아니라 structured field로 남깁니다.
- field 이름은 같은 의미에 같은 이름을 사용합니다. 예: `requestId`, `operation`, `errorCode`, `durationMs`.

## 감사 이벤트와 일반 로그 구분

- `auth_event`는 보안/업무 감사 기록입니다.
- 애플리케이션 로그는 장애 분석과 요청 추적용 기술 로그입니다.
- 회원가입 성공, 로그인 성공/실패, token refresh 성공, token reuse detection, 역할 부여/회수는 애플리케이션 로그로 대체하지 않습니다.
- 감사 이벤트는 장기 보존과 추적 책임이 있고, 애플리케이션 로그는 운영 진단과 장애 대응 책임이 있습니다.
- 감사 이벤트가 필요한 상황을 `INFO` 로그만으로 대체하지 않습니다.
- 반대로 장애 진단에 필요한 exception type, duration, retry/fallback 정보는 감사 이벤트만으로 대체하지 않습니다.

## 코드 작업 적용 기준

다음 작업을 할 때는 이 문서를 먼저 확인합니다.

- 새 API endpoint, 공통 예외 처리, Spring Security handler를 추가하거나 수정합니다.
- 로그인, refresh token, 권한, 관리자 기능, abuse 대응처럼 보안 이벤트가 관련됩니다.
- application service에서 중요한 상태 전이, transaction, rollback, 재시도, fallback을 다룹니다.
- persistence adapter, 외부 API, messaging, file I/O, batch, async 작업을 추가합니다.
- 로그를 새로 추가하거나 기존 로그의 level, message, field를 바꿉니다.

작업 문서나 PR 설명에는 다음을 남깁니다.

- 어떤 상황에서 로그를 남기는지
- 선택한 level과 이유
- 포함한 field와 제외한 field
- 민감정보 미수집 기준
- 중복 로그 방지 위치
- 테스트 또는 수동 검증 방법

## 테스트 기준

- request id 생성, inbound 재사용, invalid fallback, MDC 정리는 단위 테스트로 검증합니다.
- 401/403과 public/protected API는 MVC/Security slice 테스트로 검증합니다.
- 모든 `/api/**` 요청의 공통 완료 이벤트와 필수 field, 성공·거부·실패 분류를 단위 및 MVC/Security 테스트로 검증합니다.
- 인증된 요청과 MVC 이전 403에는 UUID `actorId`, 공통 오류 응답에는 `errorCode`가 결속되는지 검증합니다.
- 새 API가 Controller별 로깅 없이 공통 완료 이벤트를 적용받는 구조를 유지합니다.
- 예상하지 못한 예외 로그의 request id 포함과 민감정보 미노출은 log capture 테스트로 검증합니다.
- domain logging 의존 금지는 ArchUnit으로 검증합니다.
- logging 설정 파일 분리는 resource-level 테스트로 검증합니다.
- 새 `ERROR`/`WARN` 로그를 추가하면 가능한 범위에서 log capture 테스트로 핵심 field와 민감정보 미노출을 검증합니다.
- 예외 처리 경계가 바뀌면 같은 실패가 중복 `ERROR`로 남지 않는지 확인합니다.
- 외부 연동 실패 테스트는 `system`, `operation`, `duration` 또는 retry/fallback field를 검증 후보로 둡니다.
- 테스트가 과도하게 깨지지 않도록 message 전체 문자열보다 structured field, error code, request id, 민감정보 미노출을 우선 검증합니다.

## 관련 문서

- **구현 기반**: [Spring Boot](210-spring-boot.md), [Spring Security](220-spring-security.md)
- **test 기준**: [테스트 스택](300-testing-stack.md)
- **최초 구현 기록**: [004 API 로깅 기반](../work-items/features/2026/004-api-logging-foundation.md)
- **요청 추적 확장 기록**: [015 API 운영 추적성](../work-items/features/2026/015-api-operational-traceability.md)
- **공식·보안 기준**: [Spring Boot Logging](https://docs.spring.io/spring-boot/reference/features/logging.html), [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html), [OpenTelemetry Logs Data Model](https://opentelemetry.io/docs/specs/otel/logs/data-model/), [SLF4J Manual](https://www.slf4j.org/manual.html)
