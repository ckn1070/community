# Account 인증/인가 데이터 모델

이 문서는 account/auth 정책을 현재 PostgreSQL 구조에 어떻게 배치했는지 설명합니다. 업무 의미와 접근 정책은 `domain`, 정확한 table·column·constraint·index는 [schema reference](schema/100-account-auth.md), 실행 사실은 Flyway migration이 소유합니다.

## 구조 개요

```text
account
├─ account_profile
├─ account_password_credential
├─ account_role_assignment ── auth_role
├─ refresh_token
├─ account_verification_token
└─ auth_event

admin_invitation ── auth_role
                 ├─ invited_by_account
                 └─ accepted_account / revoked_by_account
```

- 관리자와 일반 회원은 `account`라는 하나의 인증 주체를 사용합니다.
- 공개 profile, password credential, role assignment와 token 생명주기는 별도 table로 분리합니다.
- access token은 저장하지 않고 refresh·verification·invitation token은 원문이 아닌 hash만 저장합니다.
- 역할 기준값은 `auth_role`, 부여·회수 이력은 `account_role_assignment`가 소유합니다.
- 인증·인가 감사 사실은 `auth_event`가 소유합니다.

## 테이블 책임

| 테이블 | 구조적 책임 | 정책 owner | 현재 물리 writer |
| --- | --- | --- | --- |
| `account` | 인증 주체, 공개 식별자, 상태와 확인·로그인 시각 | [Account 도메인](../domain/100-account.md) | `account` persistence |
| `account_profile` | 공개 표시 정보와 중복 판단용 정규화 값 | [Account 도메인](../domain/100-account.md) | `account` persistence |
| `account_password_credential` | password hash, 변경 필요 상태와 실패 계측 | [인증/인가 정책](../domain/110-account-auth-policy.md) | `account` persistence |
| `auth_role` | coarse-grained role 기준값 | [인증/인가 정책](../domain/110-account-auth-policy.md) | `account` persistence |
| `account_role_assignment` | role 부여·회수의 현재 상태와 이력 | [Account 도메인](../domain/100-account.md) | `account` persistence |
| `refresh_token` | rotation family, hash와 폐기 상태 | [Account 도메인](../domain/100-account.md) | `account`와 `auth` persistence의 이중 매핑 |
| `account_verification_token` | email verification·password reset token 생명주기 | [Account 도메인](../domain/100-account.md) | `account` persistence |
| `admin_invitation` | 관리자 초대 hash와 수락·회수 terminal state | [Account 도메인](../domain/100-account.md) | `account` persistence |
| `auth_event` | event type, result, reason과 비민감 metadata | [Auth Event 정책](../domain/120-auth-event-policy.md) | `account`와 `auth` persistence의 이중 매핑 |

정확한 FK, unique·check constraint, index와 허용 값은 [Account/Auth Schema Reference](schema/100-account-auth.md)를 사용합니다. 이 문서에 schema 표를 다시 복제하지 않습니다.

## 공통 Schema 결정

- 테이블명은 단수형을 사용합니다.
- `created_at`, `updated_at`은 Spring Data JPA Auditing이 관리하고 insert fallback으로 `DEFAULT NOW()`를 유지합니다.
- 외부에는 `account.public_id`를 사용하고 내부 DB id와 원문 email을 token claim에 넣지 않습니다.
- PostgreSQL native enum 대신 `VARCHAR`와 `CHECK` constraint를 사용합니다.
- IP 주소는 PostgreSQL `INET`을 사용합니다.
- 실제 관리자 비밀번호는 migration에 seed하지 않고, 필수 role 기준값만 versioned migration으로 관리합니다.
- 기존 migration을 수정하지 않고 새 versioned migration으로 schema를 변경합니다.

이 결정의 기술 근거는 [Flyway](../tech-stack/240-flyway.md)와 [PostgreSQL](../tech-stack/250-postgresql.md), 현재 적용 결과는 migration과 schema reference가 소유합니다.

## 생명주기별 데이터 경계

