# Refresh Token 재발급 API 상세 설계

## 문서 내 진입점

| 확인할 내용 | 위치 |
| --- | --- |
| API·token·transaction 판단 | [설계 메모](#설계-메모), [기술 기본값](#기술-기본값) |
| 최종 정책 | [확정된 결정](#확정된-결정) |

## 배경

회원 로그인 API는 access token과 refresh token을 발급하는 흐름을 소유합니다.
refresh token 재발급은 로그인과 같은 인증 토큰 기반을 사용하지만, 만료된 access token을 새로 받는 별도 token lifecycle 기능입니다.

완료 당시 account 인증/인가 schema에는 `refresh_token.family_id`, `replaced_by_token_id`, `revoked_reason`, `last_used_at`이 있고, `auth_event`에는 `TOKEN_REFRESH`, `TOKEN_REUSE_DETECTED` 값이 준비되어 있었습니다.
따라서 이번 작업은 새 DB 기반을 크게 만드는 일이 아니라, 기존 token 기반 위에서 refresh token rotation과 reuse detection 정책을 확정하고 API로 연결하는 작업으로 봅니다.

## 설계 메모

### API endpoint

확정 endpoint는 `POST /api/v1/auth/sessions/refresh`입니다.
이 프로젝트의 로그인 API는 `POST /api/v1/auth/sessions`이므로, refresh token 재발급은 외부 API 사용자에게 "인증 session을 갱신한다"는 같은 맥락으로 보이게 하는 편이 자연스럽습니다.

`refresh-token-rotations`는 서버 내부 정책 이름으로는 정확하지만 public endpoint로는 일반적으로 많이 쓰이는 형태가 아닙니다.
외부 API 계약에서는 내부 보안 정책을 드러내는 정확성보다, API 소비자가 바로 이해할 수 있는 관용성과 프로젝트 API 흐름을 우선합니다.

후보 비교:

| 후보 | 판단 |
| --- | --- |
| `POST /api/v1/auth/sessions/refresh` | 선택됨. 로그인 endpoint와 이어지고, API 소비자가 의미를 바로 이해하기 쉽습니다. action segment가 있지만 인증 API에서는 관용성과 이해도가 더 중요합니다. |
| `POST /api/v1/auth/refresh` | 많이 보이는 단순 형태입니다. 다만 session 맥락이 덜 드러나고 auth namespace 안의 다른 refresh 의미와 섞일 수 있습니다. |
| `POST /api/v1/auth/tokens/refresh` | 많이 보이는 형태입니다. 다만 이 API가 access token만 갱신하는지 refresh token도 rotation하는지 외부 이름만으로는 덜 분명합니다. |
| `POST /api/v1/auth/refresh-token-rotations` | 선택하지 않습니다. 내부 rotation 정책은 정확히 드러나지만 public API로는 낯설고 구현 정책을 과하게 노출합니다. |
| `POST /oauth/token` + `grant_type=refresh_token` | OAuth/OIDC 서버를 제공할 때 자연스럽습니다. 현재 프로젝트의 자체 session API 흐름과는 맞지 않습니다. |

public API endpoint 후보를 정할 때는 다음 순서로 확인합니다.

1. 일반적으로 많이 쓰이는 형태인지 확인합니다.
2. API 소비자가 내부 구현 지식 없이 이해할 수 있는지 확인합니다.
3. 프로젝트의 기존 endpoint와 문맥이 이어지는지 확인합니다.
4. resource-oriented 원칙이 내부 구현 정책을 외부에 노출시키지 않는지 확인합니다.
5. 비주류 이름을 추천해야 한다면 일반적이지 않다는 점과 선택 이유를 먼저 명시합니다.

endpoint는 public API 계약이므로 관용성과 기존 `/auth/sessions` namespace를 비교해 확정했습니다.

### 요청과 응답

요청:

```json
{
  "refreshToken": "raw-refresh-token"
}
```

응답:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "accessTokenExpiresAt": "2026-06-22T12:15:00Z",
  "refreshToken": "new-raw-refresh-token",
  "refreshTokenExpiresAt": "2026-07-22T12:00:00Z",
  "memberId": "0f3445fd-5f22-4f74-9a9d-1ff0c68a924a",
  "roles": ["ROLE_MEMBER"]
}
```

로그인 API가 refresh token을 response body로 전달하므로 재발급 API도 같은 방식을 우선합니다.
HttpOnly cookie로 바꾸려면 CSRF, CORS credential, SameSite 정책을 함께 확정해야 하므로 이번 작업에서는 제외합니다.

### 오류 응답

refresh token 실패는 다음 경우를 같은 401 오류 계약으로 응답합니다.

- refresh token 누락 또는 형식 오류
- hash lookup 실패
- 만료된 token
- 이미 폐기된 token
- 이미 rotation되어 교체된 token
- account 상태상 재발급 불가

오류 코드는 `INVALID_REFRESH_TOKEN`입니다.
reuse detection 여부는 클라이언트 응답에 노출하지 않고 감사 이벤트와 내부 보안 처리로만 남깁니다.

### UseCase 흐름

```text
POST /api/v1/auth/sessions/refresh
  -> RotateRefreshTokenController
  -> RotateRefreshTokenRequest validation
  -> RotateRefreshTokenCommand
  -> RotateRefreshTokenUseCase.execute
       -> RefreshTokenHashPort
       -> RefreshTokenLifecycleStorePort.findByHashForUpdate
       -> AccountAuthSubjectLookup
       -> AccessTokenIssuerPort
       -> RefreshTokenIssuerPort
       -> RefreshTokenLifecycleStorePort.rotate 또는 revokeFamily...
  -> RotateRefreshTokenResponse
```

실제 구현에서는 별도 `RefreshTokenRotationPolicy`와 `AuthEventRecorderPort`를 만들지 않았습니다.
현재 정책은 `RotateRefreshTokenService` 안에서 충분히 읽히는 크기이고, `auth_event` 기록은 refresh token lifecycle 저장과 같은 transaction에 있어야 하므로 `RefreshTokenLifecycleStorePort` 구현체가 함께 처리합니다.

### Application 모델

- UseCase: `RotateRefreshTokenUseCase`
- Command: `RotateRefreshTokenCommand`
- Result: `RotatedRefreshTokenResult`
- Web Request: `RotateRefreshTokenRequest`
- Web Response: `RotateRefreshTokenResponse`

재발급이라는 사용자 표현보다 rotation이라는 서버 정책을 타입명에 드러내도록 선택했습니다.
외부 API 문서와 사용자-facing 설명에서는 "session refresh" 또는 "refresh token 재발급"이라고 부르고, 코드에서는 기존 token 폐기와 새 token 연결까지 포함하는 `RotateRefreshToken`을 사용합니다.

### Port

- `RefreshTokenHashPort`: refresh token 원문을 저장용 hash로 변환합니다.
- `RefreshTokenLifecycleStorePort`: token hash 잠금 조회, 새 refresh token 저장, 기존 token 폐기, token family 폐기, 감사 이벤트 기록을 transaction 안에서 수행합니다.
- `AccessTokenIssuerPort`: 현재 account subject와 role 기준으로 access token을 발급합니다.
- `RefreshTokenIssuerPort`: 새 refresh token 원문, hash, 만료 시각을 생성합니다.
- `AccountAuthSubjectLookup`: `account.api` 공개 계약으로 account public id, 최신 상태, 활성 role code를 조회합니다.

lookup과 mutation은 같은 token row lock/transaction 경계에 묶이므로 하나의 lifecycle store port로 유지합니다.
감사 이벤트도 rotation과 family 폐기 저장이 rollback될 때 함께 rollback되어야 하므로 같은 persistence adapter 안에 둡니다.

### Transaction과 동시성

rotation은 하나의 transaction에서 처리합니다.
동시에 같은 refresh token이 제출될 수 있으므로 token row를 잠그고 한 요청만 rotation을 완료하게 합니다.

완료 당시 선택한 구현 계약:

- token hash로 refresh token row를 조회할 때 pessimistic write lock을 사용합니다.
- lock 획득 후 `expires_at`, `revoked_at`, `replaced_by_token_id`를 다시 확인합니다.
- 새 refresh token을 저장한 뒤 기존 token을 `ROTATED`와 `replaced_by_token_id`로 갱신합니다.
- 이미 폐기되었거나 교체된 token이면 family 활성 token을 `REUSE_DETECTED`로 폐기합니다.
- token lookup 실패처럼 row가 없으면 lock 대상이 없으므로 일반 invalid token 실패로 처리합니다.

이 방식은 새 schema 없이 기존 `refresh_token` 구조와 pessimistic write lock으로 구현했습니다.

### Reuse와 만료의 판정 우선순위

폐기·교체 상태는 token의 현재 사용 가능 여부뿐 아니라 이미 발급한 family가 탈취됐을 수
있다는 보안 신호입니다. 따라서 row lock 뒤에는 만료 시각보다 reuse 상태를 먼저 판정합니다.

| token 상태 | 처분 | 감사 결과 | public 응답 |
| --- | --- | --- | --- |
| `revoked_at` 또는 `replaced_by_token_id` 있음 | 활성 family를 `REUSE_DETECTED`로 폐기 | `TOKEN_REFRESH/FAILURE/TOKEN_REUSE_DETECTED` | `401 INVALID_REFRESH_TOKEN` |
| 폐기·교체 이력 없음, 만료됨 | 상태 변경 없이 거부 | 기록하지 않음 | `401 INVALID_REFRESH_TOKEN` |
| 폐기·교체 이력 없음, 유효함 | account 상태 확인 뒤 rotation 진행 | 성공 또는 account 상태 거부 정책 적용 | 기존 계약 유지 |

reuse 후보가 동시에 만료됐더라도 첫 번째 행을 적용합니다. 만료는 token의 시간상 유효성만
끝내지만, 폐기·교체 이력은 이미 사용된 token이 다시 제출됐다는 사실을 나타내므로 활성
replacement를 남기지 않습니다. 반대로 폐기·교체 이력이 없는 단순 만료에는 family 폐기나
reuse 감사 이벤트를 만들지 않습니다.

이 우선순위는 `RotateRefreshTokenService`가 소유합니다. 기존
`RefreshTokenRecord.isReuseCandidate()`와 lifecycle store port로 충분히 표현되므로 별도
`RefreshTokenRotationPolicy`, port, schema를 추가하지 않습니다.

### Account 상태 정책

refresh token은 로그인 시점의 account 상태를 영구 보장하지 않습니다.
재발급 시점마다 account 상태와 role을 다시 조회해야 합니다.

| 상태 | 재발급 정책 |
| --- | --- |
| `PENDING` | 로그인 정책과 동일하게 제한 access token 발급 |
| `ACTIVE` | full access token 발급 |
| `SUSPENDED` | 재발급 거부, refresh token family 폐기 |
| `WITHDRAWN` | 재발급 거부, refresh token family 폐기 |

`SUSPENDED`, `WITHDRAWN` refresh 요청은 거부하고 해당 token family를 폐기합니다.
계정 정지/탈퇴 기능 자체가 아직 준비되지 않았더라도, refresh 요청 시점에 현재 account 상태가 이 값이면 token family를 더 유지하지 않습니다.

### Auth Event와 Logging

완료 당시 성공한 rotation은 `TOKEN_REFRESH`로, 이미 폐기되었거나 교체된 refresh token의 재사용 탐지는 `TOKEN_REUSE_DETECTED`로 기록했습니다. 후속 [Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md)는 이를 각각 `TOKEN_REFRESH/SUCCESS`와 `TOKEN_REFRESH/FAILURE/TOKEN_REUSE_DETECTED`로 정규화했습니다. 현재 계약은 [Auth Event 정책](../../../domain/120-auth-event-policy.md)이 소유합니다.

일반 application log는 장애 진단용으로만 사용하고, 다음 값은 남기지 않습니다.

- refresh token 원문
- access token 원문
- token hash
- Authorization header
- Cookie
- request body
- 원문 email

## 기술 기본값

### 동시성 제어 방식

- 선택: refresh token row에 pessimistic write lock을 잡고 transaction 안에서 rotation합니다.
- 이유: 동시에 같은 token이 들어와도 하나만 성공해야 하며, 기존 schema로 구현 가능합니다.

## 확정된 결정

### Refresh Token 재발급 endpoint

- 결정: refresh token 재발급 endpoint는 `POST /api/v1/auth/sessions/refresh`입니다.
- 이유: 로그인 endpoint인 `POST /api/v1/auth/sessions`와 이어지고, 외부 API 사용자가 "인증 session 갱신"으로 바로 이해할 수 있습니다.
- 선택하지 않은 대안:
  - `POST /api/v1/auth/refresh`: 많이 쓰이는 단순 형태지만 session 맥락이 덜 드러납니다.
  - `POST /api/v1/auth/tokens/refresh`: 많이 쓰이는 형태지만 access token 갱신과 refresh token rotation의 범위가 이름에서 덜 분명합니다.
  - `POST /api/v1/auth/refresh-token-rotations`: 내부 rotation 정책은 정확히 드러나지만 public endpoint로는 일반적이지 않고 구현 정책을 과하게 노출합니다.

### Reuse Detection 포함

- 결정: 첫 재발급 구현에 reuse detection을 포함합니다.
- 이유: 기존 schema가 `family_id`, `replaced_by_token_id`, `REUSE_DETECTED`를 이미 준비했고, rotation만 구현하고 reuse detection을 미루면 탈취 token 재사용을 탐지해도 활성 family를 남길 수 있습니다.

### Reuse Detection 판정 우선순위

- 결정: `revoked_at` 또는 `replaced_by_token_id`가 있는 token은 만료 여부와 관계없이 reuse로
  판정하며, reuse 후보가 아닐 때만 만료를 검사합니다.
- 이유: reuse 상태는 활성 replacement family를 폐기해야 하는 보안 신호이고, 단순 만료는
  상태 변경과 감사 기록이 없는 유효성 실패입니다. 만료를 먼저 적용하면 탈취 신호와 활성
  family가 남습니다.
- 배제 대안: 만료를 먼저 검사하는 방식은 동일 401 응답은 유지하지만 family 폐기와 감사
  계약을 누락하므로 사용하지 않습니다.
- 재검토 조건: token family 모델이나 reuse event 정책이 영구 SOT에서 변경됩니다.

### Refresh Token 전달 방식

- 결정: 로그인 API와 동일하게 request/response body를 사용합니다.
- 이유: 로그인 문서에서 response body 전달이 확정되었고, cookie 기반으로 바꾸려면 CSRF/CORS 정책을 함께 확정해야 합니다.
- 재검토 조건: 브라우저 웹 클라이언트만 지원하고 HttpOnly Secure cookie, CSRF, CORS credential, SameSite 정책을 함께 확정합니다.

### Account 상태별 Refresh 정책

- 결정:
  - `ACTIVE`: full access token 발급
  - `PENDING`: 제한 access token 발급
  - `SUSPENDED`, `WITHDRAWN`: 재발급 거부
- 추가 결정: `SUSPENDED`, `WITHDRAWN` account의 refresh 요청이 들어오면 해당 token family를 폐기합니다.

### 내부 정책 이름과 외부 endpoint 이름 분리

- 결정: 외부 endpoint는 관용성과 소비자 이해도를 우선하고, 내부 코드 이름은 rotation 정책을 정확히 드러냅니다.
- 외부 endpoint: `POST /api/v1/auth/sessions/refresh`
- 실제 내부 이름: `RotateRefreshTokenUseCase`, `RotateRefreshTokenService`, `RefreshTokenLifecycleStorePort`
- 이유: public API는 내부 구현 정책보다 API 사용자 이해와 기존 endpoint 맥락을 우선해야 합니다.
- 검토 결과: 별도 `RefreshTokenRotationPolicy` type은 만들지 않았습니다. 정책이 독립 객체를 정당화할 만큼 분리되지 않아 `RotateRefreshTokenService`가 소유합니다.

## 관련 문서

- **선행 token·schema 결과**: [DB Schema](001-account-auth-db-schema.md), [회원 로그인](005-member-login-api.md), [인증 토큰 기반](006-auth-token-foundation.md)
- **업무·데이터 기준**: [Account domain](../../../domain/100-account.md), [120 Auth Event 정책](../../../domain/120-auth-event-policy.md), [Account 데이터 모델](../../../project/220-account-auth-data-model.md)
- **구조·API 기준**: [모듈 지도](../../../project/210-module-map.md), [경계와 매핑](../../../architecture/220-boundary-and-mapping-rules.md), [REST API 설계](../../../architecture/230-rest-api-design.md)
- **보안·logging 기준**: [Spring Security](../../../tech-stack/220-spring-security.md), [로깅 기준](../../../tech-stack/280-logging.md)
- **설계 절차**: [설계 워크플로우](../../../workflow/110-design-workflow.md)
