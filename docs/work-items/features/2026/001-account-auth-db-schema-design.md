# Account 인증/인가 DB Schema 상세 설계

## 배경

회원가입과 로그인 기능을 구현하기 전에 인증 주체, 역할, refresh token, 인증 이벤트를 저장할 DB 골격을 먼저 확정해야 합니다.
현재 작업은 회원가입이나 로그인 기능 구현이 아니라 account 기반 인증/인가 DB schema 설계와 migration 검증입니다.

## 설계 메모

- 테이블명은 프로젝트 PostgreSQL 문서 기준에 따라 단수형을 사용합니다.
- `account_profile`과 `account_password_credential`은 `account_id`를 PK/FK로 쓰는 1:1 확장 테이블입니다.
- `email_normalized`와 `display_name_normalized`는 DB unique 기준을 명시하기 위해 별도 컬럼으로 둡니다.
- `auth_role`은 내부 FK 안정성을 위해 `id`를 PK로 두고, `code`는 unique 기준 코드로 사용합니다.
- `account_role_assignment`는 단순 매핑이 아니라 역할 부여와 회수 이력을 표현하므로 별도 `id`를 둡니다.
- access token은 DB에 저장하지 않고, refresh token만 해시로 저장합니다.
- refresh token은 rotation과 reuse detection을 지원할 수 있도록 `family_id`, `replaced_by_token_id`, `revoked_reason`을 둡니다.
- `auth_event`는 보안 분석과 운영 추적을 위한 최소 감사 로그입니다.

## 확정된 DB schema 결정

| 항목 | 확정한 결정 | 반영 위치 |
| --- | --- | --- |
| `created_at`, `updated_at` 관리 방식 | 애플리케이션에서 Spring Data JPA Auditing으로 관리하고 DB trigger는 사용하지 않습니다. DB는 `NOT NULL`, `DEFAULT NOW()`, `updated_at >= created_at` check constraint로 보조합니다. | migration, 이후 JPA Entity와 auditing 테스트 |
| `created_at`, `updated_at`의 `DEFAULT NOW()` | insert fallback 용도로 유지합니다. update 자동 갱신 책임은 JPA Auditing에 둡니다. | `account`, `account_profile`, `account_password_credential`, `auth_role` |
| `public_id` 생성 주체 | 애플리케이션에서 생성합니다. DB default는 두지 않고 `NOT NULL`, `UNIQUE`만 보장합니다. | `account.public_id`, 이후 account 생성 usecase |
| `auth_role` PK | 내부 FK 안정성을 위해 `id`를 PK로 두고, `code`는 `UNIQUE` 기준 코드로 둡니다. | `auth_role.id`, `auth_role.code`, `account_role_assignment.role_id` |
| `ip_address` 타입 | PostgreSQL `INET`을 사용합니다. | `refresh_token.ip_address`, `auth_event.ip_address`, migration/schema 테스트 |
| enum 성격 값의 DB 제약 방식 | PostgreSQL native enum은 쓰지 않고 `VARCHAR`와 `CHECK` constraint를 사용합니다. | `account.status`, `refresh_token.revoked_reason`, `auth_event.event_type`, `auth_event.result` |
| `auth_event.event_type` 초기 허용 목록 | `SIGN_UP`, `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT`, `TOKEN_REFRESH`, `TOKEN_REUSE_DETECTED`, `PASSWORD_CHANGED`, `PASSWORD_RESET_REQUIRED`, `ROLE_GRANTED`, `ROLE_REVOKED`를 허용합니다. | `chk_auth_event_type`, migration/schema 테스트 |
| `refresh_token.revoked_reason` 초기 허용 목록 | `LOGOUT`, `ROTATED`, `REUSE_DETECTED`, `PASSWORD_CHANGED`, `ACCOUNT_SUSPENDED`, `ADMIN_REVOKED`를 허용합니다. | `chk_refresh_token_revoked_reason`, migration/schema 테스트 |

위 `auth_event.event_type` 목록은 최초 schema 설계 당시의 결정 기록입니다. 이후 [Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md)가 현재 taxonomy를 대체 소유하며, 현재 물리 허용값은 [Schema Reference](../../../project/schema/100-account-auth.md#auth_event)에서 확인합니다.

## 관련 문서

- **현재 모듈 배치**: [모듈 지도](../../../project/210-module-map.md)
- **인증 구현 기준**: [Spring Security](../../../tech-stack/220-spring-security.md)
- **schema·DB 기준**: [Flyway](../../../tech-stack/240-flyway.md), [PostgreSQL](../../../tech-stack/250-postgresql.md)
- **후속 데이터 범위**: [Account Backlog](../../backlog/2026/003-account-auth-follow-up.md)
