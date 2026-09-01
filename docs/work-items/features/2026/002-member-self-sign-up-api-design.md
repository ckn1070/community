# 일반 회원 자체 가입 API 상세 설계

## 문서 내 진입점

| 확인할 내용 | 위치 |
| --- | --- |
| 확정 결론과 가입 유형 경계 | [확정 결정 요약](#확정-결정-요약), [가입 유형 경계](#가입-유형-경계) |
| 모듈·상태·보안 결정 | [핵심 설계 결정](#핵심-설계-결정) |
| HTTP와 저장 계약 | [API 계약](#api-계약), [생성 데이터](#생성-데이터), [UseCase 흐름](#usecase-흐름) |
| 구현 구조 | [도메인 모델 구조](#도메인-모델-구조) |

## 확정 결정 요약

| 항목 | 결정 |
| --- | --- |
| 가입 유형 | 이번 작업은 일반 회원 자체 가입만 다룹니다. |
| 관리자 흐름 | 관리자 계정 생성, 초대, 수락, 역할 부여는 이번 작업에서 제외합니다. |
| endpoint | `POST /api/v1/members`를 사용합니다. |
| email normalization | trim + lowercase를 사용합니다. |
| display name normalization | 표시값은 trim + 연속 공백 단일화를 사용하고, 중복 판단값은 그 결과의 lowercase를 사용합니다. |
| 감사 이벤트 | 가입 성공 이벤트만 기록합니다. |
| API 공통 기반 분리 | 공통 오류 응답/예외 처리와 API Security baseline은 하나의 선행 기반 작업으로 묶습니다. |
| 이메일 인증 방향 | 신규 account는 `PENDING`으로 생성하고 이메일 인증 완료 시 `ACTIVE`로 전환합니다. |

## 가입 유형 경계

| 흐름 | 행위자 | 생성 또는 변경되는 역할 | 인증/인가 기준 | endpoint 후보 | 이번 작업 |
| --- | --- | --- | --- | --- | --- |
| 일반 회원 자체 가입 | 미인증 사용자 | `ROLE_MEMBER` | public, abuse 방어 필요 | `POST /api/v1/members` | 포함 |
| 초기 최고 관리자 bootstrap | 운영자 또는 시스템 | `ROLE_SUPER_ADMIN` | 운영 secret, 1회성, 공개 HTTP 금지 | 공개 endpoint 없음 | 제외 |
| 관리자 초대 생성 | `ROLE_SUPER_ADMIN` | account 생성 없음 | 인증된 최고 관리자 | `POST /api/v1/admin-invitations` | 제외 |
| 관리자 초대 수락 | 초대받은 사용자 | 초대에 지정된 관리자 역할 | invitation token, 만료 검증 | `POST /api/v1/admin-invitations/{invitationId}/acceptances` | 제외 |
| 관리자 직접 생성 | `ROLE_SUPER_ADMIN` 또는 운영자 | `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN` | 강한 감사 로그, 임시 비밀번호 정책 | 별도 검토 | 제외 |
| 기존 회원 관리자 승격 | `ROLE_SUPER_ADMIN` | 기존 account에 관리자 역할 추가 | 인증된 최고 관리자 | `POST /api/v1/members/{memberId}/role-assignments` 후보 | 제외 |

## 핵심 설계 결정

### 1. 모듈 경계

- 선택: 내부 구현은 `account` 모듈에 둡니다. 외부 endpoint는 그대로 `POST /api/v1/members`를 사용합니다. `auth` 모듈은 지금 만들지 않고 로그인/JWT/OAuth 작업에서 분리합니다.
- 왜 이 결론인가: 현재 DB와 도메인 문서는 일반 회원과 관리자를 모두 `account`로 표현합니다. 관리자도 로그인 가능한 인증 주체이고, password credential, role assignment, account status를 공유합니다. 따라서 이 생명주기를 `member`가 소유하면 관리자까지 member로 설명해야 해서 도메인 언어가 어긋납니다.
- endpoint를 그대로 두는 이유: URI는 클라이언트가 보는 공개 리소스 이름입니다. 내부 모듈명이 `account`여도, 가입 결과가 커뮤니티 회원 생성이라면 외부 API는 `/members`가 더 자연스럽습니다.
- `auth`를 이 작업에서 분리하지 않는 이유: 이번 작업은 로그인, token, OAuth provider 연동이 아니라 account 생성입니다. `auth`까지 함께 만들면 credential 저장과 account 생성 사이의 모듈 계약을 먼저 설계해야 해서 첫 기능 구현 복잡도가 커집니다. 대신 `PasswordHasherPort`를 두고 인증 토큰 기반은 [006-auth-token-foundation.md](006-auth-token-foundation.md)에서 분리합니다.

| 선택지 | 평가 | 선택 여부 |
| --- | --- | --- |
| `account` 중심 | 일반 회원과 관리자 계정을 모두 표현합니다. 현재 schema와 도메인 문서의 언어와 맞습니다. `/members` endpoint와 내부 소유권을 분리할 수 있습니다. | 선택됨 |
| `member` 중심 | 첫 가입 API만 보면 단순하지만, 관리자 계정과 인증 주체까지 member에 넣어야 해서 이름과 책임이 흐려집니다. | 제외 |
| `account`, `member`, `auth` 즉시 강분리 | 장기적으로 깔끔하지만 첫 기능부터 모듈 간 계약이 과해집니다. OAuth가 실제 작업으로 들어올 때 분리해도 늦지 않습니다. | 후순위 |
| `auth` 중심 | `/auth/sign-up`처럼 인증 플로우 중심으로 구현할 수 있지만, 일반 회원 생성과 관리자 생성 경계가 다시 흐려집니다. | 제외 |

### 2. 초기 account status

- 선택: 일반 회원 자체 가입 시 account는 `PENDING`으로 생성합니다.
- 왜 이 결론인가: 이메일 인증을 후속 흐름으로 연결하는 제품 방향에서는 가입 직후 완전 활성 상태인 `ACTIVE`보다, 이메일 확인이 필요한 상태인 `PENDING`이 도메인 의미에 맞습니다.
- 구현 기준: 가입 API는 `PENDING` account, profile, password credential, `ROLE_MEMBER` role assignment를 생성합니다. 현재 이메일 인증 완료 흐름과 상태 전이는 [110 Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)이 소유하며, [008 이메일 인증 설계](008-member-email-verification-design.md)는 그 흐름을 선택한 당시 근거를 보존합니다.
- 이메일 인증 연계: 인증 전에는 커뮤니티 쓰기 기능을 제한하고, 인증 메일 재발송과 이메일 인증 완료 흐름을 허용합니다.

| 선택지 | 평가 | 선택 여부 |
| --- | --- | --- |
| `PENDING` | 이메일 확인 전 상태를 명확히 표현하고 인증 전 기능 제한 정책을 연결할 수 있습니다. | 선택 |
| `ACTIVE` | 이메일 인증 없이 바로 쓰게 할 때는 단순하지만 이메일 확인 전후 상태를 구분할 수 없습니다. | 제외 |

### 3. Password hashing과 password policy

- 선택: `DelegatingPasswordEncoder`를 사용하고 기본 알고리즘은 Argon2id로 둡니다.
- 왜 이 결론인가: 새 시스템에서는 memory-hard 알고리즘인 Argon2id가 bcrypt보다 우선입니다. OWASP는 Argon2id를 우선 추천하고, bcrypt는 Argon2id나 scrypt를 사용할 수 없는 경우의 대안으로 둡니다.
- Spring 적용: hash 문자열에 알고리즘 prefix를 남겨 나중에 알고리즘을 바꿀 수 있게 `DelegatingPasswordEncoder`를 사용합니다.
- Argon2id 완료 당시 파라미터는 memory 19 MiB, iterations 2, parallelism 1을 선택했습니다. 성능 특성이나 배포 자원이 달라지면 보안 강도와 hash/verify 비용을 함께 측정해 재검토합니다.
- 완료 당시 password-only 인증은 최소 15자, 최대 128자로 제한했습니다. 후속 정책 변경에서 최소 길이를 10자로 낮췄으며, 현재 값과 모든 비밀번호 설정 흐름의 계약은 [Account의 Password Policy](../../../domain/100-account.md#password-policy)와 `PasswordPolicy` source가 소유합니다. 공백 포함 passphrase 허용, 전체 공백 금지와 조합 규칙 미강제 원칙은 유지됩니다.

| 선택지 | 평가 | 선택 여부 |
| --- | --- | --- |
| Argon2id | 새 시스템에 가장 적합합니다. 메모리 비용을 사용해 GPU 공격 비용을 높일 수 있습니다. 파라미터 튜닝은 필요하지만 현재 단계에서 감수할 가치가 있습니다. | 선택됨 |
| bcrypt | 널리 검증되고 단순하지만 72 bytes 입력 제한이 있고 memory-hard 특성이 약합니다. Argon2id 적용이 불가능할 때 fallback으로 둡니다. | fallback |
| PBKDF2 | FIPS 요구가 있을 때 의미가 있습니다. 현재 프로젝트에는 FIPS 요구가 없습니다. | 제외 |
| Spring 기본 delegating 설정만 사용 | 알고리즘 전환 구조는 좋지만, 기본값을 명시하지 않으면 프로젝트 보안 기준이 흐려집니다. | 제외 |

### 4. 중복과 탈퇴 계정 재가입

- 선택: 현재 일반 회원 자체 가입에서는 기존 account의 normalized email/display name 재사용을 모두 막습니다.
- 왜 이 결론인가: 현재 schema는 전체 row 기준 unique constraint입니다. 탈퇴/익명화 기능이 아직 없으므로, 재사용을 허용하려면 account 보존, 개인정보 익명화, 작성 콘텐츠 표시, 감사 로그까지 같이 설계해야 합니다.
- 탈퇴 기능에서의 추천 방향: email은 탈퇴 유예 기간 이후 소유권 재검증을 거쳐 재사용 가능하게 검토합니다. display name은 커뮤니티 정체성과 사칭 문제가 있으므로 기본은 재사용 금지로 두고, 필요하면 긴 cooldown 또는 관리자 해제 정책을 별도로 둡니다.

| 항목 | 현재 가입 API | 탈퇴 기능 설계 시 추천 |
| --- | --- | --- |
| email | 재사용 금지 | 탈퇴 유예 기간 이후 이메일 소유권 재검증을 전제로 재사용 허용 검토 |
| display name | 재사용 금지 | 기본 재사용 금지, 필요 시 cooldown 또는 관리자 해제 |
| 작성 콘텐츠 | 이번 작업 제외 | 탈퇴 사용자 표시명, 작성글 보존/익명화 정책과 함께 결정 |
| DB constraint | 기존 unique constraint 유지 | 탈퇴/익명화 정책에 맞춰 partial unique index 또는 tombstone 전략 검토 |

## API 계약

### 요청

```json
{
  "email": "user@example.com",
  "password": "correct horse battery staple",
  "displayName": "사용자"
}
```

### 성공 응답

```json
{
  "memberId": "0f3445fd-5f22-4f74-9a9d-1ff0c68a924a",
  "email": "user@example.com",
  "displayName": "사용자",
  "status": "PENDING"
}
```

응답에는 내부 DB id, password hash, access token, refresh token을 포함하지 않습니다.
자동 로그인은 로그인/JWT 작업에서 별도로 설계합니다.

### 오류 응답 계약

오류 응답 형식과 공통 예외 변환 기준은 [003-api-common-foundation.md](003-api-common-foundation.md)를 따릅니다.
이 작업은 회원가입 유스케이스에서 발생하는 업무 오류 code와 HTTP status 연결만 추가합니다.

| 상황 | HTTP status | 오류 코드 |
| --- | --- | --- |
| request validation 실패 | 400 | `INVALID_REQUEST` |
| email 중복 | 409 | `EMAIL_ALREADY_USED` |
| display name 중복 | 409 | `DISPLAY_NAME_ALREADY_USED` |
| `ROLE_MEMBER` seed 누락 | 500 | `AUTH_ROLE_NOT_CONFIGURED` |
| 예상하지 못한 저장 실패 | 500 | `INTERNAL_ERROR` |

## 생성 데이터

일반 회원 자체 가입 성공 시 다음 데이터가 하나의 transaction에서 생성됩니다.

| 테이블 | 생성 내용 |
| --- | --- |
| `account` | public id, email, normalized email, `PENDING` status |
| `account_profile` | display name, normalized display name |
| `account_password_credential` | Argon2id password hash, password policy 상태 |
| `account_role_assignment` | `ROLE_MEMBER` 활성 부여 |
| `auth_event` | 완료 당시 taxonomy의 `SIGN_UP/SUCCESS` 이벤트 |

`account_role_assignment.granted_by_account_id`는 시스템 자동 부여이므로 null을 사용합니다.
이 API는 `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN`을 입력으로 받지 않고, 조회하거나 부여하지도 않습니다.

## 구현 배치의 소유권

이 설계는 `account`가 가입 aggregate와 application 흐름을 소유하고 Web·persistence·password hashing을 adapter로 격리한다는 경계만 고정합니다. 완료 뒤의 exact package와 type inventory는 이 문서에 복제하지 않으며 [모듈 지도](../../../project/210-module-map.md)와 실제 `account` source가 소유합니다.

## UseCase 흐름

```text
POST /api/v1/members
  -> RegisterMemberController
  -> RegisterMemberRequest validation
  -> RegisterMemberAccountCommand
  -> RegisterMemberAccountUseCase.execute
       -> EmailAddress 정규화와 검증
       -> DisplayName 정규화와 검증
       -> PasswordPolicy 검증
       -> AccountUniquenessPort
       -> AuthRoleLookupPort(ROLE_MEMBER)
       -> PasswordHasherPort
       -> Account.registerMember(...)
       -> MemberAccountStorePort(Account aggregate + AccountRegisteredEvent)
  -> RegisterMemberResponse
```

`RegisterMemberAccountService`가 transaction 경계입니다.
account, profile, credential, role assignment, auth event 중 하나라도 실패하면 전체 가입은 rollback되어야 합니다.
`SIGN_UP/SUCCESS`는 완료 당시 domain event 필드가 아니라 persistence adapter가 `AccountRegisteredEvent`를 `auth_event` 저장 형식으로 옮길 때 부여한 감사 레코드 값입니다. 후속 [Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md)는 이를 `ACCOUNT_SIGN_UP/SUCCESS`로 대체했으며, 현재 값은 [Auth Event 정책](../../../domain/120-auth-event-policy.md)이 소유합니다.

## 도메인 모델 구조

`docs/workflow/110-design-workflow.md`의 도메인 모델 기준에 따라 가입 use case의 aggregate와 경계 모델을 다음처럼 고정합니다.

### Domain Inventory

| 타입 | 역할 | 설계 결정 |
| --- | --- | --- |
| `EmailAddress` | Value Object | email 정규화와 유효성 소유 |
| `DisplayName` | Value Object | 표시값 정규화와 유효성 소유 |
| `PasswordPolicy` | Domain Policy | password 길이와 허용 문자 정책 소유 |
| `AccountStatus` | Domain Enum | account 생명주기 상태 소유 |
| `RoleCode` | Domain Enum | 역할 코드 소유 |
| `Account` | Aggregate Root | 자체 가입 생성 불변식 소유 |
| `AccountProfile` | Aggregate 내부 Value Object | profile 필수값 소유 |
| `PasswordHash` | Value Object | 빈 hash 금지 |
| `PasswordCredential` | Aggregate 내부 Value Object | password credential 상태 소유 |
| `RoleAssignment` | Aggregate 내부 Value Object | 초기 회원 role assignment 소유 |
| `AccountRegisteredEvent` | Domain Event | 가입 성공 발생 사실만 표현 |

### Aggregate 결정

일반 회원 자체 가입은 상태 변경 usecase이며, `account`, `account_profile`, `account_password_credential`, `account_role_assignment`를 하나의 transaction에서 생성합니다.
따라서 `Account` aggregate를 둡니다.
Aggregate를 생략하는 선택지는 현재 DDD 기준과 맞지 않습니다.

- Aggregate root: `Account`
- 일관성 경계: account 식별자, email, status, profile, password credential, 활성 role assignment
- 생성 factory: `Account.registerMember(...)`
- 생성 invariant:
  - 자체 가입 account는 `PENDING` 상태로 시작합니다.
  - 자체 가입 account는 `ROLE_MEMBER` role assignment만 초기 보유합니다.
  - profile과 password credential은 자체 가입 account 생성에 필수입니다.
  - password hash는 비어 있을 수 없습니다.
  - 자체 가입 성공은 `AccountRegisteredEvent` domain event로 남깁니다.
  - 관리자 role 부여와 token 발급은 이 factory의 책임이 아닙니다.
- persistence 매핑: 완료 당시 persistence adapter가 `Account` aggregate를 account/profile/password credential/role assignment 저장 구조로 매핑하고, `AccountRegisteredEvent`를 `auth_event`의 `SIGN_UP/SUCCESS` 감사 레코드로 매핑했습니다.

### Boundary Model Inventory

| 경계 | 모델 | 책임 |
| --- | --- | --- |
| Web request | `RegisterMemberRequest` | HTTP 입력 계약 |
| Application command | `RegisterMemberAccountCommand` | use case 입력 |
| Domain model | `Account` | 가입 aggregate와 불변식 |
| Port input | `Account` | aggregate 저장 입력 |
| Port output | 없음 | application에 DB 내부 id를 노출하지 않음 |
| Web response | `RegisterMemberResponse` | 외부 member resource 표현 |
| Persistence entity | `Account*JpaEntity`, `AuthEventJpaEntity` | DB 저장 구조 |
| Domain event | `AccountRegisteredEvent` | 가입 성공 발생 사실 |
| Audit record | `AuthEventJpaEntity` | 완료 당시 domain event를 `SIGN_UP/SUCCESS`로 매핑 |

저장 요청 payload를 domain model로 두는 대안은 경계 전달 타입을 domain으로 오분류하므로 배제합니다.

### 최종 구현 계약

- `AccountErrorCode`는 HTTP·application 오류 계약이므로 domain이 아니라 application 경계가 소유합니다.
- 표시용 `display_name`에는 trim과 연속 공백 단일화를 적용하고, 중복 판단용 `display_name_normalized`에는 lowercase 값을 저장합니다.
- persistence adapter는 `Account` aggregate를 저장 구조로 매핑하고 DB 내부 id를 application 밖으로 노출하지 않습니다.
- `AccountRegisteredEvent`는 발생 사실만 표현하며 완료 당시 `SIGN_UP/SUCCESS` 저장값 매핑은 persistence adapter가 소유했습니다.
- Web response는 domain enum을 직접 노출하지 않고 외부 계약의 `status` 문자열을 사용합니다.

## 관련 문서

- **선행 구현 결과**: [DB Schema](001-account-auth-db-schema.md), [API 공통 기반](003-api-common-foundation.md)
- **후속 Account 범위**: [Account Backlog](../../backlog/2026/003-account-auth-follow-up.md)
- **업무·데이터 기준**: [Account domain](../../../domain/100-account.md), [Account 데이터 모델](../../../project/220-account-auth-data-model.md)
- **구현 배치**: [모듈 지도](../../../project/210-module-map.md)
- **구조·API 기준**: [헥사고날 아키텍처](../../../architecture/200-hexagonal-architecture.md), [REST API 설계](../../../architecture/230-rest-api-design.md)
- **보안·test 기준**: [Spring Security](../../../tech-stack/220-spring-security.md), [테스트 스택](../../../tech-stack/300-testing-stack.md)
- **후속 taxonomy 대체**: [Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md), [현재 Auth Event 정책](../../../domain/120-auth-event-policy.md)

## 외부 참고

- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [NIST SP 800-63B](https://pages.nist.gov/800-63-4/sp800-63b.html)
- [Spring Security Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
