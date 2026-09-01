# Account 도메인

이 문서는 account의 생성·인증·복구·관리자 생명주기에서 사용하는 profile, role, credential, token과 invitation의 도메인 의미를 정리합니다.
상태, 역할, session access, 비밀번호 변경 필요 여부에 따른 접근 정책 매트릭스는 [110-account-auth-policy.md](110-account-auth-policy.md)를 기준으로 합니다.

## 목적

- 관리자와 일반회원을 포괄하는 인증 주체의 용어를 명확히 합니다.
- 계정 상태, 역할 부여, refresh token, 인증 이벤트의 의미를 한곳에서 확인할 수 있게 합니다.
- DB 구조와 구현 코드가 같은 도메인 언어를 사용하게 합니다.

## 적용 범위

- account 생성·활성화·credential 복구
- 로그인과 token lifecycle
- 관리자와 일반회원 역할·초대·초기 credential 관리
- 인증·인가 감사 사실

## 핵심 용어

| 용어              | 의미                                                           |
| --------------- | ------------------------------------------------------------ |
| Account         | 로그인 가능한 인증 주체입니다. 일반회원과 관리자 모두 account입니다.                   |
| Profile         | 커뮤니티에서 공개되는 account의 표시 정보입니다.                               |
| Credential      | account가 인증에 사용하는 수단입니다. 현재는 비밀번호 자격증명을 다룹니다.                |
| Role            | account에 부여되는 coarse-grained 권한입니다.                          |
| Role Assignment | 특정 account에 role을 부여하거나 회수한 상태와 이력입니다.                       |
| Refresh Token   | JWT access token을 재발급하기 위한 Base64URL opaque 장기 토큰입니다. 서버에는 원문이 아니라 SHA-256 hash를 저장합니다. |
| Verification Token | 이메일 인증처럼 account 소유권을 확인하기 위한 1회용 token입니다. 서버에는 원문이 아니라 해시를 저장합니다. |
| Admin Invitation | 최고 관리자가 아직 account가 없는 email에 관리자 account 생성을 허용하는 1회용 초대입니다. |
| Auth Event      | 로그인 성공/실패, 토큰 재발급, 역할 변경 같은 인증 관련 이벤트입니다.                    |

## Account

`account`는 인증 주체입니다.
관리자를 별도 테이블로 분리하지 않고, 같은 account에 관리자 역할을 부여합니다.

이 결정의 이유는 다음과 같습니다.

- 로그인, 비밀번호, 토큰, 계정 상태 정책을 중복하지 않습니다.
- 일반회원이 관리자 역할을 받을 수 있습니다.
- 관리자 권한 부여와 회수를 role assignment로 추적할 수 있습니다.
- 향후 운영자, 콘텐츠 관리자 같은 역할을 추가해도 계정 구조가 바뀌지 않습니다.

구현에서는 `Account`를 aggregate root로 둡니다.
일반 회원 자체 가입은 `Account.registerMember(...)` factory가 생성 규칙을 소유합니다.
이 factory는 `PENDING` 상태, 필수 profile, password credential, 시스템 자동 `ROLE_MEMBER` role assignment, `AccountRegisteredEvent` domain event를 함께 생성합니다.
관리자 초대 수락은 `Account.acceptAdminInvitation(...)` factory가 신규 관리자 account graph 생성 규칙을 소유합니다.
이 factory는 `ACTIVE` 상태, `email_verified_at`, 필수 profile, password credential, `ROLE_ADMIN` role assignment를 함께 생성합니다.
기존 account 관리자 승격, 로그인 token 발급, 이메일 인증 완료 처리는 이 factory에 포함하지 않습니다.

## Profile

`account_profile`은 커뮤니티에서 공개되는 표시 정보입니다.
회원가입 시 `display_name`은 필수입니다.

`display_name`은 사용자에게 보여주는 값이고, `display_name_normalized`는 중복 판단을 위한 값입니다.
표시 이름 정책이 바뀌어도 중복 판단 기준을 명시적으로 유지하기 위해 두 값을 분리합니다.
현재 모든 account 생성 경로인 일반 회원 자체 가입, 관리자 초대 수락과 최초 최고 관리자 bootstrap은 같은 `DisplayName` 계약을 사용합니다. 표시값은 앞뒤 공백 제거와 연속 공백 단일화를 적용하고, 중복 판단값은 그 결과를 lowercase로 변환합니다.

