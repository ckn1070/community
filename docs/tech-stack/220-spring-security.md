# Spring Security 사용 가이드

이 문서는 Spring Security 기반 인증, 인가, 보안 테스트 기준을 정의합니다.

## 목적

- 인증과 인가 책임을 명확히 분리합니다.
- 보안 정책이 Controller에 흩어지지 않게 합니다.
- 보안 기능을 테스트 가능한 구조로 유지합니다.

## 기본 원칙

- 기본적으로 모든 요청은 보호된다고 가정합니다.
- 공개 endpoint는 명시적으로 허용합니다.
- 인증 실패와 권한 부족을 구분합니다.
- 요청 수준 인가와 method/domain 수준 인가를 역할에 맞게 나눕니다.
- 보안 설정은 `config` 또는 security adapter에 둡니다.
- 도메인 정책은 Spring Security API에 직접 의존하지 않습니다.

## 현재 API Security baseline

- business API는 기본 보호 상태로 둡니다.
- public business API는 `SecurityFilterChain`의 allow-list에 명시적으로 추가합니다.
- public business API allow-list는 Security matcher와 OpenAPI operation 분류가 함께 소비하는
  단일 source로 유지합니다.
- 현재 public business API는 다음과 같습니다.
  - `POST /api/v1/members`
  - `POST /api/v1/auth/sessions`
  - `POST /api/v1/auth/sessions/refresh`
  - `POST /api/v1/auth/logout`
  - `POST /api/v1/auth/verifications/email`
  - `POST /api/v1/auth/verifications/email/confirm`
  - `POST /api/v1/auth/password-resets`
  - `POST /api/v1/auth/password-resets/confirm`
  - `POST /api/v1/admin/invitations/{invitationId}/acceptances`
- refresh token 재발급 endpoint는 access token 만료 후 호출되어야 하므로 public business API로 둡니다.
- logout endpoint는 만료되거나 폐기된 access token을 가진 클라이언트도 refresh token을 폐기할 수 있어야 하므로 public business API로 둡니다.
- `PUT /api/v1/auth/password`는 Bearer JWT와 `SESSION_FULL`을 요구하지만 비밀번호 변경 강제 상태에서도 허용합니다.
- `POST /api/v1/admin/invitations`와 `DELETE /api/v1/admin/invitations/{invitationId}`는 `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`, `ROLE_SUPER_ADMIN`을 모두 요구합니다.
- 그 외 `/api/v1/admin/**`는 `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`와 `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN`을 요구합니다.
- 그 외 `/api/**` 요청은 Bearer JWT, `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`를 요구합니다.
- `PENDING` account용 제한 session은 `SESSION_LIMITED` authority로 표현하며, 일반 보호 API 접근에는 사용할 수 없습니다.
- `/api/**`는 stateless JSON API 기준으로 CSRF 보호 예외로 둡니다.
- 인증 실패는 `401 UNAUTHORIZED`, 권한 부족은 `403 FORBIDDEN`으로 구분합니다.
- Security 오류 응답은 REST API 공통 `ProblemDetail` schema를 따릅니다.
- HTTP Basic과 form login은 business API에서 사용하지 않습니다.
- CORS 운영 정책과 rate limit은 관련 기능 요구가 시작될 때 별도 작업에서 설계합니다.

## Bearer JWT 기준

- JWT access token 검증은 Spring Security OAuth2 Resource Server 구성을 사용합니다.
- access token은 HS256 HMAC 서명으로 시작하고, HMAC secret은 저장소에 두지 않고 외부 설정으로 주입합니다.
- access token 기본 만료 시간은 15분입니다.
- JWT `iss` claim은 설정값과 일치해야 합니다.
- JWT `sub` claim은 `account.public_id`를 사용하고 내부 DB id는 넣지 않습니다.
- JWT `roles` claim은 `ROLE_*` role code 목록을 사용합니다.
- JWT `account_status` claim은 발급 시점의 account 상태를 사용합니다. 제한 session 판단 보조 정보이며, refresh token 재발급 시에는 DB의 최신 account 상태를 다시 조회합니다.
- `roles` claim은 Spring Security `ROLE_*` authority로 변환하며, `SCOPE_` prefix로 변환하지 않습니다.
- JWT `session_access` claim은 `FULL` 또는 `LIMITED` 값을 사용합니다.
- `session_access=FULL`은 `SESSION_FULL`, `session_access=LIMITED`는 `SESSION_LIMITED` authority로 변환합니다.
- 일반 `/api/**` 보호 API는 `SESSION_FULL` authority를 요구합니다.
- access token에는 원문 email, display name, 내부 account id, refresh token 식별자를 넣지 않습니다.

