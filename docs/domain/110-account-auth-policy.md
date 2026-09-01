# Account 인증/인가 정책

이 문서는 account 상태, role, session access, 비밀번호 변경 필요 여부가 어떤 상황에서 부여되고, 각 조건이 API 접근을 어떻게 제한하는지 정리합니다.

## 목적

- 인증/인가 판단에 쓰이는 조건을 한곳에서 확인합니다.
- JWT claim과 Spring Security authority의 의미를 명확히 합니다.
- 새 API를 추가할 때 어떤 조건을 요구해야 하는지 판단할 수 있게 합니다.
- 관리자 계정, 초기 비밀번호 변경, 일반 회원 가입 상태가 같은 용어로 설명되게 합니다.

## 적용 범위

- account status
- role과 role assignment
- session access
- `password_change_required`
- access token claim
- Spring Security authority
- 기본 `/api/**` 접근 기준
- 이메일 인증 요청/완료 public API 접근 기준
- 비밀번호 재설정 요청/완료 public API 접근 기준
- 로그아웃 public API 접근 기준
- 관리자 초대 생성/수락/회수 접근 기준
- `/api/v1/admin/**` 접근 기준

이 문서는 현재 구현된 인증/인가 정책만 다룹니다.
일반 역할 부여/회수 API, 자기 계정 상태 조회 API, 세부 permission 모델은 아직 이 문서의 범위가 아닙니다.

## 핵심 원칙

- Account는 일반 회원과 관리자를 모두 표현하는 인증 주체입니다.
- Role은 account에 부여되는 coarse-grained 권한입니다.
- Session access는 로그인 결과로 발급된 token이 일반 보호 API까지 사용할 수 있는지 구분합니다.
- `password_change_required`는 로그인 성공 여부와 별개로 API 접근을 제한하는 보안 상태입니다.
- `/api/**`는 기본적으로 Bearer JWT 인증을 요구합니다. 공개 API는 보안 설정에서 명시적으로만 엽니다.
- 이메일 인증 요청/완료 API는 가입과 활성화 사이의 사용자가 호출해야 하므로 public API로 열지만, account 존재 여부와 발송 실패 세부사항은 status/body에 노출하지 않습니다.
- 비밀번호 재설정 요청/완료 API는 로그인할 수 없는 사용자가 호출해야 하므로 public API로 열지만, account 존재 여부, account 상태, 발송 실패 세부사항은 status/body에 노출하지 않습니다.
- 로그인과 두 복구 요청은 현재 계정 존재·상태에 따라 password 검증, DB 작업과 provider 호출 비용이 달라 timing 균일성까지 보장하지 않습니다. 이 문서가 현재 보안 경계를 소유하고, timing 균일화의 수정 상태와 완료 조건은 [009 계정 열거 timing Bug](../work-items/bugs/2026/009-authentication-enumeration-timing.md)에서 추적합니다.
- 비밀번호 변경 전 계정은 로그인은 가능하지만 `PUT /api/v1/auth/password` 외 인증이 필요한 일반·관리자 보호 API 접근은 차단합니다. `permitAll` public endpoint는 별도입니다.
- Refresh token 재발급 시에는 token claim을 신뢰하지 않고 DB의 최신 account 상태, role, 비밀번호 변경 필요 여부를 다시 조회합니다.

## 업무 모델과 소유권

- `account`의 `PasswordCredential`이 password hash 변경과
  `password_change_required = false` 전이를 함께 표현합니다.
- `EmailVerificationChallenge`와 `PasswordResetChallenge`가 cooldown·만료·소비·회수와
  terminal 재전이 금지를 표현합니다.
- `AdminInvitation`이 수락·회수·만료 상태와 terminal 재전이 금지를 표현합니다.
- `auth`의 `RefreshSession`이 issue·rotation·logout·reuse 후보와 만료 전이를 표현하고,
  `auth` persistence만 `refresh_token`을 변경합니다.
- 비밀번호 변경·재설정이 성공하면 `account`가 `CredentialChanged`를 동기 발행하고,
  `auth` listener가 같은 transaction에서 active refresh session을 폐기합니다.