## Account Status

계정 상태는 다음 값을 사용합니다.

| 상태          | 의미                                                        |
| ----------- | --------------------------------------------------------- |
| `PENDING`   | 가입은 되었지만 추가 확인이 필요한 상태입니다. 일반 회원 자체 가입의 초기 상태입니다. |
| `ACTIVE`    | 정상적으로 로그인하고 서비스를 이용할 수 있는 상태입니다.                          |
| `SUSPENDED` | 운영 정책에 의해 이용이 제한된 상태입니다.                                  |
| `WITHDRAWN` | 탈퇴한 상태입니다.                                                |

`email_verified_at`은 이메일 인증이 완료된 시각을 기록합니다.
이메일 인증이 필요하지 않거나 아직 완료되지 않은 account는 null을 사용합니다.

상태별 로그인 결과, `LIMITED`·`FULL` session과 endpoint 접근 규칙은 [Account 인증/인가 정책](110-account-auth-policy.md#session-access)이 소유합니다. 현재 `PENDING` 로그인은 `LIMITED`를 발급하지만 이메일 인증 요청·완료 API는 public allow-list이고 자기 계정 상태 API는 아직 없습니다. 따라서 `LIMITED`의 이름만으로 존재하지 않는 허용 기능을 추론하지 않습니다.

일반 회원 자체 가입 account는 `PENDING`으로 생성되고, 이메일 인증 완료 시 `ACTIVE`로 전환됩니다.
이 전이는 인증 token 소비와 같은 transaction에서 처리합니다.
관리자 초대 수락으로 생성되는 account는 초대 token이 email 소유권 확인 수단이므로 처음부터 `ACTIVE`이고 `email_verified_at`을 수락 시각으로 기록합니다.

## Role

초기 역할은 다음 세 가지입니다.

| 역할 | 의미 |
| --- | --- |
| `ROLE_MEMBER` | 일반 회원 권한입니다. |
| `ROLE_ADMIN` | 일반 관리자 권한입니다. 콘텐츠 운영, 신고 처리 같은 관리자 기능에 사용할 수 있습니다. |
| `ROLE_SUPER_ADMIN` | 최고 관리자 권한입니다. 관리자 초대와 관리자 역할 부여 같은 상위 운영 기능에 사용할 수 있습니다. |

최고 관리자와 일반 관리자를 처음부터 구분하는 이유는 다음과 같습니다.

- 최초 관리자와 이후 초대된 관리자의 권한을 다르게 둘 수 있습니다.
- 나중에 모든 기존 관리자를 다시 분류하는 운영 마이그레이션을 피할 수 있습니다.
- 역할 하나를 추가하는 비용은 작지만, 이미 운영 중인 관리자 권한을 나누는 비용은 큽니다.

세부 permission 모델은 지금 도입하지 않습니다.
현재 단계에서는 role 기반 권한이 요구사항과 유지보수성의 균형에 맞습니다.

## Role Assignment

`account_role_assignment`는 단순 조인 테이블이 아니라 역할 부여의 현재 상태와 이력을 표현합니다.

활성 역할 부여는 `revoked_at`이 null인 행입니다.
같은 account에 같은 role이 활성 상태로 중복 부여되면 안 됩니다.
역할을 회수한 뒤 다시 부여하는 것은 허용합니다.

`granted_by_account_id`와 `revoked_by_account_id`는 관리자 권한 변경의 감사 추적을 위한 값입니다.
시스템이 자동으로 부여하거나 회수한 경우에는 null일 수 있습니다.
관리자 초대 수락으로 생성되는 `ROLE_ADMIN` assignment는 초대 생성자의 account id를 `granted_by_account_id`에 남깁니다.

## Password Credential

`account_password_credential`은 비밀번호 로그인 수단입니다.
비밀번호 해시는 Spring Security `PasswordEncoder` 결과를 저장합니다.
별도 salt 컬럼은 두지 않습니다.
Argon2id, bcrypt 같은 password hashing 결과에는 필요한 알고리즘 정보와 salt가 해시 문자열에 포함됩니다.

`password_change_required`는 초기 관리자 생성이나 임시 비밀번호 발급처럼 사용자가 자신이 정하지 않은 비밀번호로 시작할 때 강제 변경하는 데 사용합니다. 비밀번호 재설정 완료는 사용자가 새 비밀번호를 직접 정하는 흐름이므로 이 값을 `false`로 저장합니다.
비밀번호 로그인 실패는 `failed_login_count`와 `last_failed_login_at`에 기록합니다.
현재 기준에서는 hard lock을 바로 적용하지 않고, 실제 계정 잠금은 rate limit, captcha, abuse monitoring 정책과 함께 별도 작업에서 결정합니다.

## Password Policy

password를 새로 정하는 일반 회원 가입, 관리자 초대 수락, 초기 관리자 bootstrap, 인증된 비밀번호 변경과 비밀번호 재설정은 같은 `PasswordPolicy`를 사용합니다. 기준은 최소 10자, 최대 128자입니다.
공백을 포함한 passphrase는 허용하지만 전체가 공백인 비밀번호는 거부합니다.
대문자, 숫자, 특수문자 조합 규칙은 강제하지 않습니다.

## Initial Admin

실제 관리자 계정과 비밀번호를 Flyway migration에 seed하지 않습니다.
비밀번호나 임시 비밀번호가 Git에 남을 수 있기 때문입니다.

초기 최고 관리자는 일반 account이며, `ROLE_SUPER_ADMIN` 역할을 부여받습니다.
임시 비밀번호나 초기 비밀번호를 사용하는 경우 `password_change_required = true`로 생성해 최초 로그인 후 비밀번호 변경을 강제합니다.

## Admin Invitation

관리자 초대는 최고 관리자가 아직 account가 없는 email에 `ROLE_ADMIN` account 생성을 허용하는 흐름입니다.
초대 raw token은 이메일 링크와 수락 request에만 존재하고, 서버에는 SHA-256 hash만 저장합니다.
초대 만료 시간은 24시간입니다.

초대 수락은 신규 account 생성 흐름입니다.
기존 account의 비밀번호를 바꾸거나 기존 회원에게 role을 추가하는 기능이 아닙니다.
초대 email에 이미 account가 있으면 초대 생성과 수락 모두 거부합니다.

같은 email에 open 초대가 있으면 새 초대 생성 시 기존 open 초대를 회수 상태로 만들고 새 token을 발급합니다.
최고 관리자는 open 초대를 명시적으로 회수할 수 있습니다.
이미 수락되거나 회수된 초대는 다시 수락하거나 회수할 수 없습니다.

## Refresh Token

Refresh token은 인증 주체인 account가 아니라 `auth`의 `RefreshSession` 생명주기입니다.
이 절은 account 상태·credential 변경과의 협력 정책만 설명하며 물리 writer는 `auth` 하나입니다.

JWT access token은 짧게 만료시키고 DB에 저장하지 않습니다.
refresh token은 원문을 저장하지 않고 해시만 저장합니다.
비밀번호 로그인 성공 시 refresh token 원문은 API 응답으로 한 번만 전달하고, 서버에는 SHA-256 hash와 `family_id`, 발급/만료 시각만 저장합니다.
현재 refresh token 만료 시간은 30일입니다.

refresh token은 rotation을 기본 정책으로 둡니다.
재발급 시 기존 refresh token은 회수하고 새 token을 발급합니다.
revoked·replaced refresh token의 재사용이 감지되면 만료 여부와 관계없이 같은 `family_id`에
속한 활성 token을 `REUSE_DETECTED`로 폐기하고 reuse 감사 이벤트를 남깁니다. 폐기·교체
이력이 없는 단순 만료 token은 거부하되 family와 감사 이벤트를 변경하지 않습니다.
로그아웃은 요청으로 전달된 refresh token 하나만 `LOGOUT` 사유로 폐기하며, 전체 기기 로그아웃이나 access token blacklist는 현재 정책에 포함하지 않습니다.

refresh token 재발급 시점에는 account 상태와 role을 다시 조회합니다.
`ACTIVE`와 `PENDING` account만 새 access token을 받을 수 있고, `SUSPENDED`와 `WITHDRAWN` account는 재발급을 거부합니다.
거부된 account의 refresh token family는 더 유지하지 않고 폐기합니다.
현재 저장 schema는 `WITHDRAWN` 전용 폐기 사유를 갖지 않으므로 account 상태 거부로 폐기되는 family는 `ACCOUNT_SUSPENDED` 사유를 사용하고, 구체 실패 원인은 감사 이벤트의 `reason_code`에 남깁니다.

## Verification Token

`account_verification_token`은 account 소유권 확인과 비로그인 자격증명 복구에 사용하는 1회용 token 저장소입니다.
현재 구현된 purpose는 `EMAIL_VERIFICATION`과 `PASSWORD_RESET`, delivery channel은 `EMAIL`입니다.

원문 token은 DB에 저장하지 않고 SHA-256 hash만 저장합니다.
같은 account, purpose, delivery channel에는 open token이 하나만 존재할 수 있습니다.

이메일 인증 token의 기본 정책은 다음과 같습니다.

| 항목 | 정책 |
| --- | --- |
| token TTL | 30분 |
| 재요청 cooldown | 60초 |
| cooldown 중 재요청 | 기존 open token 유지, 새 token 발급과 발송 없음 |
| 인증 완료 | valid token 소비, `email_verified_at` 기록, account `ACTIVE` 전환 |

비밀번호 재설정 token도 같은 저장 생명주기를 사용하지만 목적과 완료 효과는 분리합니다. 요청은 `ACTIVE`·`PENDING` account에만 내부적으로 token을 발급하고, 완료는 token 소비, 새 password hash 저장, `password_change_required = false`, 활성 refresh token 폐기를 하나의 transaction으로 처리합니다. `PENDING` account를 `ACTIVE`로 전환하지 않습니다. TTL, cooldown과 공개 API의 enumeration 방지 계약은 [Account 인증/인가 정책](110-account-auth-policy.md)이 소유합니다.

## Security Audit

`auth_event`는 `securityaudit`가 단독 저장하는 보안과 운영 추적용 최소 감사 로그입니다.
회원 자체 가입의 `ACCOUNT_SIGN_UP/SUCCESS` 감사 레코드는 `AccountRegisteredEvent` domain event를 persistence adapter가 저장 형식으로 매핑한 결과입니다.

현재 event type은 인증/인가 흐름 이름만 담고, 성공/실패는 `result`, 세부 원인은 `reason_code`로 분리합니다.
현재 이벤트 흐름은 다음과 같습니다.

- `ACCOUNT_SIGN_UP`
- `PASSWORD_LOGIN`
- `LOGOUT`
- `TOKEN_REFRESH`
- `PASSWORD_CHANGE`
- `PASSWORD_CHANGE_REQUIREMENT`
- `ROLE_CHANGE`
- `EMAIL_VERIFICATION_REQUEST`
- `EMAIL_VERIFICATION_CONFIRM`
- `ADMIN_INVITATION_CREATED`
- `ADMIN_INVITATION_ACCEPTED`
- `ADMIN_INVITATION_REVOKED`
- `PASSWORD_RESET_REQUESTED`
- `PASSWORD_RESET_COMPLETED`

이벤트별 생성 조건, `result`, `reason_code`, `metadata`, transaction 경계는 [120-auth-event-policy.md](120-auth-event-policy.md)를 기준으로 합니다.

로그인 실패처럼 account를 특정하지 못하는 경우 `account_public_id`는 null일 수 있습니다.
현재 expand 기간에는 compatibility를 위해 `account_id`도 유지하지만 application 계약에는
노출하지 않습니다.
이 경우 `identifier_hash`를 사용해 원문 이메일을 남기지 않고 실패 시도를 추적합니다.
`identifier_hash`는 JWT secret과 다른 HMAC-SHA256 secret으로 계산해야 합니다. 현재 application은
각 secret의 최소 길이만 검증하고 두 값의 동일 여부는 거부하지 않습니다. 이 문서가 분리
정책을 계속 소유하며, 확인된 구현 결함의 수정 상태는
[002 Auth secret 분리 검증 누락](../work-items/bugs/2026/002-auth-secret-separation-validation.md)에서
추적합니다.
원문 email, password, access token, refresh token은 auth event metadata에 남기지 않습니다.

## 관련 문서

- **인증·인가 정책**: [Account 인증/인가 정책](110-account-auth-policy.md)
- **감사 이벤트 정책**: [Auth Event 정책](120-auth-event-policy.md)
- **현재 데이터 구조**: [Account 인증/인가 데이터 모델](../project/220-account-auth-data-model.md)
- **구현 배치·기술**: [모듈 지도](../project/210-module-map.md), [Spring Security](../tech-stack/220-spring-security.md)
