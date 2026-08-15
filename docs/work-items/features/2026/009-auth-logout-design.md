# 인증 로그아웃 API 상세 설계

## 문서 내 진입점

| 확인할 내용 | 위치 |
| --- | --- |
| API·token·transaction 선택과 배제 대안 | [API Contract 상세](#api-contract-상세), [Token Lifecycle 상세](#token-lifecycle-상세), [Access Token 상세](#access-token-상세) |
| 감사·모듈 경계 | [Audit Event 상세](#audit-event-상세), [Module Boundary 상세](#module-boundary-상세) |

## 배경

비밀번호 로그인 API는 access token과 refresh token을 발급하고, refresh token 원문은 응답 body로 한 번만 전달합니다.
서버에는 refresh token 원문이 아니라 SHA-256 hash와 `family_id`, 발급/만료 시각을 저장합니다.

로그인 후 실제 세션 생명주기를 운용하려면 사용자가 보유한 refresh token을 명시적으로 폐기하는 로그아웃 API가 필요합니다.
현재 구현에는 refresh token 재발급, rotation과 reuse detection 기반이 존재합니다.
따라서 이번 로그아웃 작업은 기존 refresh token lifecycle 저장소와 감사 이벤트 저장 패턴을 확장해 단일 refresh token 폐기만 다룹니다.

## API Contract 상세

### 선택 근거

인증 API 관용성을 REST resource 순수성보다 우선합니다.

### 선택

`POST /api/v1/auth/logout`을 선택합니다.

### 영향 범위

- HTTP method와 path
- request body field 이름
- 성공 status code
- validation 실패와 token 상태 실패의 구분

### 관용 후보

| 후보 | 관용성 | 설명 |
| --- | --- | --- |
| `POST /api/v1/auth/logout` | 높음 | 인증 API에서 가장 흔한 logout 표현입니다. 내부 구현이 refresh token 폐기여도 소비자는 로그아웃으로 이해합니다. |
| `POST /api/v1/auth/sessions/logout` | 중간 | 기존 `auth/sessions` namespace와 이어지지만 path에 action이 들어갑니다. |
| `DELETE /api/v1/auth/sessions/current` | 중간 | session resource 삭제로는 자연스럽지만 current session을 cookie나 access token으로 식별하는 구조에 더 적합합니다. |

### 프로젝트 일관성 후보

| 후보 | 일관성 | 설명 |
| --- | --- | --- |
| `POST /api/v1/auth/sessions` | 기존 로그인 endpoint | 로그인은 session 생성으로 모델링되어 있습니다. |
| `DELETE /api/v1/auth/sessions/current` | session resource 관점에서 일관 | 단, 현재 refresh token이 body로 전달되므로 `current` 식별 방식이 모호합니다. |
| `POST /api/v1/auth/sessions/revocations` | 내부 token 폐기와 일관 | 하지만 소비자에게 `revocation`은 내부 보안 정책을 드러내는 비주류 표현입니다. |

### 추천 후보

```http
POST /api/v1/auth/logout
Content-Type: application/json
```

```json
{
  "refreshToken": "..."
}
```

성공 응답:

```http
204 No Content
```

### 관용성 판정과 비주류 여부

- `POST /api/v1/auth/logout`은 인증 API 영역에서 관용성이 높습니다.
- REST resource 순수성은 낮지만, 인증 API에서는 logout 같은 command endpoint가 널리 쓰입니다.
- 내부 구현인 refresh token revocation을 public path에 드러내지 않으므로 소비자 이해도가 높습니다.
- 비주류 후보가 아닙니다.

### 선택하지 않는 대안

- `POST /api/v1/auth/sessions/revocations`
  - 제외 이유: resource처럼 보이지만 소비자가 이해해야 하는 개념이 `logout`이 아니라 `revocation`으로 바뀝니다. 내부 token 폐기 정책을 path에 드러내는 비주류 후보입니다.
- `DELETE /api/v1/auth/sessions/current`
  - 제외 이유: refresh token이 HttpOnly cookie로 전달되어 서버가 current session을 자연스럽게 식별할 때 적합합니다. 현재는 request body token 제출이 필요합니다.
- `DELETE /api/v1/auth/sessions`
  - 제외 이유: DELETE request body 의존은 클라이언트와 프록시 호환성 측면에서 피하는 편이 낫습니다.

### 재검토 조건

- refresh token 저장 방식이 HttpOnly cookie로 바뀝니다.
- session 목록 조회와 특정 session 삭제 기능이 생깁니다.
- OAuth/OIDC logout 표준 흐름을 제공하게 됩니다.

## Token Lifecycle 상세

### 선택 근거

클라이언트 편의와 token 상태 미노출을 우선합니다.

### 선택

validation 오류를 제외한 token 상태 문제는 모두 `204 No Content`로 응답합니다.

### 영향 범위

- 활성 token 폐기
- 존재하지 않는 token 처리
- 만료 token 처리
- 이미 폐기된 token 처리
- 동일 token 로그아웃 재호출 처리

### 상세 초안

| 입력 token 상태 | 서버 처리 | 응답 |
| --- | --- | --- |
| 활성 refresh token | `revoked_at`, `revoked_reason=LOGOUT` 기록 | `204 No Content` |
| 존재하지 않는 token | 상태 변경 없음 | `204 No Content` |
| 만료 token | 상태 변경 없음 | `204 No Content` |
| 이미 폐기된 token | 상태 변경 없음 | `204 No Content` |
| blank token | request validation 실패 | `400 INVALID_REQUEST` |

### 추천 이유

- 로그아웃은 클라이언트가 보유 token을 폐기하는 목적의 작업입니다.
- token 존재 여부나 상태를 오류로 구분하면 token 상태 추측 표면이 늘어납니다.
- 클라이언트는 로그아웃 성공 후 로컬 token을 버리면 되므로 실제 서버 상태 구분이 필요하지 않습니다.

### 선택하지 않는 대안

- invalid token `401`
  - 제외 이유: 클라이언트 로그아웃 복구가 복잡해지고 token 상태 추측 표면이 늘어납니다.
- already revoked token `409`
  - 제외 이유: 로그아웃은 멱등 동작으로 처리하는 편이 클라이언트와 재시도에 유리합니다.
- expired token `401`
  - 제외 이유: 만료 token이라도 클라이언트가 로그아웃을 끝내는 데 실패할 이유가 없습니다.

### 트레이드오프

- 클라이언트는 서버에서 실제로 token이 폐기됐는지 알 수 없습니다.
- invalid token 남용 추적은 이번 작업이 아니라 rate limit, abuse monitoring 후속 작업에서 다룹니다.

## Access Token 상세

### 선택 근거

이번 작업은 refresh token 단일 폐기에 집중하고, 즉시 access token 무효화 기반은 별도 작업으로 분리합니다.

### 선택

이번 로그아웃에서는 access token blacklist를 만들지 않습니다.
클라이언트가 보유 access token을 폐기하고, 서버는 짧은 만료 시간에 의존합니다.

### 영향 범위

- 로그아웃 시 access token 서버 저장소를 만들지 않습니다.
- 이미 발급된 access token은 만료 전까지 검증에 성공할 수 있습니다.
- refresh token 재사용은 로그아웃 이후 차단합니다.

### 추천 이유

- 현재 access token은 stateless JWT이며 DB에 저장하지 않습니다.
- blacklist는 별도 저장소, 만료 정리, 조회 비용, 장애 정책이 필요합니다.
- access token 만료 시간이 짧다는 기존 설계와 맞습니다.

### 선택하지 않는 대안

- access token blacklist
  - 제외 이유: 단일 로그아웃 기능보다 큰 보안 기반 작업입니다. 운영 저장소와 TTL cleanup 정책이 필요합니다.
- access token family/session version claim
  - 제외 이유: token 발급/검증 구조를 바꾸므로 별도 기반 작업입니다.

### 재검토 조건

- access token 만료 시간이 길어집니다.
- 관리자 강제 로그아웃이나 즉시 권한 회수가 필요해집니다.
- 보안 요구사항상 로그아웃 직후 bearer token 사용을 반드시 막아야 합니다.

## Audit Event 상세

### 선택 근거

실제 서버 상태 변경만 감사 이벤트로 기록합니다.

### 선택

활성 refresh token을 account와 연결해 폐기한 경우에만 `LOGOUT/SUCCESS` auth event를 기록합니다.
존재하지 않는 token, 만료 token, 이미 폐기된 token은 auth event를 기록하지 않습니다.

### 영향 범위

- event type: `LOGOUT`
- result: `SUCCESS`
- account 연결: 활성 token으로 account를 특정할 수 있는 경우만
- metadata: refresh token 원문, token hash, Authorization header를 기록하지 않음

### 추천 이유

- 로그아웃 성공 이벤트는 실제 서버 상태 변경을 추적하기 위한 감사 기록입니다.
- invalid token은 account를 신뢰성 있게 특정할 수 없습니다.
- invalid token 시도 추적은 인증 abuse monitoring 범위가 더 적절합니다.

### 선택하지 않는 대안

- invalid token도 `LOGOUT/FAILURE` 기록
  - 제외 이유: account를 특정할 수 없고, 의미 있는 운영 이벤트보다 noise가 많아질 수 있습니다.
- token hash를 metadata에 저장
  - 제외 이유: 원문 token은 아니지만 refresh token 추적 값을 감사 metadata에 남길 필요가 없습니다.

## Transaction Policy 상세

### 선택 근거

token 상태와 감사 이벤트의 불일치를 방지합니다.

### 선택

활성 token 폐기와 `LOGOUT/SUCCESS` auth event 저장은 같은 transaction으로 처리합니다.

### 추천 이유

- token이 폐기됐는데 감사 이벤트가 없거나, 감사 이벤트만 있고 token이 살아 있는 상태를 피합니다.
- 로그인 성공 저장 정책과 같은 방향입니다.
- 로그아웃은 단일 DB 상태 변경이므로 보상 transaction이나 outbox가 필요하지 않습니다.

### 선택하지 않는 대안

- token 폐기 후 event best-effort 저장
  - 제외 이유: 감사 로그 누락이 발생할 수 있습니다.
- outbox 기반 event 저장
  - 제외 이유: 외부 비동기 전파가 아니라 내부 감사 레코드 저장이므로 과합니다.

## Module Boundary 상세

### 선택 근거

이번 로그아웃은 `auth` 모듈 안에서 처리합니다.

### 영향 범위

기존 프로젝트 문서 기준으로 `auth` 모듈이 인증 session과 token lifecycle을 소유합니다.
이번 로그아웃도 `auth` 모듈에 둡니다.

### 적용 기준

- web controller, command, use case, application service는 `auth` 내부에 둡니다.
- account 내부 구현을 직접 참조하지 않습니다.
- refresh token 폐기와 auth event 기록은 기존 `auth` refresh-token lifecycle persistence 경계를 확장합니다.
- `LogoutUseCase`, `LogoutService`, `LogoutController`가 입력 경계를 소유하고 `RefreshTokenLifecycleStorePort`와 `RefreshTokenPersistenceAdapter`가 폐기 transaction을 소유합니다.

## 관련 문서

- **선행 session lifecycle**: [회원 로그인](005-member-login-api.md), [인증 토큰 기반](006-auth-token-foundation.md), [Refresh Token 재발급](007-refresh-token-reissue-api.md)
- **후속 Account 범위**: [Account Backlog](../../backlog/2026/003-account-auth-follow-up.md)
- **업무·데이터 기준**: [Account domain](../../../domain/100-account.md), [Account 데이터 모델](../../../project/220-account-auth-data-model.md)
- **구조·API·보안 기준**: [모듈 지도](../../../project/210-module-map.md), [REST API 설계](../../../architecture/230-rest-api-design.md), [Spring Security](../../../tech-stack/220-spring-security.md)
- **설계 절차**: [설계 워크플로우](../../../workflow/110-design-workflow.md)