Application service는 입력 검증과 transaction 순서를 조율하고, JPA Entity는 Domain Model이
허용한 결과를 table에 매핑합니다. `securityaudit`는 이 결과의 감사 record를 단독 저장합니다.

## 인증 조건 요약

| 조건                       | 값                  | 부여 시점                        | 주요 효과                                |
| ------------------------ | ------------------ | ---------------------------- | ------------------------------------ |
| Account status           | `PENDING`          | 일반 회원 자체 가입 초기 상태            | limited session만 발급합니다.              |
| Account status           | `ACTIVE`           | 정상 이용 가능 계정, 최초 최고 관리자 생성    | full session 발급 대상입니다.               |
| Account status           | `SUSPENDED`        | 운영 정책으로 제한된 계정               | 로그인과 refresh 재발급을 거부합니다.             |
| Account status           | `WITHDRAWN`        | 탈퇴 계정                        | 로그인과 refresh 재발급을 거부합니다.             |
| Role                     | `ROLE_MEMBER`      | 일반 회원 자체 가입                  | 일반 회원 권한입니다.                         |
| Role                     | `ROLE_ADMIN`       | 관리자 초대 수락                    | 일반 관리자 API 접근 조건입니다.                 |
| Role                     | `ROLE_SUPER_ADMIN` | 최초 최고 관리자 bootstrap         | 관리자 API와 최고 관리자 전용 API 접근 조건입니다. |
| Session access           | `LIMITED`          | `PENDING` 계정 로그인             | 일반 보호 API 접근을 허용하지 않습니다.           |
| Session access           | `FULL`             | `ACTIVE` 계정 로그인              | 비밀번호 변경 상태와 role에 따라 보호 API에 접근합니다. |
| Password change required | `true`             | 최초 최고 관리자, 임시 비밀번호             | 비밀번호 변경 API 외 일반·관리자 보호 API 접근을 차단합니다. public allow-list는 별도입니다. |
| Password change required | `false`            | 일반 가입 기본값, 비밀번호 변경·재설정 완료    | session/role 조건에 따라 API 접근이 가능합니다.   |

## Account Status

Account status는 account 자체의 이용 가능 상태입니다.

| 상태 | 발생 상황 | 로그인 결과 | refresh token 재발급 | API 접근 |
| --- | --- | --- | --- | --- |
| `PENDING` | 일반 회원 자체 가입 후 추가 확인 전 | 성공, `LIMITED` session | 성공 가능, `LIMITED` session | 일반 `/api/**` 접근 불가 |
| `ACTIVE` | 정상 이용 계정, 최초 최고 관리자 | 성공, `FULL` session | 성공 가능, `FULL` 또는 정책상 계산된 session | 다른 조건을 만족하면 접근 가능 |
| `SUSPENDED` | 운영 제한 | 실패 | 실패, token family 폐기 | 접근 불가 |
| `WITHDRAWN` | 탈퇴 | 실패 | 실패, token family 폐기 | 접근 불가 |

`PENDING`은 계정 존재와 제한된 인증 상태를 표현합니다.
현재 일반 보호 API는 `SESSION_FULL`을 요구하므로 `PENDING` 계정 token으로는 접근할 수 없습니다.

## Role

Role은 account에 부여되는 coarse-grained authorization 조건입니다.

| role | 부여되는 상황 | 현재 사용처 |
| --- | --- | --- |
| `ROLE_MEMBER` | 일반 회원 자체 가입 시 시스템 자동 부여 | 일반 회원 권한 표현 |
| `ROLE_ADMIN` | 최고 관리자가 만든 초대를 사용자가 수락할 때 부여 | 일반 `/api/v1/admin/**` 접근 조건 |
| `ROLE_SUPER_ADMIN` | 최초 최고 관리자 bootstrap에서 부여 | 일반 관리자 API와 관리자 초대 생성·회수 접근 조건 |

활성 role assignment는 `revoked_at`이 null인 부여 이력입니다.
JWT access token의 `roles` claim은 발급 시점의 활성 role code 목록입니다.
Refresh token 재발급 시에는 DB의 최신 활성 role을 다시 조회해 새 access token을 발급합니다.

