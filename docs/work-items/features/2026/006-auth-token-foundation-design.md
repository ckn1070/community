# 인증 토큰 기반 상세 설계

## 배경

회원 로그인 API 작업에서 비밀번호 로그인 기능과 함께 Bearer JWT 인증, token 발급/검증, `auth` 모듈 신설, role authority 변환, Security 설정 전환이 함께 드러났습니다.
이 기반은 `POST /api/v1/auth/sessions` 하나에 닫히지 않고 refresh token 재발급, 로그아웃, 관리자 API, OAuth/OIDC, MFA까지 반복해서 영향을 줍니다.

따라서 이 기반 작업은 로그인 기능 안에 섞기보다 **분리하는 게 낫습니다**.
분리 대상은 "로그인 기능 전체"가 아니라, 로그인과 후속 인증 기능이 공유할 인증 토큰 기반입니다.

## 설계 메모

### 분리 결론

이 기반 작업은 회원 로그인 기능에서 **분리하는 게 낫습니다**.

분리하는 대상:

- `auth` 모듈 신설과 모듈 의존 방향
- JWT access token 발급 기준
- JWT decoder와 Bearer token 인증 설정
- role claim과 authority 변환
- token 설정과 secret/key 운용 기준
- public/protected API 인증 경계 테스트 기준

로그인 기능 문서에 남길 대상:

- `POST /api/v1/auth/sessions`
- email/password 검증 흐름
- refresh token 저장
- 로그인 성공/실패 auth event
- 실패 횟수와 잠금 후보 정책
- account status별 로그인 허용 정책

### 선택한 모듈 경계

```text
auth
  api
  internal
    application
      port.in
      port.out
      service
    adapter.in.web.publicapi
    adapter.out.security
    adapter.out.persistence

account
  api
    AccountAuthSubject
    AccountAuthSubjectLookup
  internal
    # auth는 직접 참조하지 않습니다.

config
  # SecurityFilterChain 조립
```

`auth`가 `account.internal` JPA repository나 entity를 직접 참조하는 것은 금지합니다.
계정 인증 조회가 필요하면 `account.api` 공개 계약 또는 `auth` application port와 adapter 조합으로 경계를 만듭니다.

### 경계 계약

- `AccessTokenIssuerPort`: application이 access token 발급을 요청하는 outbound port입니다.
- 완료 당시 `account.api` 공개 계약은 account public id, 상태와 role을 `auth`에 제공했습니다. exact current type과 필드는 후속 기능에서 확장됐으므로 이 문서가 현재 inventory를 소유하지 않습니다.
- JWT 검증과 authority 변환은 Spring Security adapter가 소유하며 application port를 만들지 않습니다.
- 현재 exact type과 package inventory는 [모듈 지도](../../../project/210-module-map.md)와 source가 소유합니다.

### 이 기반에서 선택한 JWT Claim

| Claim | 값 | 이유 |
| --- | --- | --- |
| `sub` | account public id | 내부 DB id를 노출하지 않고 account를 안정적으로 식별합니다. |
| `roles` | role code 목록 | coarse-grained authorization에 필요합니다. |
| `iss` | 설정값 | token 발급 주체를 검증합니다. |
| `iat` | 발급 시각 | 진단과 검증 기준입니다. |
| `exp` | 만료 시각 | access token 노출 기간을 제한합니다. |

이 기반에서 제외한 값:

- 내부 DB id
- 원문 email
- display name
- refresh token 식별자
- password credential 상태

이 표는 006 완료 당시의 최초 claim 계약입니다. 이후 [회원 로그인](005-member-login-api.md)이 `session_access`를, [관리자 계정 기반](010-admin-account-foundation.md)이 `account_status`·`password_change_required`를 추가했습니다. 현재 전체 claim과 authority 계약은 [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)이 소유하며, 후속 값을 이 기반 작업의 최초 산출물로 소급하지 않습니다.

### Security 설정 결정

- 006 완료 당시 `/api/v1/members`는 public으로 유지했습니다.
- 006 완료 당시 이 기반을 소비하던 `/api/v1/auth/sessions`를 public endpoint로 두고, 당시 그 외 `/api/**`는 Bearer JWT 인증을 요구했습니다.
- stateless JSON API 기준으로 CSRF 비활성화는 유지하되, refresh token을 cookie로 전달하기로 결정하면 CSRF/CORS 정책을 다시 검토합니다.
- HTTP Basic은 제거하고 Bearer JWT 기반을 사용합니다.

이 목록은 후속 이메일 인증·관리자 초대·비밀번호 재설정 endpoint를 포함한 현재 allow-list가
아닙니다. 현재 public/protected endpoint와 session access 계약은
[Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)이 소유하고, 실행 사실은
`ApiSecurityConfig`가 소유합니다.