| operation | 실제 변경·transaction 경계 | 세부 정책 owner |
| --- | --- | --- |
| 회원 가입 | account graph, member role, 가입 감사 이벤트 | [Account 도메인](../domain/100-account.md) |
| 로그인 | refresh token, 로그인 시각·실패 계측, 감사 이벤트 | [인증/인가 정책](../domain/110-account-auth-policy.md) |
| refresh rotation·reuse | token family와 감사 이벤트 | [Account 도메인](../domain/100-account.md) |
| 이메일 인증 요청 | verification token을 먼저 commit하고, after-commit provider 호출 뒤 요청 감사 이벤트 저장만 별도 `REQUIRES_NEW` transaction | [Auth Event 정책](../domain/120-auth-event-policy.md) |
| 이메일 인증 확인 | account 활성화, verification token 소비와 확인 감사 이벤트를 같은 transaction에서 변경 | [Account 도메인](../domain/100-account.md) |
| 관리자 초대 생성 | 기존 open invitation 회수, 새 invitation과 생성 감사 이벤트를 같은 transaction에서 변경하고 notification은 commit 이후 호출 | [Auth Event 정책](../domain/120-auth-event-policy.md) |
| 관리자 초대 수락 | invitation 수락 terminal state, 신규 account graph, admin role과 수락 감사 이벤트를 같은 transaction에서 변경 | [Account 도메인](../domain/100-account.md) |
| 관리자 초대 회수 | invitation 회수 terminal state와 회수 감사 이벤트를 같은 transaction에서 변경 | [Account 도메인](../domain/100-account.md) |
| 비밀번호 재설정 요청 | verification token을 먼저 commit하고, after-commit provider 호출 뒤 요청 감사 이벤트 저장만 별도 `REQUIRES_NEW` transaction | [Auth Event 정책](../domain/120-auth-event-policy.md) |
| 비밀번호 재설정 완료 | verification token 소비, credential, active refresh token과 완료 감사 이벤트를 같은 transaction에서 변경 | [인증/인가 정책](../domain/110-account-auth-policy.md) |

위 표는 서로 다른 operation을 한 행에 합치지 않고 실제 변경 경계만 요약합니다. 정상·거부·
재시도 세부와 API 접근 조건은 반복하지 않습니다. 이메일 인증 요청과 비밀번호 재설정 요청의
provider 결과·감사 저장 실패 의미 분리는 Proposed
[Notification 결과와 감사 저장 실패 혼합](../work-items/bugs/2026/005-notification-audit-result-separation.md)이
소유합니다.

`refresh_token`과 `auth_event`의 이중 writer는 의도된 최종 모듈 계약이 아니라 현재 구현 예외입니다. 로그인·비밀번호 흐름은 `account` adapter가, rotation·logout·reuse 흐름은 `auth` adapter가 같은 table을 서로 다른 JPA type으로 갱신합니다. 또한 `auth` refresh token repository가 native SQL로 `account` table을 직접 읽어 internal id를 public id로 변환합니다. 목표 owner와 transaction·조회 공개 계약을 정해 하나의 물리 persistence 경계로 이관하기 전까지 새 writer나 직접 table read를 추가하지 않습니다. 후속 완료 조건은 [Account 인증/인가 후속 기능 Backlog](../work-items/backlog/2026/003-account-auth-follow-up.md#authaccount-persistence-owner-정리)가 소유합니다.

## 구현 Source

- **실행 schema**: `src/main/resources/db/migration/V1__create_account_table.sql`부터 현재 최신 migration
- **현재 schema reference**: [schema/100-account-auth.md](schema/100-account-auth.md)
- **module·package 책임**: [모듈 지도](210-module-map.md)
- **업무 정책**: [domain 문서](../domain/000-index.md)
- **물리 조회 구조**: [Account/Auth schema](schema/100-account-auth.md)

문서와 migration이 충돌하면 migration을 기준으로 schema reference와 이 구조 지도를 갱신합니다. 정책과 구현이 충돌하면 단순히 문서를 구현에 맞추지 않고 관련 work item에서 의도된 정책과 변경 방향을 먼저 결정합니다.

## 재검토 조건

- 소셜 로그인이나 passwordless credential이 추가됩니다.
- account 삭제·익명화·보존 정책이 현재 구조로 표현되지 않습니다.
- role만으로 authorization 정책을 설명하기 어려워집니다.
- token family, session 또는 device의 별도 aggregate가 필요해집니다.