## Session Access

Session access는 로그인 결과로 발급된 token의 사용 범위를 나타냅니다.

| 값 | 발생 조건 | Spring authority | 의미 |
| --- | --- | --- | --- |
| `LIMITED` | `PENDING` account 로그인 | `SESSION_LIMITED` | 인증은 되었지만 일반 보호 API 접근은 허용하지 않습니다. |
| `FULL` | `ACTIVE` account 로그인 | `SESSION_FULL` | 다른 password·role 조건을 만족하면 보호 API에 접근합니다. |

일반 `/api/**`와 `/api/v1/admin/**`는 `SESSION_FULL`을 요구합니다.
이메일 인증 요청/완료 API는 현재 public allow-list에 있으므로 `SESSION_LIMITED`를 요구하지 않습니다.
`SESSION_LIMITED`는 자기 계정 상태 확인처럼 인증된 PENDING 계정 전용 제한 기능이 생길 때 사용할 수 있는 별도 상태입니다.
초기 관리자 비밀번호 변경 강제 상태를 `SESSION_LIMITED`로 표현하지 않습니다.

## Password Change Required

`password_change_required`는 비밀번호 변경 전 API 차단이 필요한지 나타내는 보안 조건입니다.

| 값 | 발생 조건 | Spring authority | 접근 효과 |
| --- | --- | --- | --- |
| `true` | 최초 최고 관리자 생성, 임시 비밀번호 | `PASSWORD_CHANGE_REQUIRED` | `PUT /api/v1/auth/password` 외 인증 필요 보호 API 접근 차단. public allow-list는 별도 |
| `false` | 일반 계정 기본값, 비밀번호 변경·재설정 완료 | `PASSWORD_CHANGE_NOT_REQUIRED` | 다른 session/role 조건에 따라 API 접근 가능 |

`password_change_required = true` 계정도 로그인은 성공할 수 있습니다.
로그인을 허용하는 이유는 비밀번호 변경 API를 호출할 access token이 필요하기 때문입니다.
다만 refresh token 재발급은 이 상태에서 거부합니다.
초기 비밀번호 변경 대상은 access token이 만료되면 다시 로그인한 뒤 비밀번호 변경을 진행해야 합니다.

## JWT Claim

Access token에는 인증과 인가에 필요한 최소 claim만 넣습니다.

| claim | 값 | 출처 | 용도 |
| --- | --- | --- | --- |
| `sub` | account public id 문자열 | `account.public_id` | 인증 principal 식별자 |
| `roles` | role code 목록 | 활성 role assignment | role authority 변환 |
| `session_access` | `FULL` 또는 `LIMITED` | 로그인/refresh 시 account status 정책 | session authority 변환 |
| `account_status` | account status name | DB의 account status | token 발급 시점 상태 관찰 |
| `password_change_required` | boolean | password credential | password change authority 변환 |

Access token에는 다음 값을 넣지 않습니다.

- 내부 DB id
- 원문 email
- display name
- password hash
- access token 원문
- refresh token 원문
- refresh token hash

Custom claim 이름은 snake_case를 사용합니다.
현재 custom claim은 `session_access`, `account_status`, `password_change_required`입니다.
`roles`는 단일 단어라 snake_case 변환 대상이 아닙니다.

## Spring Security Authority

JWT claim은 SecurityFilterChain에서 사용할 authority로 변환합니다.

| 입력 claim | 입력 값 | 생성 authority |
| --- | --- | --- |
| `roles` | `ROLE_MEMBER` | `ROLE_MEMBER` |
| `roles` | `ROLE_ADMIN` | `ROLE_ADMIN` |
| `roles` | `ROLE_SUPER_ADMIN` | `ROLE_SUPER_ADMIN` |
| `session_access` | `FULL` | `SESSION_FULL` |
| `session_access` | `LIMITED` | `SESSION_LIMITED` |
| `password_change_required` | `true` | `PASSWORD_CHANGE_REQUIRED` |
| `password_change_required` | `false` | `PASSWORD_CHANGE_NOT_REQUIRED` |

