# API 로깅 기반 상세 설계

## 문서 내 진입점

| 확인할 내용 | 위치 |
| --- | --- |
| 확정 방향 | [확정 설계 요약](#확정-설계-요약) |
| 선택 이유와 대안 | [설계 메모](#설계-메모), [선택지 비교](#선택지-비교) |

## 배경

API 공통 기반 작업은 공통 오류 응답, 예외 처리, Security baseline을 먼저 확정했고 `trace id`, `correlation id`, logging, observability 전체 체계는 제외했습니다.
하지만 일반 회원 자체 가입 API는 첫 public 변경 API이고, 이후 로그인, refresh token, 관리자 기능, abuse 대응이 이어질 가능성이 높습니다.
로깅 기준 없이 기능 구현을 시작하면 예외 로그, 요청 식별자, 감사 이벤트, 민감정보 제외 기준이 각 기능에 흩어질 수 있습니다.

이번 작업은 관측성 플랫폼 전체를 도입하는 작업이 아닙니다.
Spring Boot와 SLF4J의 기본 구조를 활용해 작지만 확장 가능한 API 로깅 기준을 먼저 확정하고, 회원가입 API와 이후 인증 기능이 같은 기준을 적용하게 만드는 선행 기반 작업입니다.

## 확정 설계 요약

API 로깅 기반은 다음 구조로 확정합니다.

| 항목 | 선택 |
| --- | --- |
| 로깅 API | 애플리케이션 코드는 SLF4J API를 사용하고, Spring Boot 기본 Logback 구성을 우선 활용합니다. |
| 로깅 소유권 | 루트 `config` 계층이 HTTP 요청 식별자, MDC, 공통 예외 로그 정책을 소유합니다. |
| 도메인 계층 | Domain은 Logger, MDC, Logback, Spring logging 설정에 의존하지 않습니다. 도메인 규칙은 로그가 아니라 단위 테스트로 검증합니다. |
| 요청 식별자 | 모든 API 요청에 `requestId`를 부여하고 MDC와 응답 header에 연결합니다. 기존 `X-Request-Id`가 안전한 형식이면 재사용하고, 아니면 서버가 새로 생성합니다. |
| 구조화 로그 | 운영 환경은 Spring Boot built-in ECS JSON structured logging을 사용하고, 로컬 개발은 사람이 읽기 쉬운 console pattern을 사용합니다. |
| 예외 로그 | 예상하지 못한 500 예외는 공통 예외 핸들러에서 request id, method, path, error code와 함께 `error`로 1회 기록합니다. 4xx 업무 오류는 기본적으로 stack trace를 남기지 않습니다. |
| 민감정보 제외 | password, token, authorization/cookie header, request/response body, 원문 개인정보는 애플리케이션 로그에 남기지 않습니다. |
| 감사 이벤트 경계 | `auth_event`는 보안/업무 감사 기록이고, 애플리케이션 로그는 장애 분석용 기술 로그입니다. 회원가입 성공 같은 감사 이벤트를 일반 로그로 대체하지 않습니다. |
| 실패 격리 | 애플리케이션 로그 출력 실패는 비즈니스 요청을 실패시키지 않습니다. 단, transaction 안에서 저장하기로 한 `auth_event` 실패는 해당 기능의 데이터 무결성 정책에 따라 별도 판단합니다. |
| Lombok | annotation별 whitelist와 blacklist를 적용하고, 편의성이 크고 위험이 낮은 `@Slf4j`만 허용합니다. |

## 설계 메모

### 1. Spring Boot 기본 로깅 활용

Spring Boot는 기본적으로 Logback 설정을 제공하고, 일반적인 경우 logging dependency를 직접 바꾸지 않아도 됩니다.
따라서 별도 logging framework를 도입하지 않고 Boot 기본 구조 위에서 설정과 정책을 먼저 잡습니다.

운영 구조화 로그는 Spring Boot built-in ECS format을 선택했습니다. 현재 요구한 MDC와 structured field를 별도 encoder 없이 제공하므로 추가 logging dependency를 도입하지 않았습니다. 요구 field를 built-in format으로 표현할 수 없게 되면 그때 encoder 도입을 별도 도구 결정으로 재검토합니다.

### 2. Request ID와 MDC

request id는 HTTP 요청과 애플리케이션 로그를 연결하는 최소 상관관계 값입니다.
MDC key는 `requestId`를 기본값으로 두고, 응답 header는 `X-Request-Id`를 사용합니다.

완료 당시 선택한 request id 생성 규칙은 다음과 같습니다.

- 서버 생성 값은 UUID 또는 동등하게 충돌 가능성이 낮은 값입니다.
- inbound `X-Request-Id`는 ASCII 영문자, 숫자, `.`, `_`, `-`만 허용합니다.
- 최대 길이는 64자로 제한합니다.
- 검증에 실패하면 요청을 거부하지 않고 서버 생성 값으로 대체합니다.
- 요청 처리 종료 시 `MDC.remove("requestId")` 또는 동등한 정리 절차를 보장합니다.

### 3. 로그 레벨 기준

| 상황 | 기본 level | 이유 |
| --- | --- | --- |
| 예상하지 못한 5xx 예외 | `ERROR` | 운영자가 즉시 원인 추적을 시작해야 하는 장애 후보입니다. |
| 외부 의존성 실패, 복구 가능한 내부 실패 | `WARN` 또는 `ERROR` | 사용자 영향과 재시도 가능성에 따라 구분합니다. |
| validation 실패 | 기본 로그 없음 또는 `DEBUG` | 정상적인 클라이언트 오류이며 stack trace가 운영 신호를 흐릴 수 있습니다. |
| 업무 충돌 예외 | 기본 로그 없음 또는 `INFO` | 중복 가입처럼 예상 가능한 흐름입니다. |
| 인증 실패 | 기본 로그 없음 또는 `DEBUG` | 로그인 기능에서 실패 횟수와 abuse 기준이 생기면 별도 정책으로 확장합니다. |
| 권한 부족 | `WARN` 검토 | 관리자 기능처럼 보안 경계가 명확해질 때 정책을 구체화합니다. |
| 애플리케이션 시작/종료 | Boot 기본 `INFO` | Spring Boot 기본 로그를 활용합니다. |

### 4. 민감정보 제외 기준

애플리케이션 로그에 남기지 않는 값:

- password, password hash, password policy raw input
- access token, refresh token, 인증 token, reset token
- `Authorization`, `Cookie`, `Set-Cookie` header
- request body, response body
- 원문 이메일, 전화번호, 주소 같은 개인정보
- SQL parameter 원문
- 내부 DB id

필요한 경우 대체 값:

- request id
- public id
- event type
- error code
- HTTP method와 path template
- 원문 대신 hash 처리된 identifier

### 5. 감사 이벤트와 애플리케이션 로그 분리

`auth_event`는 보안과 운영 추적을 위한 감사 로그입니다.
회원가입 성공, 로그인 성공/실패, token reuse detection, 역할 부여/회수 같은 이벤트는 일반 애플리케이션 로그가 아니라 감사 이벤트로 보존합니다.

애플리케이션 로그는 다음 목적에 집중합니다.

- 장애 분석
- 예외 stack trace 추적
- 요청 상관관계 확인
- 설정과 배포 상태 확인

두 기록은 서로 보완하지만 대체하지 않습니다.

## 선택지 비교

### 1. 로깅 framework

| 선택지 | 평가 | 선택 여부 |
| --- | --- | --- |
| Spring Boot 기본 Logback + SLF4J | Boot starter와 맞고 추가 의존성이 없습니다. 구조화 로그도 Boot 지원 범위를 먼저 활용할 수 있습니다. | 선택됨 |
| Log4j2 전환 | 성능과 기능 장점은 있지만 현재 요구에는 framework 전환 비용이 큽니다. | 제외 |
| 별도 logging wrapper 추상화 | 모든 로그 호출을 감쌀 수 있지만 지금은 계층과 boilerplate가 먼저 늘어납니다. | 제외 |

### 2. Request ID 처리

| 선택지 | 평가 | 선택 여부 |
| --- | --- | --- |
| Servlet filter에서 request id/MDC 처리 | 모든 `/api/**` 요청에 일관되게 적용할 수 있고 Spring MVC와 Security 앞뒤 연결이 명확합니다. | 선택됨 |
| Controller별 처리 | 누락 위험이 높고 반복됩니다. | 제외 |
| 전체 tracing 도입 | 장기적으로 유효하지만 현재는 metrics/tracing collector까지 범위가 커집니다. | 후속 |

### 3. 운영 로그 format

| 선택지 | 평가 | 선택 여부 |
| --- | --- | --- |
| 운영 JSON 구조화 로그, 로컬 readable console | 운영 파싱과 로컬 개발성을 함께 만족합니다. | 선택됨 |
| 모든 환경 plain text | 시작은 쉽지만 검색/집계/알림 확장성이 낮습니다. | 제외 |
| 모든 환경 JSON | 일관성은 좋지만 로컬 개발성이 떨어질 수 있습니다. | fallback |

## 관련 문서

- **첫 소비 기능·선행 기반**: [일반 회원 자체 가입](002-member-self-sign-up-api.md), [API 공통 기반](003-api-common-foundation.md)
- **설계·개발 절차**: [작업 lifecycle](../../../workflow/100-work-execution-workflow.md), [설계 워크플로우](../../../workflow/110-design-workflow.md), [TDD](../../../workflow/210-tdd-workflow.md)
- **기술·test 기준**: [Spring Boot](../../../tech-stack/210-spring-boot.md), [테스트 스택](../../../tech-stack/300-testing-stack.md)
- **공식·보안 기준**: [Spring Boot Logging](https://docs.spring.io/spring-boot/reference/features/logging.html), [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html), [SLF4J Manual](https://www.slf4j.org/manual.html)