## 확정 결정

### 1. JWT 서명 방식과 key 운용

- 결정: access token 서명 방식은 HMAC symmetric secret으로 시작합니다.
- 분류: 요구사항 의존형
- 선택안: 단일 서버/단일 발급자 MVP이므로 HMAC secret으로 시작하고, 외부 검증자나 gateway가 생기면 asymmetric key로 전환합니다.
- 선택지:
  - HMAC secret: 설정이 단순하고 초기 구현 비용이 낮습니다.
  - RSA/ECDSA key pair: 검증자와 발급자를 분리하기 좋지만 key 운용과 rotation이 복잡합니다.
- 선택 이유: 현재 프로젝트는 단일 Spring Boot 애플리케이션이며 외부 token 검증자가 확정되지 않았습니다.
- 트레이드오프: HMAC은 검증 주체가 secret을 알면 발급도 가능하므로, 검증자가 늘어나는 구조에는 부적합합니다.
- 재검토 조건: API gateway, 별도 resource server, 외부 서비스가 JWT를 직접 검증합니다.

### 2. `auth`와 `account`의 공개 계약 형태

- 결정: `auth`가 로그인과 token 검증에 필요한 account 정보는 `account.api` 공개 계약으로 조회합니다.
- 분류: 기술 표준형
- 선택안: `account.api`에 인증 조회용 공개 계약을 두고, `auth`는 그 계약에만 의존합니다.
- 선택지:
  - `account.api` 공개 계약: 모듈 경계가 명확하고 Spring Modulith 검증에 맞습니다.
  - `auth` port + account adapter 구현: auth 관점 의존성이 분명하지만 account 쪽 adapter 위치와 모듈 의존 방향을 더 설계해야 합니다.
  - `account.internal` 직접 참조: 구현은 빠르지만 모듈 경계를 깨므로 제외합니다.
- 선택 이유: 현재 `account`는 token subject로 사용할 account 인증 데이터의 소유자이고, 외부 모듈은 공개 계약만 참조해야 합니다.
- 트레이드오프: 공개 계약을 설계해야 하므로 첫 구현 파일 수가 늘어납니다.
- 재검토 조건: Spring Modulith 모듈 경계 전략을 바꿉니다.

### 3. Access Token 만료 시간

- 결정: JWT access token 만료 시간은 15분으로 시작합니다.
- 분류: 요구사항 의존형
- 선택안: 15분으로 시작합니다.
- 선택지:
  - 5분: 보안은 강하지만 refresh 호출이 잦아집니다.
  - 15분: 일반적인 균형입니다.
  - 30분: UX는 편하지만 탈취 시 노출 기간이 늘어납니다.
- 선택 이유: access token은 DB에 저장하지 않으므로 발급 후 즉시 회수하기 어렵습니다.
- 트레이드오프: 짧은 만료 시간은 refresh token 재발급 구현과 클라이언트 재시도 처리가 중요해집니다.
- 재검토 조건: 모바일 유지 로그인 요구나 관리자 보안 정책이 구체화됩니다.

### 4. Spring Security JWT Resource Server 사용 여부

- 결정: JWT 검증은 Spring Security Resource Server 구성을 사용합니다.
- 분류: 기술 표준형
- 선택안: Spring Security JWT Resource Server 구성을 사용합니다.
- 선택지:
  - Resource Server: 표준 검증 흐름과 테스트 지원을 활용합니다.
  - 자체 filter: 세밀한 제어는 가능하지만 표준 보안 처리를 직접 책임져야 합니다.
- 선택 이유: Bearer JWT 검증은 Spring Security가 직접 지원하는 표준 영역이고, 프로젝트 기준도 Spring Security Best Practice를 우선합니다.
- 트레이드오프: Resource Server 의존성이나 설정 방식은 Spring Boot 4 호환성을 확인해야 합니다.
- 재검토 조건: Resource Server 설정이 프로젝트 Spring Boot 4 조합에서 예상보다 무겁거나 호환 문제가 있습니다.

## 관련 문서

- **첫 소비 기능·공통 기반**: [회원 로그인](005-member-login-api.md), [API 공통 기반](003-api-common-foundation.md), [로깅 기반](004-api-logging-foundation.md)
- **구조·데이터 기준**: [모듈 지도](../../../project/210-module-map.md), [Account 데이터 모델](../../../project/220-account-auth-data-model.md)
- **보안·logging 기준**: [Spring Security](../../../tech-stack/220-spring-security.md), [로깅 기준](../../../tech-stack/280-logging.md)
- **설계 절차**: [설계 워크플로우](../../../workflow/110-design-workflow.md)
