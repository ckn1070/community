# 계정 비밀번호 재설정 상세 설계


## 배경

로그인된 사용자의 비밀번호 변경은 access token과 현재 비밀번호를 사용합니다.
비밀번호를 잊은 사용자는 현재 비밀번호를 제출할 수 없으므로 account email로 전달한 1회용 reset token이 별도 인증 수단이 됩니다.
관리자와 일반 회원은 같은 account 모델을 사용하므로 reset 흐름도 account 공통 기능으로 설계했습니다.

## 용어 구분

| 용어 | 인증 수단 | 결과 | 이번 범위 |
| --- | --- | --- | --- |
| 비밀번호 변경 | access token과 현재 password | 로그인된 account의 credential 변경 | 제외 |
| 비밀번호 재설정 | email로 받은 reset token | 기존 account의 credential 복구 | 포함 |
| 임시 비밀번호 초기화 | 운영 권한과 임시 password | 다음 로그인 후 변경 강제 | 제외 |
| 관리자 초대 수락 | invitation token | 새 관리자 account 생성 | 제외 |

## 확정 결정

### D1. API 계약

- 배경: 로그인 상태 password change와 비로그인 reset은 인증 수단이 다릅니다.
- 검토한 선택지: 기존 password endpoint에 token 옵션 추가, email+token 완료 요청, 별도 reset resource
- 선택: 요청 `POST /api/v1/auth/password-resets`, 확인 `POST /api/v1/auth/password-resets/confirm`
- 선택 근거: 인증 수단과 실패 계약을 분리하고 token이 대상 account를 결정하게 합니다.
- 영향: 두 endpoint는 public allowlist이며 요청은 `202`, 확인은 `204`를 반환합니다.
- 배제한 대안: 기존 endpoint 통합은 계약을 혼합하고 email 재입력은 대상 선택 경계를 흐립니다.
- 재검토 조건: 인증 복구를 단일 challenge resource로 통합하는 제품 API가 확정됩니다.

### D2. Token 저장 구조

- 배경: 이메일 인증과 reset은 목적만 다른 1회용 token lifecycle입니다.
- 검토한 선택지: 전용 reset table, 기존 verification token 확장, stateless signed token
- 선택: `account_verification_token`에 `PASSWORD_RESET` purpose를 추가합니다.
- 선택 근거: hash, expiry, consumed, revoked 구조를 중복하지 않고 open token 단일성을 재사용합니다.
- 영향: raw token은 저장하지 않고 SHA-256 hash만 저장합니다.
- 배제한 대안: 전용 table은 lifecycle을 중복하고 stateless token은 소비·강제 폐기 통제가 약합니다.
- 재검토 조건: account가 없는 recovery challenge나 서로 다른 보존 정책이 필요합니다.

### D3. 대상 Account 상태

- 배경: `PENDING` 사용자도 이메일 인증 전 비밀번호를 잊을 수 있습니다.
- 검토한 선택지: `ACTIVE`만 허용, `ACTIVE`와 `PENDING` 허용, 모든 상태 허용
- 선택: `ACTIVE`와 `PENDING`만 token 발급 대상으로 둡니다.
- 선택 근거: credential 복구와 account 활성화는 다른 정책입니다.
- 영향: `PENDING`은 reset 후에도 `PENDING`을 유지합니다.
- 배제한 대안: `ACTIVE` 전용은 복구 경로를 막고, 정지·탈퇴 account 허용은 운영 정책을 우회합니다.
- 재검토 조건: 상태별 별도 support 복구 정책이 생깁니다.

### D4. TTL과 Cooldown

- 배경: token 노출 시간과 반복 요청 비용을 함께 통제해야 합니다.
- 검토한 선택지: 긴 TTL·무제한 요청, 짧은 TTL·강한 cooldown, 30분·60초
- 선택: TTL 30분, cooldown 60초를 사용합니다.
- 선택 근거: email 확인 시간을 제공하면서 token 노출과 중복 provider 호출을 제한합니다.
- 영향: cooldown 중에는 기존 open token을 유지하고, cooldown 밖 재요청은 기존 open token을 폐기합니다.
- 배제한 대안: 무제한 요청은 abuse 비용이 크고 긴 cooldown은 정상 복구를 방해합니다.
- 재검토 조건: 실제 발송 지연, abuse 지표, 고객 지원 데이터가 다른 값을 요구합니다.