## 인증 기준

- 인증은 사용자가 누구인지 확인하는 책임입니다.
- `Principal`, `Authentication`, token claims는 Web/Security Adapter에서 내부 모델로 변환합니다.
- Application/Domain은 필요한 사용자 식별자와 권한 정보만 받습니다.
- password 저장이 필요하면 반드시 `PasswordEncoder` 정책을 명시합니다.
- 인증 방식이 정해지지 않은 단계에서는 테스트 가능한 port를 먼저 정의합니다.

## Password Storage 기준

- 자체 가입 비밀번호 저장은 `DelegatingPasswordEncoder`를 사용합니다.
- 신규 password hash의 Spring Security delegating id는 `argon2`입니다.
- 실제 Argon2 hash 본문은 Argon2id 형식이며, 초기 파라미터는 memory 19 MiB, iterations 2, parallelism 1입니다.
- Spring Security `Argon2PasswordEncoder` 실행을 위해 Bouncy Castle provider를 runtime 의존성으로 둡니다.
- 비밀번호 원문은 DB, API 응답, 로그, 감사 이벤트에 저장하지 않습니다.
- password hashing 구현은 account application port 뒤의 security adapter에 둡니다.

## 인가 기준

Spring Security 공식 문서는 `authorizeHttpRequests`로 요청 수준 authorization rule을 선언하는 방식을 설명합니다.

프로젝트 기준:

- URL 기반 접근 제어는 SecurityFilterChain에서 관리합니다.
- 리소스 소유자 확인 같은 도메인 권한은 Application/Domain 정책으로 검증합니다.
- Controller에서 `if admin` 같은 권한 분기를 반복하지 않습니다.
- 규칙은 구체적인 matcher를 먼저 두고, 마지막에 기본 규칙을 둡니다.
- public API, authenticated API, role/authority API를 명확히 구분합니다.

## CSRF 기준

- 쿠키 기반 세션 인증을 사용하는 browser form/API는 CSRF 보호를 기본적으로 유지합니다.
- stateless bearer token 기반 API는 CSRF threat model을 검토한 뒤 비활성화를 결정합니다.
- CSRF 비활성화는 인증 방식과 클라이언트 저장소 전략을 함께 설명해야 합니다.
- 테스트에서는 CSRF 성공/실패 케이스를 필요한 범위에서 확인합니다.

## CORS 기준

- CORS는 인증/인가가 아니라 브라우저 교차 출처 요청 정책입니다.
- 허용 origin, method, header, credential 여부를 명시합니다.
- `*`와 credential 허용을 함께 쓰지 않습니다.
- 운영과 로컬 개발 CORS 정책을 분리합니다.
- Spring Security filter chain과 Spring MVC CORS 설정의 적용 순서를 확인합니다.

## 아키텍처 경계 적용

- Security configuration은 `config` 또는 `adapter.out.security`에 둡니다.
- 인증된 사용자 정보는 application이 이해하는 모델로 변환합니다.
- Domain은 `SecurityContextHolder`를 참조하지 않습니다.
- UseCase는 필요한 권한 정보를 명시적 parameter나 policy port로 받습니다.
- 복잡한 권한 정책은 Domain Policy로 이동합니다.

## 테스트 기준

- URL 인가는 Web MVC + Security test로 검증합니다.
- 인증 성공, 미인증, 권한 부족 케이스를 함께 둡니다.
- method security를 사용하면 method security test를 별도로 둡니다.
- 권한 정책이 도메인 규칙이면 Spring 없이 단위 테스트합니다.
- CSRF가 필요한 요청은 성공/실패 케이스를 구분합니다.

## 피해야 할 패턴

- 모든 endpoint를 임시로 `permitAll` 처리합니다.
- 보안 정책을 Controller마다 중복 작성합니다.
- Domain에서 `SecurityContextHolder`를 읽습니다.
- 인증 객체 전체를 Domain으로 넘깁니다.
- 테스트에서 성공 케이스만 검증합니다.

## 관련 참고

- [Spring Security - Authorize HttpServletRequests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- [Spring Security - CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Security - CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)
- [Spring Security - Testing](https://docs.spring.io/spring-security/reference/servlet/test/index.html)

## 관련 문서

- **framework 기반**: [Spring Boot](210-spring-boot.md)
- **보안 test 기준**: [테스트 스택](300-testing-stack.md)
- **adapter·domain 경계**: [경계와 매핑 규칙](../architecture/220-boundary-and-mapping-rules.md)
