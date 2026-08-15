# Account 인증/인가 DB Schema

## 상태

Done

## 결과

- 완료 당시 신규 DB 기준으로 account/auth 초기 schema와 필수 role seed를 Flyway V1~V8로 구현했습니다.
- 완료 당시 Testcontainers PostgreSQL에서 migration 8개, schema 검증 test 9개와 Spring context smoke test를 통과했습니다.
- account 도메인 의미, 데이터 모델과 물리 schema reference를 영구 문서에 반영했습니다.
- 전체 완료 여부: 예
- 남은 책임: 없음

## 범위

- account, profile, password credential, role, role assignment
- refresh token과 auth event
- 필수 `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_MEMBER` seed
- PostgreSQL migration과 실제 schema 검증

이 작업 이후 추가된 verification token, admin invitation과 password reset schema는 후속 기능 작업과 [현재 Schema Reference](../../../project/schema/100-account-auth.md)가 소유합니다.

## 확정 결정 색인

[상세 설계](001-account-auth-db-schema-design.md)는 다음 판단을 보존합니다.

- account/auth table 경계와 식별자 전략
- credential, role assignment, refresh token과 auth event 관계
- Flyway migration 분리와 seed 방식
- PostgreSQL constraint·index와 Testcontainers 검증 기준

## 영구 SOT 승격

- [Account 도메인](../../../domain/100-account.md)
- [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)
- [Account/Auth Schema Reference](../../../project/schema/100-account-auth.md)
- [Flyway 기준](../../../tech-stack/240-flyway.md)
- [PostgreSQL 기준](../../../tech-stack/250-postgresql.md)

## 관련 문서

- **결정 근거**: [상세 설계](001-account-auth-db-schema-design.md)
- **후속 범위**: [Account Backlog](../../backlog/2026/003-account-auth-follow-up.md)
- **후속 taxonomy·migration 상태**: [Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md)
