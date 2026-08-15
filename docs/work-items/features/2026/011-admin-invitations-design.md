# 관리자 초대/수락 상세 설계

## 문서 내 진입점

| 확인할 내용 | 위치 |
| --- | --- |
| 결정별 대안과 근거 | [확정 결정 근거](#확정-결정-근거) |
| DB·token·API·보안 계약 | [확정된 설계](#확정된-설계) |

## 배경

초기 최고 관리자 bootstrap 이후에는 관리자 계정을 공유하지 않고 추가 관리자를 초대할 수 있어야 합니다.
기존 account 모델은 일반 회원과 관리자를 같은 인증 주체로 다루고, 역할은 `account_role_assignment`로 부여합니다.
관리자 초대는 일반 회원 자체 가입과 보안 경계가 다르므로 별도 endpoint, 별도 use case, 별도 authorization policy로 설계합니다.

## 확정 결정 근거

### D1. 초대 생성 권한

- 선택: `ROLE_SUPER_ADMIN`만 관리자 초대를 생성합니다.
- 결정 범위: 관리자 초대 생성 use case와 API authorization policy입니다.

`ROLE_ADMIN`은 콘텐츠 운영과 신고 처리 같은 일반 운영 권한으로 남기고, 관리자 계정 생명주기는 `ROLE_SUPER_ADMIN`이 소유합니다.
관리자 수를 늘리는 행위는 권한 구조와 감사 책임에 직접 영향을 주므로 최고 관리자 권한으로 제한하는 편이 안전합니다.

선택하지 않는 대안:

| 대안 | 제외 이유 |
| --- | --- |
| `ROLE_ADMIN`도 초대 가능 | 일반 관리자가 새 관리자를 계속 늘릴 수 있어 권한 확산을 통제하기 어렵습니다. |
| 운영 secret으로만 초대 가능 | bootstrap 이후 일상 운영 기능으로 쓰기 어렵고 감사 주체가 account로 남지 않습니다. |

### D2. 초대 가능한 관리자 role

- 선택: 첫 구현은 `ROLE_ADMIN` 초대만 허용합니다.
- 결정 범위: 초대 생성 request의 role 허용 목록과 role assignment 생성 정책입니다.

최고 관리자 추가는 계정 복구, 공동 운영, 비상 권한 정책과 함께 봐야 합니다.
따라서 첫 구현에서는 일반 관리자 초대만 열고, `ROLE_SUPER_ADMIN` 초대는 별도 정책 작업으로 분리합니다.

선택하지 않는 대안:

| 대안 | 제외 이유 |
| --- | --- |
| `ROLE_SUPER_ADMIN` 초대 허용 | 최고 관리자 증식과 복구 정책을 같이 결정해야 합니다. |
| role 선택 없이 항상 최고 관리자 | 권한 최소화 원칙과 맞지 않습니다. |

### D3. 기존 account 처리

- 선택: 초대 대상 email에 기존 account가 있으면 이번 범위에서는 거부합니다.
- 결정 범위: 초대 생성 전 email 중복 검사와 수락 시 account 생성 정책입니다.

기존 회원을 관리자로 승격하는 기능은 account 생성이 아니라 role assignment 추가입니다.
초대 수락과 기존 회원 승격을 같은 흐름에 넣으면 display name, password credential, email verification, 기존 session 처리 기준이 섞입니다.

선택하지 않는 대안:

| 대안 | 제외 이유 |
| --- | --- |
| 기존 회원이면 role만 추가 | 관리자 승격 기능이며 감사 이벤트, 사용자 동의, 기존 session 재발급 기준이 다릅니다. |
| 수락 시 기존 account를 찾아 병합 | account 생명주기와 가입 경계를 흐립니다. |

### D4. 초대 수락 account 상태

- 선택: 수락 성공 시 `ACTIVE`, `email_verified_at = now`, `password_change_required = false`로 생성합니다.
- 결정 범위: account, profile, password credential 생성 값입니다.

초대 링크는 특정 email로 전달되고, 수락 과정에서 token을 제출하므로 이메일 소유권 확인 역할을 합니다.
수락자가 이 과정에서 비밀번호를 직접 설정한다면 강제 비밀번호 변경 flag는 필요하지 않습니다.

선택하지 않는 대안:

| 대안 | 제외 이유 |
| --- | --- |
| `PENDING` 생성 후 별도 이메일 인증 | 초대 email token과 이메일 인증 token이 중복됩니다. |
| 임시 비밀번호 생성 후 변경 강제 | 초대 수락 UX가 불필요하게 복잡해지고 임시 비밀번호 전달 문제가 생깁니다. |

### D5. 초대 token 생명주기

- 선택: raw token은 저장하지 않고 SHA-256 hash만 저장하며 만료는 24시간입니다.
- 결정 범위: token 저장 방식, 만료 시간, 수락 검증 방식입니다.

관리자 초대 token은 권한 있는 account 생성으로 이어지므로 원문 저장을 피합니다.
24시간은 운영자가 초대 후 당일 처리하기에 충분하면서 장기 노출 위험을 제한하는 기본값입니다.

선택하지 않는 대안:

| 대안 | 제외 이유 |
| --- | --- |
| raw token 저장 | DB 유출 시 초대 수락으로 바로 이어질 수 있습니다. |
| 7일 이상 만료 | 운영 편의는 높지만 관리자 권한 획득 token이 오래 살아 있습니다. |
| 30분 이하 만료 | 관리자 초대는 이메일 인증보다 조직 내 확인 시간이 더 길 수 있습니다. |

### D6. 동일 email 재초대 정책

- 선택: 같은 email의 open 초대가 있으면 기존 초대를 폐기하고 새 초대를 발송합니다.
- 결정 범위: 초대 생성 use case의 중복 open 초대 처리입니다.

raw token은 저장하지 않으므로 기존 초대를 그대로 재발송할 수 없습니다.
별도 재발송 API 없이 운영자가 같은 email로 다시 초대하면 새 token을 발급하고 기존 open 초대를 폐기하는 편이 단순합니다.

선택하지 않는 대안:

| 대안 | 제외 이유 |
| --- | --- |
| open 초대가 있으면 오류 | 이메일 유실이나 만료 전 재발송 요구를 처리하기 어렵습니다. |
| 기존 token 재발송 | raw token을 저장하지 않는 정책과 충돌합니다. |

### D7. 초대 이메일 발송 경계

- 선택: 기존 `notification` 모듈을 확장하고 `ADMIN_INVITATION_BASE_URL`을 별도 환경변수로 둡니다.
- 결정 범위: notification public API, template, 설정 key입니다.

관리자 초대 이메일은 이메일 인증과 같은 provider와 template 조립 책임을 공유합니다.
다만 이동하는 화면과 copy는 다르므로 base URL은 분리하는 편이 설정 의도를 명확히 합니다.

선택하지 않는 대안:

| 대안 | 제외 이유 |
| --- | --- |
| account 모듈에서 직접 Resend 호출 | provider 세부사항이 account 내부로 들어와 notification 경계가 깨집니다. |
| `EMAIL_VERIFICATION_BASE_URL` 재사용 | 인증 화면 URL과 관리자 초대 화면 URL의 생명주기가 다릅니다. |

### D8. 별도 회수 API 포함 여부

- 선택: 별도 회수 API를 이번 범위에 포함합니다.
- 결정 범위: open 초대를 명시적으로 폐기하는 admin API, 권한, 감사 이벤트, 수락과의 상태 충돌 처리입니다.

회수 API를 추가하는 것이 무리일 정도로 큰 범위는 아닙니다.
어차피 `admin_invitation` table에는 `revoked_at` 또는 그에 준하는 폐기 상태가 필요하고, 재초대 정책도 기존 open 초대를 폐기해야 합니다.
잘못 보낸 초대를 24시간 TTL에만 맡기는 것은 관리자 권한 발급 기능의 운영 안전성 측면에서 약합니다.

따라서 최소 회수 API는 같은 작업에 포함하는 편이 더 낫습니다.
다만 초대 목록 조회, 상태 상세 조회, 재발송 전용 API, 관리자 화면 UI까지 함께 넓히지는 않습니다.
이번 범위에 포함한다면 회수는 `ROLE_SUPER_ADMIN`만 가능하고, open 초대에 대해서만 성공하며, 이미 수락된 초대는 회수할 수 없습니다.

선택하지 않는 대안:

| 대안 | 제외 이유 |
| --- | --- |
| 회수 API 제외 | 잘못 발송한 관리자 초대가 만료 전까지 계속 유효합니다. |
| 초대 row 삭제 | 감사 추적을 잃습니다. 폐기 시각을 남기는 방식이 더 안전합니다. |
| 회수와 함께 목록/상세/재발송 전용 API까지 포함 | 이번 작업의 핵심인 생성/수락보다 운영 관리 화면 성격이 커집니다. |

### D9. 감사 이벤트 taxonomy

- 선택: `ADMIN_INVITATION_CREATED`, `ADMIN_INVITATION_ACCEPTED`, `ADMIN_INVITATION_REVOKED`를 `auth_event`에 추가합니다.
- 결정 범위: `auth_event.event_type` check constraint와 persistence mapping입니다.

관리자 초대와 수락은 인증 주체와 역할 생성에 영향을 주는 보안 이벤트입니다.
기존 `ROLE_GRANTED`만으로는 초대 기반 생성과 직접 role 부여를 구분하기 어렵습니다.
회수 API를 같은 범위에 넣는다면 회수도 보안 이벤트이므로 생성/수락과 같은 taxonomy 안에서 남기는 편이 맞습니다.

선택하지 않는 대안:

| 대안 | 제외 이유 |
| --- | --- |
| `ROLE_GRANTED`만 사용 | 초대 생성과 수락 전후 흐름을 감사 로그에서 분리하기 어렵습니다. |
| 별도 admin audit table | 이번 범위에서는 기존 `auth_event` 확장으로 충분합니다. |
| 회수 이벤트만 생략 | 관리자 권한 발급 token 폐기를 감사 로그에서 추적하기 어렵습니다. |

### D10. Public/Admin API 계약

- 선택: 생성은 `POST /api/v1/admin/invitations`, 수락은 `POST /api/v1/admin/invitations/{invitationId}/acceptances`, 회수는 `DELETE /api/v1/admin/invitations/{invitationId}`로 둡니다.
- 결정 범위: HTTP method, path, request/response contract입니다.

초대 생성은 인증된 최고 관리자가 초대 리소스를 만드는 행위입니다.
초대 수락은 초대 리소스에 대한 수락 하위 리소스를 생성하는 행위로 표현합니다.
수락 API는 public endpoint가 되지만 raw token을 body로 받아야 하며, invitation id만으로는 수락할 수 없습니다.
수락 request는 새 관리자 account의 비밀번호를 최초 설정하는 흐름이므로 `password`와 `passwordConfirm`을 함께 받고, 두 값이 다르면 저장 전 `INVALID_REQUEST`로 거부합니다.

하이픈 자체는 이 프로젝트 REST 가이드의 기본 path segment 표기입니다.
다만 `admin-invitations`처럼 두 도메인 축을 한 segment에 묶는 것은 항상 최선이 아닙니다.
이 프로젝트는 로그인에서 `auth-sessions`가 아니라 `/auth/sessions`, 이메일 인증에서 `email-verification-confirmations`가 아니라 `/auth/verifications/email/confirm`을 선택했습니다.
같은 기준을 적용하면 관리자 영역은 `/admin`, 초대 리소스는 `/invitations`로 나누는 편이 더 일관적입니다.

`/api/v1/invitations/admin`은 초대 collection 아래에 `admin`이라는 하위 리소스나 action이 있는 것처럼 읽힙니다.
이 기능에서 `admin`은 초대의 하위 대상이 아니라 관리자 영역 또는 관리자 권한으로 수행하는 기능의 namespace입니다.
따라서 segment 순서는 `/admin/invitations`가 더 자연스럽습니다.

후보:

| 후보 | 판정 | 이유 |
| --- | --- | --- |
| `POST /api/v1/admin/invitations` | 선택됨 | admin namespace와 invitation resource를 분리해 기존 `/auth/sessions` 계열과 일관됩니다. |
| `POST /api/v1/admin/invitations/{invitationId}/acceptances` | 선택됨 | 초대에 대한 수락 생성으로 읽히며, raw token은 body에서 별도 검증합니다. |
| `DELETE /api/v1/admin/invitations/{invitationId}` | 선택됨 | open 초대 리소스를 폐기하는 최소 회수 API로 표현할 수 있습니다. |
| `POST /api/v1/invitations/admin` | 제외 | admin이 invitation의 하위 리소스나 action처럼 읽혀 namespace 의미가 뒤집힙니다. |
| `POST /api/v1/admin-invitations` | 보류 | 하이픈은 자연스럽지만 `admin` namespace와 invitation resource를 한 segment에 묶습니다. |
| `POST /api/v1/admin-invitations/{invitationId}/acceptances` | 보류 | 의미는 명확하지만 프로젝트의 namespace 분리 흐름과 덜 맞습니다. |
| `POST /api/v1/admin-invitations/acceptances` | 제외 | path는 짧지만 어떤 초대에 대한 수락인지 request body에 숨습니다. |
| `POST /api/v1/auth/admin-invitation-acceptance` | 제외 | auth action 이름이 커지고 리소스 경계가 흐립니다. |

### D11. 초대, 비밀번호 변경, 비밀번호 재설정 경계

- 선택: 관리자 초대 수락은 신규 account 생성 흐름으로 두고, 기존 account 비밀번호 재설정은 별도 기능으로 분리합니다.
- 결정 범위: 이번 관리자 초대/수락 작업의 포함/제외 범위와 후속 설계 문서 필요성입니다.

관리자 초대 수락은 초대받은 email 소유자가 invitation token으로 새 관리자 account를 만드는 흐름입니다.
이미 존재하는 account의 비밀번호를 바꾸거나, 일반회원이 잊어버린 비밀번호를 복구하는 흐름이 아닙니다.

현재 인증 흐름은 다음처럼 분리합니다.

| 흐름 | 대상 | 인증 수단 | 결과 | 이번 작업 포함 여부 |
| --- | --- | --- | --- | --- |
| bootstrap 초기 비밀번호 변경 | 최초 `ROLE_SUPER_ADMIN` | 로그인 access token + current password | `password_change_required = false` | 제외, 이미 구현됨 |
| 관리자 초대 수락 | 아직 account가 없는 초대 대상 email | invitation token | `ACTIVE` 관리자 account 생성 | 포함 |
| 기존 회원 관리자 승격 | 기존 account | 최고 관리자 인증과 별도 승격 정책 | 기존 account에 관리자 role 추가 | 제외 |
| 일반회원 비밀번호 재설정 | 기존 account | account email로 보낸 password reset token | 기존 account password hash 변경 | 완료 당시 제외, 후속 [012](012-account-password-reset.md)에서 구현 |
| emergency recovery | 최고 관리자 접근 불능 | 별도 운영 복구 절차 | 최고 관리자 접근 복구 | 제외, 별도 운영 설계 필요 |

일반회원 비밀번호 재설정은 이 작업과 분리한 뒤 후속 [계정 비밀번호 재설정](012-account-password-reset.md)에서 구현했습니다. 현재 reset 계약은 해당 Result와 [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)이 소유합니다.

선택하지 않는 대안:

| 대안 | 제외 이유 |
| --- | --- |
| 관리자 초대 수락에서 기존 account 비밀번호를 재설정 | 초대와 계정 복구가 섞이고, email 입력만으로 다른 account 변경을 시도하는 위험이 커집니다. |
| 비밀번호 재설정을 관리자 초대 작업에 포함 | 기존 account 보안, token purpose, enumeration 방지, session 폐기 설계가 별도 표면입니다. |
| bootstrap command를 최고 관리자 복구 수단으로 반복 사용 | 현재 운영 정책은 활성 최고 관리자가 있으면 bootstrap no-op이며, emergency recovery는 별도 절차가 필요합니다. |

### D12. 초대 role 참조 방식

- 선택: `admin_invitation.role_id`가 `auth_role(id)`를 FK로 참조하게 합니다.
- 결정 범위: `admin_invitation` DB schema, persistence mapping, 초대 생성 시 role 조회 방식입니다.

`role_code` 문자열만 저장하면 초대 table이 실제 역할 catalog와 분리됩니다.
기존 `account_role_assignment`도 `auth_role(id)`를 참조하므로, 초대가 수락 시 부여할 역할 역시 같은 기준으로 참조하는 편이 데이터 무결성에 맞습니다.
첫 구현에서 허용 role은 `ROLE_ADMIN` 하나지만, 그 제한은 초대 생성 use case가 `auth_role.code = 'ROLE_ADMIN'`만 선택하도록 검증합니다.

선택하지 않는 대안:

| 대안 | 제외 이유 |
| --- | --- |
| `role_code VARCHAR(50)`만 저장 | 존재하지 않는 role code를 막는 DB 참조 무결성이 없습니다. |
| `role_id`와 `role_code_snapshot`을 함께 저장 | role code가 안정 값이라면 snapshot 중복이 먼저 필요하지 않습니다. |

## 확정된 설계

### S1. DB schema 상세 설계

- 결정 범위: `admin_invitation` table, `auth_event` event type 확장, account/role FK, 상태 전이 constraint, index, migration 순서입니다.
- 구현 시작 시점의 최신 migration은 `V10__extend_auth_event_for_email_verification.sql`이었고, 이 작업의 DB 변경은 `V11__create_admin_invitation_table.sql`에 반영했습니다.

`admin_invitation`

| column                  | type                                      | null                     | 의미                      |
| ----------------------- | ----------------------------------------- | ------------------------ | ----------------------- |
| `id`                    | `BIGINT GENERATED BY DEFAULT AS IDENTITY` | `NOT NULL`               | 내부 PK                   |
| `public_id`             | `UUID`                                    | `NOT NULL`               | API path에 노출할 안정 식별자    |
| `email`                 | `VARCHAR(254)`                            | `NOT NULL`               | 초대 대상의 trim·lowercase email snapshot |
| `email_normalized`      | `VARCHAR(254)`                            | `NOT NULL`               | 중복 판단과 발송 대상 비교 기준      |
| `role_id`               | `BIGINT`                                  | `NOT NULL`               | 수락 시 부여할 `auth_role` 참조 |
| `token_hash`            | `CHAR(64)`                                | `NOT NULL`               | raw token SHA-256 hash  |
| `invited_by_account_id` | `BIGINT`                                  | `NOT NULL`               | 초대를 생성한 account         |
| `accepted_account_id`   | `BIGINT`                                  | nullable                 | 수락으로 생성된 account        |
| `revoked_by_account_id` | `BIGINT`                                  | nullable                 | 초대를 명시 회수한 account      |
| `issued_at`             | `TIMESTAMPTZ`                             | `NOT NULL`               | token 발급 시각             |
| `expires_at`            | `TIMESTAMPTZ`                             | `NOT NULL`               | token 만료 시각             |
| `accepted_at`           | `TIMESTAMPTZ`                             | nullable                 | 수락 시각                   |
| `revoked_at`            | `TIMESTAMPTZ`                             | nullable                 | 재초대 또는 회수로 폐기된 시각       |
| `created_at`            | `TIMESTAMPTZ`                             | `NOT NULL DEFAULT NOW()` | row 생성 시각               |
| `updated_at`            | `TIMESTAMPTZ`                             | `NOT NULL DEFAULT NOW()` | row 수정 시각               |

확정 constraint와 index:

| 항목 | 내용 |
| --- | --- |
| PK | `id` primary key |
| unique | `public_id`, `token_hash` |
| FK | `role_id` references `auth_role(id)` |
| FK | `invited_by_account_id`, `accepted_account_id`, `revoked_by_account_id` references `account(id)` |
| hash check | `token_hash`는 64자리 lowercase hex |
| timestamp check | `expires_at > issued_at`, `accepted_at >= issued_at`, `revoked_at >= issued_at`, `updated_at >= created_at` |
| terminal state check | `accepted_at`과 `revoked_at`은 동시에 존재할 수 없음 |
| accepted account check | `accepted_at`이 있으면 `accepted_account_id`가 있어야 하고, `accepted_at`이 없으면 `accepted_account_id`도 없어야 함 |
| revoked actor check | `revoked_at`이 있으면 `revoked_by_account_id`가 있어야 하고, `revoked_at`이 없으면 `revoked_by_account_id`도 없어야 함 |
| open email unique index | `(email_normalized) WHERE accepted_at IS NULL AND revoked_at IS NULL` |
| cleanup index | `(expires_at)` |
| 운영 조회 index | `(invited_by_account_id, issued_at DESC)` |

첫 구현의 초대 가능 role 제한은 DB check가 아니라 초대 생성 use case에서 `auth_role.code = 'ROLE_ADMIN'`만 선택하도록 검증합니다.
DB 참조 무결성은 `role_id` FK가 담당합니다.
같은 email 재초대는 같은 transaction에서 기존 open 초대의 `revoked_at`과 `revoked_by_account_id`를 먼저 채우고 새 초대를 저장합니다.
expired row도 `accepted_at IS NULL AND revoked_at IS NULL`이면 open row로 남아 있으므로, 재초대 use case는 만료 여부와 무관하게 기존 open row를 폐기한 뒤 새 row를 만듭니다.

`auth_event`는 기존 `chk_auth_event_type`을 교체해 다음 event type을 추가합니다.

| event_type | result | account_id | metadata |
| --- | --- | --- | --- |
| `ADMIN_INVITATION_CREATED` | `SUCCESS` | 초대 생성 관리자 account | `invitationPublicId`, `targetEmailHash`, `roleCode`, `previousInvitationRevoked`, `notificationResult` |
| `ADMIN_INVITATION_ACCEPTED` | `SUCCESS` | 생성된 관리자 account | `invitationPublicId`, `roleCode`, `invitedByAccountId` |
| `ADMIN_INVITATION_REVOKED` | `SUCCESS` | 회수 관리자 account | `invitationPublicId`, `targetEmailHash`, `reasonCode` |

`metadata`에는 raw email, raw token, token hash, 비밀번호, provider 원문 응답을 넣지 않습니다.
`targetEmailHash`는 application이 이미 정규화한 email UTF-8 bytes의 deterministic
SHA-256 lowercase hex입니다. 이 값은 동일 대상의 생성·회수 event를 결속하기 위한
식별 hash이지 keyed pseudonymization이 아니므로 API 응답·application log로 노출하지
않습니다. HMAC이나 다른 hash로 바꾸려면 기존 감사 row의 조회·migration과 key 수명주기를
별도 결정해야 합니다.

### S2. Token lifecycle

- raw token은 저장하지 않고 SHA-256 hash만 저장합니다.
- raw token은 이메일 링크에만 포함하고, 수락 API body에서 다시 제출합니다.
- token TTL은 24시간입니다.
- `open`: `accepted_at IS NULL AND revoked_at IS NULL`
- `valid`: open이고 `expires_at > now`
- `expired`: open이고 `expires_at <= now`
- `accepted`: `accepted_at IS NOT NULL`
- `revoked`: `revoked_at IS NOT NULL`
- 수락 성공 시 같은 transaction에서 `accepted_at`, `accepted_account_id`를 기록합니다.
- 회수 또는 재초대 시 같은 transaction에서 `revoked_at`, `revoked_by_account_id`를 기록합니다.

### S3. Account 생성 정책

수락 성공 시 새 account를 생성합니다.

| 대상 | 생성/설정 값 |
| --- | --- |
| `account` | 초대 email을 사용하고 `status = 'ACTIVE'`, `email_verified_at = now`로 생성 |
| `account_profile` | 수락 request의 `displayName`으로 생성 |
| `account_password_credential` | 수락 request의 `password`와 `passwordConfirm` 일치 여부를 확인한 뒤 `password`를 hash해 저장하고 `password_change_required = false`로 생성 |
| `account_role_assignment` | 초대 row의 `role_id`를 부여하고 `granted_by_account_id = invited_by_account_id`로 기록 |
| `auth_event` | `ADMIN_INVITATION_ACCEPTED` 성공 이벤트 기록 |

초대 대상 email에 기존 account가 있으면 생성과 수락 모두 거부합니다.
기존 회원 관리자 승격은 별도 기능으로 분리합니다.
display name 중복은 기존 `account_profile.display_name_normalized` unique constraint와 같은 오류 계약으로 처리합니다.

### S4. API contract

| 기능 | method/path | 인증 | 성공 응답 |
| --- | --- | --- | --- |
| 초대 생성 | `POST /api/v1/admin/invitations` | `SESSION_FULL` + `PASSWORD_CHANGE_NOT_REQUIRED` + `ROLE_SUPER_ADMIN` | `201 Created` |
| 초대 수락 | `POST /api/v1/admin/invitations/{invitationId}/acceptances` | public allow-list + token 검증 | `201 Created` |
| 초대 회수 | `DELETE /api/v1/admin/invitations/{invitationId}` | `SESSION_FULL` + `PASSWORD_CHANGE_NOT_REQUIRED` + `ROLE_SUPER_ADMIN` | `204 No Content` |

초대 생성 request:

```json
{
  "email": "admin@example.com",
  "roleCode": "ROLE_ADMIN"
}
```

초대 생성 response:

```json
{
  "invitationId": "7c24c7cb-b2d2-4d35-8a3d-49a2b71bdb8a",
  "email": "admin@example.com",
  "roleCode": "ROLE_ADMIN",
  "expiresAt": "2026-07-07T12:00:00Z"
}
```

초대 수락 request:

```json
{
  "token": "raw-invitation-token",
  "password": "new-admin-password",
  "passwordConfirm": "new-admin-password",
  "displayName": "운영 관리자"
}
```

초대 수락 response:

```json
{
  "accountId": "b73360d8-e38e-4c95-9b1b-3ce7b914c333",
  "email": "admin@example.com",
  "roleCode": "ROLE_ADMIN"
}
```

오류 계약:

| 상황 | HTTP status | error code |
| --- | --- | --- |
| request validation 실패 | `400` | `INVALID_REQUEST` |
| 초대 수락 `passwordConfirm` 누락 또는 불일치 | `400` | `INVALID_REQUEST` |
| 보호 API 인증 없음 | `401` | `UNAUTHORIZED` |
| 최고 관리자 권한 없음 | `403` | `FORBIDDEN` |
| admin endpoint에서 invitation id 없음 | `404` | `ADMIN_INVITATION_NOT_FOUND` |
| public 수락 token 불일치 | `400` | `ADMIN_INVITATION_TOKEN_INVALID` |
| public 수락 token 만료 | `400` | `ADMIN_INVITATION_TOKEN_EXPIRED` |
| 이미 수락/회수된 초대 | `409` | `ADMIN_INVITATION_ALREADY_CLOSED` |
| 허용되지 않은 role | `400` | `ADMIN_INVITATION_ROLE_NOT_ALLOWED` |
| 초대 email에 기존 account 존재 | `409` | `EMAIL_ALREADY_USED` |
| display name 중복 | `409` | `DISPLAY_NAME_ALREADY_USED` |
| 필수 role 기준 데이터 없음 | `500` | `AUTH_ROLE_NOT_CONFIGURED` |

public 수락 API는 path의 `invitationId`만으로 account 생성이 되지 않습니다.
반드시 body의 raw token을 hash해 DB의 `token_hash`와 비교해야 합니다.
`passwordConfirm`은 초대 대상 인증 수단이 아니라 오타 방지를 위한 확인 입력이며, 서버는 `password`와 같은 값인지 확인한 뒤 비밀번호 정책 검증과 hash 저장으로 넘어갑니다.

### S5. Notification boundary

기존 `notification` 모듈을 확장해 관리자 초대 발송 공개 계약을 추가합니다.
account/application 계층은 provider SDK, template, HTML, provider 응답을 알지 않습니다.

| 항목 | 확정 기준 |
| --- | --- |
| 설정 key | `ADMIN_INVITATION_BASE_URL` |
| link 조립 | notification adapter가 base URL과 `invitationId`, raw token으로 화면 진입 URL을 조립 |
| template | 관리자 초대 전용 subject/html/text template |
| 발송 시점 | DB transaction commit 이후 직접 발송 |
| provider 실패 | 초대 row는 rollback하지 않고 warn log와 `ADMIN_INVITATION_CREATED`의 `notificationResult` metadata에 실패를 남김 |
| durable retry/outbox | 이번 범위 제외 |

### S6. Authorization and security

- 생성과 회수 API는 `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`와 `ROLE_SUPER_ADMIN`을 요구합니다.
- 수락 API는 public endpoint로 열되, raw token 검증 없이는 account를 만들지 않습니다.
- 수락 성공으로 생성된 account는 `ACTIVE`이고 `password_change_required = false`입니다.
- `password_change_required = true` 계정은 기존 bootstrap 초기 비밀번호 변경 플로우로 처리하며, 관리자 초대 수락 플로우와 섞지 않습니다.
- 일반회원 비밀번호 재설정은 기존 account의 password hash를 바꾸는 별도 기능입니다.

### S7. Transaction policy

| 흐름 | transaction 기준 |
| --- | --- |
| 초대 생성 | 완료 당시 목표 계약은 기존 open 초대 폐기, 새 초대 저장과 생성 이벤트를 하나의 DB transaction으로 처리하고 발송은 commit 이후 수행하는 것이었습니다. 실제 구현 편차는 Result와 현재 SOT가 소유합니다. |
| 초대 수락 | token 검증, 기존 account 재확인, account/profile/password credential/role assignment 생성, 초대 accepted 처리, 수락 이벤트 기록을 하나의 DB transaction으로 처리합니다. |
| 초대 회수 | open 상태 재확인, revoked 처리, 회수 이벤트 기록을 하나의 DB transaction으로 처리합니다. |
| 동시 수락 | `admin_invitation` row lock 또는 조건부 update로 한 요청만 `accepted_at`을 채우게 하고 나머지는 `ADMIN_INVITATION_ALREADY_CLOSED`로 처리합니다. |
| 수락과 회수 race | 먼저 terminal state를 기록한 transaction만 성공합니다. 늦은 요청은 `ADMIN_INVITATION_ALREADY_CLOSED`로 처리합니다. |
| 같은 email 동시 초대 | open email partial unique index로 중복 open row를 막고, 충돌 시 업무 오류로 변환합니다. |

## 관련 문서

- **선행 account·auth 결과**: [DB Schema](001-account-auth-db-schema.md), [자체 가입](002-member-self-sign-up-api.md), [회원 로그인](005-member-login-api.md), [인증 토큰 기반](006-auth-token-foundation.md), [이메일 인증](008-member-email-verification.md), [로그아웃](009-auth-logout.md)
- **후속 Account 범위**: [Account Backlog](../../backlog/2026/003-account-auth-follow-up.md)
- **업무·데이터 기준**: [Account domain](../../../domain/100-account.md), [Account 데이터 모델](../../../project/220-account-auth-data-model.md)