`roles` claim의 값은 이미 `ROLE_` prefix를 포함한 프로젝트 role code입니다.
Spring Security의 기본 `SCOPE_` prefix 변환은 사용하지 않습니다.
빈 문자열, `ROLE_` prefix가 없는 값, 알 수 없는 session access 값은 authority로 변환하지 않습니다.

## API 접근 매트릭스

현재 API 접근 기준은 다음과 같습니다.

| 요청                                   | 인증 필요  | 필요 authority/조건                                                                    | 허용 대상                                           |
| ------------------------------------ | ------ | ---------------------------------------------------------------------------------- | ----------------------------------------------- |
| `POST /api/v1/members`               | 없음     | 없음                                                                                 | 공개 회원가입                                         |
| `POST /api/v1/auth/sessions`         | 없음     | 없음                                                                                 | 비밀번호 로그인                                        |
| `POST /api/v1/auth/sessions/refresh` | 없음     | refresh token 검증과 DB 재조회                                                           | refresh token 재발급                               |
| `POST /api/v1/auth/verifications/email` | 없음  | 요청 email 정규화, PENDING account 조회, cooldown/token 발급/발송 처리. account 존재 여부는 응답에 노출하지 않음 | 이메일 인증 메일 요청 |
| `POST /api/v1/auth/verifications/email/confirm` | 없음 | raw token 검증, token 소비, account `ACTIVE` 전환 | 이메일 인증 완료 |
| `POST /api/v1/auth/password-resets` | 없음 | 요청 email 정규화, `ACTIVE`/`PENDING` account 조회, cooldown/token 발급/발송 처리. account 존재 여부와 상태는 응답에 노출하지 않음 | 비밀번호 재설정 메일 요청 |
| `POST /api/v1/auth/password-resets/confirm` | 없음 | raw reset token 검증, token 소비, password hash 갱신, active refresh token 폐기. `PENDING` account status는 유지 | 비밀번호 재설정 완료 |
| `POST /api/v1/auth/logout` | 없음 | 제출된 refresh token hash 조회와 활성 token 단일 폐기. invalid·expired·이미 폐기된 token도 동일한 `204` | access token 상태와 무관하게 현재 refresh token을 폐기하려는 클라이언트 |
| `POST /api/v1/admin/invitations/{invitationId}/acceptances` | 없음 | invitation id와 raw token 검증, password 확인, 초대 수락 transaction | 초대받은 관리자 |
| `PUT /api/v1/auth/password`          | 필요     | `SESSION_FULL`                                                                     | `ACTIVE` 로그인 계정. `PASSWORD_CHANGE_REQUIRED`도 허용 |
| `POST /api/v1/admin/invitations` | 필요 | `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`, `ROLE_SUPER_ADMIN` | 비밀번호 변경 완료 최고 관리자 |
| `DELETE /api/v1/admin/invitations/{invitationId}` | 필요 | `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`, `ROLE_SUPER_ADMIN` | 비밀번호 변경 완료 최고 관리자 |
| 기타 `/api/v1/admin/**`                   | 필요     | `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`, `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN` | 비밀번호 변경 완료 관리자                                  |
| 기타 `/api/**`                         | 필요     | `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`                                     | 비밀번호 변경 완료 full session 계정                      |
| 비 API 경로                             | 경로별 설정 | 현재 기본 permit                                                                       | API 보안 체인 밖의 경로                                 |

비밀번호 변경 API가 `PASSWORD_CHANGE_NOT_REQUIRED`를 요구하지 않는 것은 의도된 예외입니다.
초기 비밀번호 변경 대상도 이 API를 호출할 수 있어야 합니다.
이메일 인증 요청 API가 public인 것은 동일 status/body 응답 정책과 함께 적용되는 예외입니다.
요청 email에 해당하는 account가 없거나, 이미 활성화됐거나, cooldown 중이어도 public 응답은 접수 성공 형태를 유지합니다.
이메일 인증 완료 API도 public이지만 raw token이 인증 수단이므로 token 형식, 만료, 소비/폐기 상태를 검증한 뒤에만 account를 활성화합니다.
비밀번호 재설정 요청 API도 public이며, `ACTIVE`와 `PENDING` account만 내부적으로 reset token을 발급합니다.
비밀번호 재설정 완료는 `PasswordCredential`이 사용자가 선택한 새 password hash와
`password_change_required = false` 결과를 만들고 `CredentialChanged`로 활성 refresh session을
폐기합니다. `PENDING` account를 `ACTIVE`로 전환하지 않습니다.