### D5. Reset 완료 효과

- 배경: 비밀번호가 바뀌면 기존 session을 계속 신뢰할 수 없습니다.
- 검토한 선택지: password만 변경, refresh token만 폐기, 관련 상태를 원자적으로 변경
- 선택: token 소비, password hash 갱신, `password_change_required=false`, active refresh token `PASSWORD_CHANGED` 폐기, 감사 이벤트를 한 transaction으로 처리합니다.
- 선택 근거: 일부 상태만 반영되는 복구 실패를 막고 탈취 session을 종료합니다.
- 영향: transaction 실패 시 모든 변경이 rollback됩니다.
- 배제한 대안: password만 바꾸면 기존 refresh session이 유지되고 부분 transaction은 상태 불일치를 만듭니다.
- 재검토 조건: global session registry나 별도 credential version 정책을 도입합니다.

### D6. 감사 이벤트

- 배경: 복구 요청 결과와 실제 credential 변경을 구분해 감사해야 합니다.
- 검토한 선택지: 기존 password change event 재사용, 새 request·complete event, provider delivery event 분리
- 선택: `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED`를 사용합니다.
- 선택 근거: 비로그인 복구 흐름과 로그인 상태 변경을 구분합니다.
- 영향: account 없음과 invalid token처럼 상태 변화가 없는 실패는 enumeration·민감정보 정책에 따라 저장하지 않습니다.
- 배제한 대안: 기존 event 재사용은 인증 수단과 업무 의미를 섞습니다.
- 재검토 조건: provider delivery 감사나 보안 탐지용 실패 이벤트 요구가 생깁니다.

### D7. Notification 경계

- 배경: account는 reset token lifecycle을, notification은 provider와 template을 소유해야 합니다.
- 검토한 선택지: account 내부 Resend 호출, notification public API, 별도 messaging worker
- 선택: notification module의 public sender contract와 Resend adapter를 사용합니다.
- 선택 근거: provider 세부사항과 template, 오류 변환을 account에서 분리합니다.
- 영향: account는 `notification.internal`을 참조하지 않습니다.
- 배제한 대안: account 내부 호출은 module 책임을 섞고 worker는 현재 신뢰성 요구보다 큽니다.
- 재검토 조건: durable retry, queue, provider 다중화가 필요합니다.

## 기능 계약

### Public API

| API | Request | Success | 공개 응답 정책 |
| --- | --- | --- | --- |
| `POST /api/v1/auth/password-resets` | `{ "email": "user@example.com" }` | `202 Accepted` | account 존재, 상태와 발송 결과를 구분하지 않습니다. |
| `POST /api/v1/auth/password-resets/confirm` | `{ "token": "...", "newPassword": "...", "newPasswordConfirmation": "..." }` | `204 No Content` | token이 대상 account를 결정하며 email을 다시 받지 않습니다. |

| 상황 | HTTP status | Error code |
| --- | --- | --- |
| request validation 실패 | `400 Bad Request` | 공통 validation error |
| token 형식 오류, 미존재, 소비 또는 폐기 | `400 Bad Request` | `PASSWORD_RESET_TOKEN_INVALID` |
| token 만료 | `400 Bad Request` | `PASSWORD_RESET_TOKEN_EXPIRED` |

### Token Lifecycle

| 상황 | 처리 |
| --- | --- |
| 첫 요청 | `PASSWORD_RESET` / `EMAIL` token을 생성하고 발송을 시도합니다. |
| 60초 cooldown 중 재요청 | 기존 open token을 유지하고 새 token과 새 발송을 만들지 않습니다. |
| cooldown 이후 재요청 | 기존 open token을 폐기하고 새 token을 생성합니다. |
| confirm 성공 | token을 소비하고 password credential을 갱신합니다. |
| confirm 실패 | password와 account 상태를 변경하지 않습니다. |

