# API 공통 기반: 오류 응답/예외 처리와 Security baseline 상세 설계

## 배경

일반 회원 자체 가입 API는 프로젝트의 첫 public 변경 API입니다.
이 API를 바로 구현하면 공통 오류 응답, 예외 처리, Security 기본 보호 정책이 첫 기능의 세부 구현처럼 섞일 수 있습니다.

공통 오류 응답과 Security baseline은 모든 HTTP API가 반복해서 따를 기준입니다.
다만 현재 필요한 범위는 JWT, 로그인, refresh token, 관리자 권한 체계를 포함하는 전체 인증 시스템이 아닙니다.
따라서 두 항목을 하나의 작은 선행 기반 작업으로 묶고, 첫 API 구현에 필요한 최소 기준만 확정합니다.

## 기반 구조 판단

- 분류: 여러 API가 공유하는 기반 구조입니다.
- 기반 구조: 공통 오류 응답, 예외 처리, Security 기본 보호, public endpoint allow-list와 Security 예외 응답 변환입니다.
- 제외 범위: 일반 회원 자체 가입 API 구현은 포함하지 않습니다.

### 소유권

- `shared.error`: 공통 오류 code 인터페이스, 공통 예외 또는 오류 descriptor 같은 HTTP API 오류 계약
- `config`: Spring MVC `ProblemDetail` 변환, 공통 예외 handler, SecurityFilterChain, public endpoint allow-list, CSRF 기준, Security 예외 응답 연결
- 업무 모듈: 업무별 오류 code, 업무 예외, 업무 예외 발생 조건

### 의존 경계

허용:

```text
account -> shared.error
config -> shared.error
config -> Spring Web MVC
config -> Spring Security
```

금지:

```text
shared.error -X-> account.internal
shared.error -X-> 임의 업무 모듈 internal
account.internal.domain -X-> shared.error
account.internal.domain -X-> Spring Web/Security
application -X-> SecurityContextHolder
```

### 확장 방식

- 새 API는 같은 Problem Details 응답 형식을 사용합니다.
- 새 업무 오류는 해당 모듈의 오류 code로 추가하고, 공통 handler는 업무 모듈 내부 타입을 직접 알지 않습니다.
- 새 public endpoint는 Security allow-list에 명시적으로 추가하고, 추가 이유와 테스트를 남깁니다.
- 인증 방식이 도입되면 기존 401/403 응답 schema는 유지하고 인증 adapter만 확장합니다.
- 중앙 handler와 Security config가 커져도 업무별 내부 타입을 직접 import하지 않습니다.

## 설계 메모

### 공통 오류 응답

- 선택: Spring `ProblemDetail`을 기반으로 하되, 프로젝트 오류 `code`와 validation `errors`를 extension field로 둡니다. 공통 오류 계약은 Spring `HttpStatus`를 직접 사용합니다.
- 선택 이유: REST API 설계 문서가 RFC 9457 Problem Details를 우선 기준으로 삼고 있고, Spring MVC와도 자연스럽게 맞습니다.
- 선택하지 않는 대안과 이유:
  - 모든 응답을 자체 `{ success, data, error }` wrapper로 감싸는 방식은 HTTP status 의미를 약하게 만들기 때문에 제외합니다.
  - 중앙 enum 하나에 모든 업무 오류 code를 모으는 방식은 모듈이 늘수록 공통 기반이 업무 세부사항을 알게 되므로 제외합니다.
  - 다국어 메시지 체계는 현재 API 계약 고정에 필요하지 않으므로 제외합니다.

### 예외 처리 경계

- 선택: 공통 handler는 Spring/framework 예외와 공통 오류 계약만 처리하고, 업무 모듈은 공통 오류 계약으로 표현 가능한 예외 또는 오류 descriptor를 제공합니다.
- 선택 이유: 공통 기반이 account 내부 타입을 직접 알면 첫 API의 내부 구조가 전체 오류 처리 기준으로 새어 나갑니다.
- domain 경계: domain model, value object, domain exception은 `ErrorCode`와 `ApiException`에 의존하지 않습니다. HTTP status 변환은 application/web 경계에서 처리합니다.
- 후속 확장: 모듈별 예외가 늘어나면 업무 모듈 내부에서 공통 오류 계약으로 변환하는 얇은 adapter 또는 factory를 둡니다.

### Security baseline

- 선택: business API는 기본 보호 상태로 두고, public endpoint는 명시적 allow-list에만 추가합니다.
- 선택 이유: Spring Security 문서와 프로젝트 보안 기준 모두 공개 endpoint를 명시적으로 허용하고 기본 보호를 유지하는 방향입니다.
- 초기 public business API: `POST /api/v1/members`
- 현재 전체 allow-list와 account 상태별 권한은 [Account 인증 정책](../../../domain/110-account-auth-policy.md)이 소유합니다.
- CSRF 기준: `/api/**`는 stateless JSON API로 보고 CSRF 보호 예외로 둡니다.
- 문서 endpoint 기준: Swagger/OpenAPI는 business API가 아니므로 이 작업 완료 당시 운영 노출에서 제외했습니다. 이후 [007 OpenAPI 실제 응답 계약 동기화](../../improvements/2026/007-openapi-response-contract.md)가 protected scheme과 401·403 명세를 구현했습니다. 현재 production 노출·보호 계약은 [260 OpenAPI 기준](../../../tech-stack/260-openapi.md)이 소유합니다.

## 관련 문서

- **첫 소비 기능**: [일반 회원 자체 가입](002-member-self-sign-up-api.md)
- **설계·개발 절차**: [설계 워크플로우](../../../workflow/110-design-workflow.md), [TDD](../../../workflow/210-tdd-workflow.md), [작업 lifecycle](../../../workflow/100-work-execution-workflow.md)
- **구조·API 기준**: [Spring Boot 모듈 구조](../../../architecture/210-spring-boot-module-structure.md), [경계와 매핑](../../../architecture/220-boundary-and-mapping-rules.md), [REST API 설계](../../../architecture/230-rest-api-design.md)
- **보안·test 기준**: [Spring Security](../../../tech-stack/220-spring-security.md), [테스트 스택](../../../tech-stack/300-testing-stack.md)