현재 동일 공개 응답은 직접적인 계정 존재 노출만 막습니다. 부적격 요청은 조기에 반환하고 적격 요청은 token 저장과 동기 notification까지 수행하므로 timing side channel은 남아 있습니다.

비밀번호 재설정 token TTL은 30분, 같은 account의 재요청 cooldown은 60초입니다. cooldown 중에는
기존 open token을 유지하고 새 token 발급·발송을 하지 않으며, cooldown 이후에는 기존 open
token을 폐기한 뒤 새 token을 발급합니다. 이 문서가 현재 정책값을 소유하고
`RequestPasswordResetService` 상수와 관련 test가 이를 구현합니다.
[012 비밀번호 재설정 설계](../work-items/features/2026/012-account-password-reset-design.md#d4-ttl과-cooldown)는
이 값을 선택한 당시 근거를 확인할 때만 사용합니다.

## 상태별 접근 예시

| 상태 | role | session access | password change required | 일반 `/api/**` | `PUT /api/v1/auth/password` | `/api/v1/admin/**` |
| --- | --- | --- | --- | --- | --- | --- |
| 일반 가입 직후 | `ROLE_MEMBER` | `LIMITED` | `false` | 차단 | 차단 | 차단 |
| 일반 회원 활성 상태 | `ROLE_MEMBER` | `FULL` | `false` | 허용 | 허용 | 차단 |
| 최초 최고 관리자 초기 비밀번호 | `ROLE_SUPER_ADMIN` | `FULL` | `true` | 차단 | 허용 | 차단 |
| 최고 관리자 비밀번호 변경 후 | `ROLE_SUPER_ADMIN` | `FULL` | `false` | 허용 | 허용 | 허용 |
| 일반 관리자 비밀번호 변경 후 | `ROLE_ADMIN` | `FULL` | `false` | 허용 | 허용 | 허용 |
| 정지 계정 | 어떤 role이든 | 없음 | 어떤 값이든 | 차단 | 차단 | 차단 |

이메일 인증 요청/완료 public API는 위 보호 API 접근 표와 별개입니다.
일반 가입 직후 `PENDING` 계정은 `POST /api/v1/auth/verifications/email`로 인증 메일을 요청하고, 메일 링크에서 받은 token으로 `POST /api/v1/auth/verifications/email/confirm`을 호출해 `ACTIVE`로 전환될 수 있습니다.

## Login과 Refresh의 차이

비밀번호 로그인은 account 상태와 password credential을 확인하고 session access를 결정합니다.

| account 상태 | 로그인 결과 |
| --- | --- |
| `ACTIVE` | access token과 refresh token 발급 |
| `PENDING` | limited session 성격의 access token과 refresh token 발급 |
| `SUSPENDED` | 거부 |
| `WITHDRAWN` | 거부 |

Staging Swagger Form Login은 같은 email·password 검증과 account 상태 판정을 재사용하지만
업무 API session을 생성하지 않습니다. `ACTIVE`, `password_change_required = false`이면서
`ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN`인 경우에만 Swagger 경로용 browser session을 만들고,
refresh token은 발급·저장하지 않습니다. 성공 시 실패 횟수, `last_login_at`과
`PASSWORD_LOGIN` 성공 감사 이벤트만 갱신합니다.

Refresh token 재발급은 `auth`가 저장한 `RefreshSession`을 검증한 뒤 `account::api` 공개 조회로
최신 account 상태, role, `password_change_required`를 다시 확인합니다. `auth`는 `account`
table을 직접 조회하지 않습니다.

| 조건 | 재발급 결과 |
| --- | --- |
| account가 `ACTIVE` 또는 `PENDING`이고 `password_change_required = false` | 새 access token과 refresh token 발급 |
| `password_change_required = true` | 재발급 거부 |
| account가 `SUSPENDED` 또는 `WITHDRAWN` | 재발급 거부, token family 폐기 |
| revoked·replaced refresh token 재사용 감지 | 만료 여부와 관계없이 재발급 거부, token family 폐기와 reuse 감사 이벤트 저장 |
| 폐기·교체 이력이 없는 만료 refresh token | 재발급 거부, token family와 reuse 감사 이벤트 변경 없음 |

## 새 API 추가 기준

새 API를 추가할 때는 먼저 API 성격을 분류합니다.

| API 성격 | 권장 조건 |
| --- | --- |
| 공개 API | SecurityFilterChain의 공개 허용 목록에 명시합니다. |
| 이메일 인증 요청 API | 공개 API로 두되 account 존재 여부를 숨기는 동일 status/body, timing 위협, cooldown과 token 민감정보 미노출 기준을 함께 둡니다. |
| 이메일 인증 완료 API | 공개 API로 두되 raw token을 인증 수단으로 보고 token 검증과 account 활성화 transaction 기준을 함께 둡니다. |
| 비밀번호 재설정 요청 API | 공개 API로 두되 account 존재·상태를 숨기는 동일 status/body, timing 위협, cooldown과 token 민감정보 미노출 기준을 함께 둡니다. |
| 비밀번호 재설정 완료 API | 공개 API로 두되 raw reset token을 인증 수단으로 보고 password credential 갱신, token 소비, refresh token 폐기 transaction 기준을 함께 둡니다. |
| 관리자 초대 수락 API | 공개 API로 두되 invitation id와 raw token을 함께 검증하고 초대 대상 email로 account를 생성합니다. |
| 일반 보호 API | 기본 `/api/**` 규칙을 사용합니다. `SESSION_FULL`과 `PASSWORD_CHANGE_NOT_REQUIRED`가 필요합니다. |
| 비밀번호 변경 API | `SESSION_FULL`만 요구하고 `PASSWORD_CHANGE_REQUIRED`도 허용합니다. |
| 관리자 API | `/api/v1/admin/**` 아래에 두고 `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN`을 요구합니다. |
| 최고 관리자 전용 API | 관리자 초대 생성·회수처럼 role 관리에 영향을 주는 API에 `ROLE_SUPER_ADMIN`을 요구합니다. |
| limited session API | 별도 path와 허용 목적을 먼저 문서화합니다. 일반 보호 API에 섞지 않습니다. |

Controller에서 `if admin` 같은 권한 분기를 반복하지 않습니다.
공통 접근 기준은 SecurityFilterChain이나 method security 기준으로 표현합니다.

## 재검토 조건

- 관리자 역할이 role만으로 설명하기 어려울 정도로 세분화됩니다.
- 자기 계정 상태 조회처럼 `SESSION_LIMITED`를 요구하는 인증된 PENDING 계정 전용 API가 추가됩니다.
- 이메일 인증 요청/완료 API를 public 방식에서 authenticated 또는 limited session 방식으로 바꿔야 합니다.
- 쿠키 기반 인증이나 브라우저 세션 API가 추가됩니다.
- refresh token 재발급 중 `password_change_required = true` 처리 정책을 바꿔야 합니다.
- 최고 관리자 전용 API가 생겨 별도 method security 기준이 필요해집니다.

## 관련 문서

- **상위 도메인**: [Account](100-account.md)
- **감사 이벤트 정책**: [Auth Event 정책](120-auth-event-policy.md)
- **현재 데이터 구조**: [Account 인증/인가 데이터 모델](../project/220-account-auth-data-model.md)
- **구현 기술 기준**: [Spring Security](../tech-stack/220-spring-security.md)
- **최초 구현 기록**: [008 이메일 인증](../work-items/features/2026/008-member-email-verification.md), [010 관리자 계정 기반](../work-items/features/2026/010-admin-account-foundation.md)
