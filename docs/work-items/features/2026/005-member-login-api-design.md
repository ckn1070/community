# 회원 로그인 API 상세 설계

## 문서 내 진입점

| 확인할 내용 | 위치 |
| --- | --- |
| API·UseCase·보안 판단 | [설계 메모](#설계-메모), [기반 결정](#기반-결정) |
| 최종 정책 | [확정된 결정](#확정된-결정) |

## 배경

일반 회원 자체 가입 API가 구현되어 account, profile, password credential, role assignment, auth event를 생성할 수 있습니다.
이제 가입된 account가 비밀번호로 로그인하고 API 접근에 사용할 인증 토큰을 받을 수 있는 흐름이 필요합니다.

현재 schema에는 `refresh_token`, `auth_event`, `account.last_login_at`, `account_password_credential.failed_login_count`, `locked_until`이 이미 준비되어 있습니다.
따라서 첫 로그인 작업은 새 인증 DB schema를 크게 추가하기보다, 기존 account 인증/인가 모델 위에 session 생성 API, JWT 발급, refresh token 저장, 인증 감사 이벤트 기록을 연결하는 작업으로 봅니다.
다만 `auth` 모듈 신설, JWT/Bearer 인증 설정, role authority 변환은 로그인 하나에 닫히지 않는 큰 기반 작업이므로 [006-auth-token-foundation.md](006-auth-token-foundation.md)로 분리합니다.

## 설계 메모

### API 리소스 모델

확정 API는 `POST /api/v1/auth/sessions`입니다.
로그인은 서버 관점에서 인증된 API 인증 세션을 새로 만드는 행위로 해석할 수 있으므로, REST API 설계 기준의 리소스 생성으로 표현합니다.
`sessions`만 사용하면 인증 세션인지 다른 업무 세션인지 애매하므로 `auth` namespace 아래의 `sessions` 리소스로 둡니다.

행위형 후보인 `POST /api/v1/auth/login`은 익숙하지만, 프로젝트 문서의 resource-oriented HTTP API 기준과는 덜 맞습니다.
`POST /api/v1/auth-sessions`도 인증 session 의미를 담을 수 있지만, 기존 `/auth` namespace와 resource 계층을 유지하기 위해 `/api/v1/auth/sessions`를 선택합니다.

### 확정 HTTP 계약

요청:

```json
{
  "email": "user@example.com",
  "password": "correct horse battery staple"
}
```

응답:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "accessTokenExpiresAt": "2026-06-22T12:15:00Z",
  "refreshToken": "raw-refresh-token",
  "refreshTokenExpiresAt": "2026-07-22T12:00:00Z",
  "memberId": "0f3445fd-5f22-4f74-9a9d-1ff0c68a924a",
  "roles": ["ROLE_MEMBER"],
  "sessionAccess": "FULL"
}
```

refresh token은 response body로 전달합니다.
브라우저 전용 클라이언트와 same-site cookie 전략이 확정되면 HttpOnly cookie 전환을 재검토합니다.

### 확정 책임 흐름

```text
POST /api/v1/auth/sessions
  -> CreatePasswordSessionController
  -> CreatePasswordSessionRequest validation
  -> CreatePasswordSessionCommand
  -> CreatePasswordSessionUseCase.execute
       -> EmailAddress 정규화
       -> account.api.PasswordLoginAccountLookup
       -> PasswordVerifierPort
       -> account 상태에 따른 SessionAccess 계산
       -> AccessTokenIssuerPort
       -> RefreshTokenIssuerPort
       -> account.api.PasswordLoginResultRecorder
            -> refresh token 저장
            -> 로그인 성공·실패 상태와 auth event 기록
  -> CreateSessionResponse
```

`auth`는 `account.internal`을 직접 참조하지 않고 `account.api`의 조회·결과 기록 계약만 소비합니다. exact package와 type inventory는 [모듈 지도](../../../project/210-module-map.md)와 실제 source가 소유합니다.

### 계정 상태 정책

확정 방향은 `ACTIVE`는 full session, `PENDING`은 제한 세션 발급입니다.
일반 회원 자체 가입은 `PENDING`으로 생성되고 [008 이메일 인증](008-member-email-verification.md)이 확인 완료 시 `ACTIVE`로 전환합니다.
이메일 인증 전이라고 로그인 자체를 막지는 않되, 서비스 핵심 기능 접근은 제한합니다.

정책:

| 상태 | 로그인 정책 |
| --- | --- |
| `PENDING` | `LIMITED` session 발급. 현재 `SESSION_LIMITED`를 요구하는 보호 API는 없으며 이메일 인증 요청·완료는 별도 public 계약으로 처리 |
| `ACTIVE` | full session 발급 |
| `SUSPENDED` | 로그인 거부, 정지 상태 오류 응답 |
| `WITHDRAWN` | 로그인 거부, 자격증명 존재 여부를 드러내지 않는 오류 응답 |

### Token 정책

- access token은 짧게 만료시키고 DB에 저장하지 않습니다.
- refresh token은 원문을 저장하지 않고 SHA-256 hash만 저장합니다.
- refresh token `family_id`는 token rotation과 reuse detection을 위해 생성 시점부터 기록합니다.
- 첫 로그인 작업에서는 refresh token rotation API를 구현하지 않더라도, 저장 구조는 rotation을 전제로 둡니다.

### Security 설정

이 로그인 작업 전의 API baseline은 임시 HTTP Basic이었고, [006 인증 토큰 기반](006-auth-token-foundation.md)에서 보호 endpoint를 Bearer JWT 인증으로 전환했습니다.
로그인 endpoint인 `/api/v1/auth/sessions`는 public allow-list에 포함합니다.

Spring Security JWT Resource Server 구성을 사용합니다.
role claim은 Spring Security 기본 scope claim 매핑에 맡기지 않고, 프로젝트의 `ROLE_MEMBER`, `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` authority로 변환하는 adapter를 둡니다.

### Auth Event와 Logging

`auth_event`는 보안/업무 감사 기록이고, application log는 장애 진단 기록입니다.
로그인 성공/실패는 `auth_event`에 기록합니다.
일반 log에는 password, token, Authorization header, Cookie, 원문 email, request body를 남기지 않습니다.

완료 당시 실패 이벤트의 식별자 기준은 다음과 같습니다.

- account를 찾은 뒤 password가 틀렸습니다: `account_id`를 연결하고 normalized email의 `identifier_hash`도 저장
- account를 찾지 못했습니다: `account_id` null, normalized email의 HMAC-SHA256 hash를 `identifier_hash`에 저장

`identifier_hash`는 JWT secret과 분리된 HMAC-SHA256 secret으로 계산합니다.
로그인 실패 감사 이벤트의 `identifier_hash`와 application log에는 원문 email이나 단순 SHA-256 hash를 남기지 않습니다. account 자체는 로그인 식별과 연락을 위해 `account.email`·`email_normalized`를 저장하며, 이 개인정보 저장 계약은 [Account Schema Reference](../../../project/schema/100-account-auth.md)가 소유합니다.

## 기반 결정

### `auth` 모듈 신설

- 분류: 모듈 경계에 영향을 주는 기술 결정입니다.
- 선택: 로그인/JWT/OAuth 작업 시작 시점부터 `auth` 모듈을 신설합니다.
- 선택 이유:
  - 기존 회원가입 문서에서 `auth`는 로그인/JWT/OAuth 구현 시 분리한다고 정리되어 있습니다.
  - account는 인증 주체의 생명주기를 소유하고, auth는 인증 session과 token lifecycle을 소유하는 편이 변경 이유를 분리합니다.
  - refresh token 재발급, 로그아웃, OAuth/OIDC, MFA가 이어져도 account 모듈이 비대해지는 것을 줄입니다.
- 선택하지 않는 대안과 이유:
  - `account` 내부에 로그인 구현을 둡니다: 첫 구현은 빠르지만 token lifecycle과 계정 생명주기가 섞입니다.
  - `config` 중심으로 인증을 구현합니다: 보안 설정과 업무 인증 흐름이 섞여 테스트와 확장이 어려워집니다.

### Bearer JWT 인증 기준

- 분류: 작고 기술적으로 명확함입니다.
- 선택: 보호 API는 Bearer JWT 인증으로 전환하고, Spring Security JWT Resource Server 구성을 우선 사용합니다.
- 선택 이유:
  - 현재 API baseline은 stateless JSON API입니다.
  - access token을 DB에 저장하지 않는 기존 데이터 모델 결정과 맞습니다.
  - Spring Security의 표준 구성과 테스트 지원을 사용할 수 있습니다.
- 선택하지 않는 대안과 이유:
  - HTTP Basic 유지: 임시 보호 수단일 뿐 로그인/JWT 요구를 충족하지 못합니다.
  - 서버 세션: stateless API와 refresh token 저장 정책과 맞지 않습니다.
  - 자체 filter만 구현: 표준 검증과 테스트 지원을 버릴 이유가 현재 없습니다.

### Refresh Token 저장 기준

- 분류: 작고 기술적으로 명확함입니다.
- 선택: refresh token 원문은 응답으로 한 번만 전달하고, DB에는 SHA-256 hash만 저장합니다.
- 선택 이유:
  - 기존 데이터 모델 문서가 refresh token hash 저장을 확정했습니다.
  - DB 유출 시 token 원문 재사용 위험을 낮춥니다.
  - `refresh_token.family_id`는 rotation과 reuse detection을 나중에 연결할 수 있습니다.
- 선택하지 않는 대안과 이유:
  - refresh token 원문 저장: 보안 리스크가 큽니다.
  - refresh token을 저장하지 않음: 로그아웃, rotation, reuse detection, 강제 폐기가 어렵습니다.
  - access token까지 저장: 현재 stateless access token 정책과 충돌합니다.

### Auth Event 기록 기준

- 분류: 작고 기술적으로 명확함입니다.
- 선택: 로그인 성공과 실패는 모두 `auth_event`로 기록합니다.
- 선택 이유:
  - 완료 당시 `auth_event` schema가 `LOGIN_SUCCESS`, `LOGIN_FAILURE`를 허용했습니다.
  - 인증 감사 이벤트는 application log로 대체하면 보존과 운영 추적 기준이 흐려집니다.
  - 실패 이벤트는 account를 특정하지 못할 수 있으므로 `identifier_hash`를 사용합니다.
- 선택하지 않는 대안과 이유:
  - 성공만 기록: credential stuffing, brute force, 잠금 정책 분석이 어려워집니다.
  - application log만 사용: 감사 이벤트와 장애 진단 로그의 책임이 섞입니다.

이 작업의 완료 당시 감사 event type은 `LOGIN_SUCCESS`·`LOGIN_FAILURE`였습니다. 후속 [Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md)가 이를 `PASSWORD_LOGIN/SUCCESS`·`PASSWORD_LOGIN/FAILURE`로 대체했으며, 현재 계약은 [Auth Event 정책](../../../domain/120-auth-event-policy.md)이 소유합니다.

### 로그인 식별자 Hash 기준

- 분류: 작고 기술적으로 명확함입니다.
- 선택: account를 특정하지 못한 로그인 실패의 `identifier_hash`는 별도 HMAC-SHA256 secret으로 계산합니다.
- 선택 이유:
  - 로그인 실패 감사 레코드에 원문 email을 복제하지 않으면서도 반복 실패 시도를 묶어 추적할 수 있습니다.
  - 단순 SHA-256은 email 후보 대입으로 역추적될 수 있으므로 secret 기반 HMAC이 더 적절합니다.
  - JWT HMAC secret과 분리해 token 서명 secret rotation과 감사 식별자 hash 정책이 서로 묶이지 않게 합니다.
- 선택하지 않는 대안과 이유:
  - 원문 email 저장: 개인정보와 감사 데이터가 불필요하게 결합됩니다.
  - 단순 SHA-256: email 공간이 작아 사전 대입 공격에 약합니다.
  - JWT HMAC secret 재사용: token 서명 secret과 감사 식별자 hash의 rotation 주기가 강하게 결합됩니다.

## 확정된 결정

### 로그인 API endpoint

- 결정: 비밀번호 로그인 endpoint는 `POST /api/v1/auth/sessions`입니다.
- 선택하지 않은 대안:
  - `POST /api/v1/auth-sessions`: 한 segment에 의미를 압축하지만 기존 `/auth` namespace의 계층과 맞지 않아 제외합니다.
  - `POST /api/v1/auth/login`: 익숙하지만 행위 중심 URI라 resource-oriented 기준과 덜 맞습니다.
  - `POST /api/v1/tokens`: token 발급만 강조되어 refresh lifecycle과 session 의미가 좁아집니다.
- 재검토 조건: 인증 API URI 정책을 전체적으로 바꿉니다.

### `PENDING` account 로그인 정책

- 결정: `PENDING` account는 로그인 자체를 막지 않고 제한 세션을 발급합니다.
- 이유: 이메일 인증 전 상태도 인증 결과와 향후 제한 기능을 구분할 수 있어야 하므로 로그인 자체는 허용하되 일반 보호 API에는 사용하지 않습니다.
- 제한: full session과 같은 권한으로 서비스 핵심 기능을 열지는 않습니다.
- 현재 허용 기능: `SESSION_LIMITED`를 요구하는 보호 API는 아직 없습니다. 이메일 인증 요청·완료 API는 public allow-list이므로 limited session 권한과 별개입니다.

### Refresh Token 전달 방식

- 결정: refresh token 원문은 response body로 전달합니다.
- 이유: 클라이언트에서도 token 보관 정책을 별도로 만들어야 하므로, 현재 단계에서는 cookie 기반 CSRF/CORS 정책을 먼저 확정하지 않고 JSON API 응답으로 시작합니다.
- 선택하지 않은 대안:
  - HttpOnly Secure cookie: 브라우저 XSS 방어에 장점이 있지만 CSRF, CORS, SameSite, credential 정책을 함께 확정해야 합니다.
- 재검토 조건: 브라우저 웹 클라이언트만 지원하고 same-site cookie 전략을 확정합니다.

### Refresh Token 만료 시간

- 결정: refresh token 만료 시간은 30일입니다.
- 이유: access token은 15분으로 짧게 유지하고, refresh token은 DB 저장과 폐기 정책으로 관리합니다.
- 선택하지 않은 대안:
  - 14일: 보안은 강하지만 재로그인 빈도가 늘어납니다.
  - 60일: 편의성은 높지만 탈취와 장기 미사용 계정 리스크가 커집니다.
- 재검토 조건: 모바일 유지 로그인 요구, 관리자 보안 정책, 실제 abuse 패턴이 드러납니다.

### 로그인 실패 횟수와 잠금 정책

- 결정: 첫 구현은 실패 횟수와 마지막 실패 시각을 기록하고, 실제 계정 잠금은 적용하지 않습니다.
- 이유: hard lock은 계정 잠금 악용과 고객 지원 비용을 만들 수 있으므로 rate limit, captcha, 이메일 인증 정책과 함께 별도 abuse 대응 작업에서 확정합니다.
- 후속 작업: 계정 잠금, IP/identifier rate limit, captcha 정책은 account auth backlog에 남깁니다.

## 선행 기반 확정 사항

- [006-auth-token-foundation.md](006-auth-token-foundation.md)에서 `auth` 모듈 신설을 확정하고 구현했습니다.
- access token claim은 `sub` account public id, `roles`, `iat`, `exp`, `iss`로 확정했습니다.
- access token에는 원문 email, display name, 내부 DB id를 넣지 않습니다.
- access token 만료 시간은 15분으로 확정했습니다.
- JWT 검증은 Spring Security Resource Server 구성을 사용합니다.
- 로그인 구현은 위 최초 claim에 `session_access`를 추가해 `PENDING`의 `LIMITED`와 `ACTIVE`의 `FULL` session을 authority로 구분했습니다. 후속 claim 확장까지 포함한 현재 계약은 [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)이 소유합니다.

## 관련 문서

- **선행 구현 결과**: [DB Schema](001-account-auth-db-schema.md), [자체 가입](002-member-self-sign-up-api.md), [API 공통 기반](003-api-common-foundation.md), [로깅 기반](004-api-logging-foundation.md)
- **공통 token 기반**: [인증 토큰 기반](006-auth-token-foundation.md)
- **후속 Account 범위**: [Account Backlog](../../backlog/2026/003-account-auth-follow-up.md)
- **업무·데이터 기준**: [Account domain](../../../domain/100-account.md), [Account 데이터 모델](../../../project/220-account-auth-data-model.md)
- **구조·API 기준**: [모듈 지도](../../../project/210-module-map.md), [경계와 매핑](../../../architecture/220-boundary-and-mapping-rules.md), [REST API 설계](../../../architecture/230-rest-api-design.md)
- **보안·logging 기준**: [Spring Security](../../../tech-stack/220-spring-security.md), [로깅 기준](../../../tech-stack/280-logging.md)
- **설계 절차**: [설계 워크플로우](../../../workflow/110-design-workflow.md)