- raw token은 저장하지 않고 SHA-256 hash만 저장합니다.
- 같은 `(account_id, purpose, delivery_channel)`에는 open token 하나만 허용합니다.
- token TTL은 30분입니다.
- `ACTIVE`와 `PENDING`만 발급 대상이며 `SUSPENDED`, `WITHDRAWN`, 미존재 account는 no-op입니다.
- `PENDING`은 reset 완료 후에도 `PENDING`을 유지합니다.

### Reset 완료 Transaction

아래 변경은 하나의 transaction으로 처리합니다.

1. reset token의 valid 상태 확인과 소비
2. 새 password hash 저장
3. `password_change_required = false`
4. 활성 refresh token을 `PASSWORD_CHANGED` 사유로 폐기
5. `PASSWORD_RESET_COMPLETED` 감사 이벤트 저장

어느 단계라도 실패하면 전체 변경을 rollback합니다.

### 감사 이벤트와 민감정보

| Event | Result | 생성 조건 |
| --- | --- | --- |
| `PASSWORD_RESET_REQUESTED` | `SUCCESS` 또는 `FAILURE` | 대상 account의 reset 발송 시도 결과 |
| `PASSWORD_RESET_COMPLETED` | `SUCCESS` | token 소비와 credential 갱신 성공 |

account 없음, 비대상 account와 invalid token처럼 상태 변화가 없는 실패는 enumeration과 민감정보 노출을 피하기 위해 저장하지 않습니다.
raw token, password 원문, password hash, 원문 email과 provider response body는 log, API response, audit metadata에 남기지 않습니다.

## 기반 구조와 경계

이 작업은 기존 account verification token과 notification 기반을 확장하는 혼합 작업입니다.
새 범용 복구 플랫폼이나 별도 messaging worker를 만들지 않습니다.

| 경계 | 소유 책임 |
| --- | --- |
| `account` | reset token lifecycle, account 상태 판단, password credential 갱신, refresh token 폐기 |
| `notification.api` | password reset 발송 요청 공개 계약 |
| `notification.internal` | Resend mapping, HTML/text template, provider 오류 분류 |
| Web | request validation과 public response contract |
| Persistence | token lock·소비, credential update, refresh token revoke와 audit insert의 원자 처리 |

`account`는 `notification.api`만 참조하고 `notification.internal`이나 Resend SDK를 직접 참조하지 않습니다.

### Boundary Model

| 경계 | 모델 |
| --- | --- |
| Web request | `RequestPasswordResetRequest`, `ConfirmPasswordResetRequest` |
| Application command | `RequestPasswordResetCommand`, `ConfirmPasswordResetCommand` |
| Application port | reset request/confirm use case, token persistence, credential reset, notification sender |
| Domain model | `EmailAddress`, `PasswordHash`, `PasswordResetTokenHash`, `AccountStatus` |
| Persistence model | verification token purpose 확장, credential update, refresh token revoke, password reset auth event |
| Notification model | `PasswordResetNotificationRequest`, `PasswordResetNotificationSender`, provider mapping과 template |

별도 `Account` aggregate 행위를 추가하지 않고 application service와 persistence port가 여러 row의 원자적 변경을 조정합니다.
새 reset payload나 persistence model에 `Aggregate` 이름을 붙이지 않습니다.

## 최종 동작 계약

- 요청 API는 account 존재, 상태, 발송 여부와 무관하게 동일한 public 응답을 사용합니다.
- `ACTIVE`와 `PENDING`만 token을 만들고 `SUSPENDED`, `WITHDRAWN`, 미존재 account는 no-op입니다.
- unknown, consumed, revoked token은 invalid error, expired token은 expired error로 처리합니다.
- raw token, password 원문, password hash, 원문 email은 log, API response, audit metadata에 노출하지 않습니다.

## 환경변수 계약

- `RESEND_API_KEY`
- `NOTIFICATION_EMAIL_FROM`
- `PASSWORD_RESET_BASE_URL`

## 관련 문서

- **공유 verification·notification 기반**: [이메일 인증](008-member-email-verification.md)
- **관리자 password lifecycle**: [관리자 계정 기반](010-admin-account-foundation.md), [관리자 초대](011-admin-invitations.md)
