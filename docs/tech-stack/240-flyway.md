# Flyway 사용 가이드

이 문서는 Flyway 기반 데이터베이스 마이그레이션 작성과 운영 기준을 정의합니다.

## 목적

- DB 스키마 변경을 코드와 함께 버전 관리합니다.
- 환경별 DB 상태를 일관되게 유지합니다.
- 마이그레이션 실패와 데이터 손실 위험을 줄입니다.

## 기본 원칙

- 모든 스키마 변경은 Flyway migration으로 관리합니다.
- 적용된 versioned migration은 수정하지 않습니다.
- 새 변경은 새 versioned migration으로 추가합니다.
- migration 파일은 Git으로 추적합니다.
- 운영 데이터에 영향을 주는 migration은 되돌림과 배포 순서를 고려합니다.
- 로컬·test profile은 Spring Boot 시작 시 migration을 실행하지만, staging 운영 Pod는
  Flyway를 끄고 배포 전용 migration process가 먼저 실행합니다.

## Migration 종류

Flyway 공식 문서는 migration을 versioned, repeatable, baseline 등으로 설명합니다.
Versioned migration은 순서대로 한 번 적용되고, repeatable migration은 checksum이 바뀔 때 다시 적용됩니다.

프로젝트 기준:

- 스키마 변경은 versioned migration을 사용합니다.
- view, function, reference view처럼 재생성 가능한 객체는 repeatable migration을 검토합니다.
- undo migration은 도입 전 운영 방식과 라이선스/지원 범위를 확인합니다.

## 파일 네이밍

기본 형식은 다음을 사용합니다.

```text
V<version>__<description>.sql
```

예시:

```text
V1__create_example_table.sql
V2__create_car_table.sql
V3__add_post_status.sql
```

기준:

- version은 증가하는 정수 또는 날짜 기반을 사용할 수 있습니다.
- 초기에는 단순 증가 정수를 사용합니다.
- description은 영문 소문자와 underscore를 사용합니다.
- 한 migration은 하나의 명확한 목적을 가집니다.

## 작성 기준

- DDL은 명시적으로 작성합니다.
- constraint, index, foreign key 이름을 가능한 명시합니다.
- nullable 변경은 기존 데이터와 배포 순서를 고려합니다.
- enum 또는 check constraint 변경은 application code와 순서를 맞춥니다.
- 대량 데이터 수정은 lock, transaction time, rollback 가능성을 검토합니다.
- destructive change는 최소 2단계 배포를 검토합니다.
- table, column, constraint, index, enum-like check value, seed data를 변경하면 해당 schema reference 문서도 같은 작업에서 갱신합니다.

## Schema Reference 기준

Flyway migration 파일이 DB schema의 source of truth입니다.
Schema reference 문서는 source of truth가 아니라, 여러 migration을 적용한 현재 최종 schema 상태를 사람이 읽기 쉽게 모은 문서입니다.

프로젝트 기준:

- migration은 변경 이력과 실행 순서를 소유합니다.
- schema reference는 table별 현재 최종 column, constraint, index, check value, seed data를 소유합니다.
- 한 table에 여러 migration이 쌓이면 reference에는 migration별 이력을 나열하지 않고 최종 상태를 씁니다.
- schema 변경 migration을 추가할 때는 같은 작업에서 관련 schema reference를 갱신합니다.
- reference와 migration이 충돌하면 migration을 기준으로 보고, reference를 수정합니다.
- 아직 reference가 없는 schema 영역에서 반복 조회가 필요해지면 `project` 문서 그룹에 새 schema reference를 추가합니다.

현재 account/auth schema reference는 [../project/schema/100-account-auth.md](../project/schema/100-account-auth.md)를 기준으로 합니다.

## 현재 알려진 Migration 호환성 결함

Auth Event taxonomy 변경 과정에서 이미 repository에 추가돼 있던 `V7__create_auth_event_table.sql`과 `V10__extend_auth_event_for_email_verification.sql`이 직접 수정됐고 별도 forward migration은 추가되지 않았습니다. 수정본으로 처음 생성한 DB는 현재 taxonomy를 갖지만, 원래 checksum으로 두 version을 적용한 지속 DB는 Flyway validation 실패와 기존 event row 전환 누락 위험이 있습니다.

이 상태에서 `flyway repair`, checksum 수동 변경 또는 기존 migration 재수정을 먼저 수행하지 않습니다. 환경별 `flyway_schema_history` checksum과 기존 `auth_event.event_type` 값 분포를 읽기 전용으로 확인하고, 백업·forward migration·필요한 repair 순서를 검증한 뒤 처리해야 합니다. 현재 상태와 완료 조건은 [Auth Event Taxonomy 재설계](../work-items/improvements/2026/001-auth-event-taxonomy-redesign.md)가 소유합니다.

## Staging 운영 실행 경계

Staging에서는 같은 application image를 runtime과 migration 역할로 나눠 사용합니다.

```text
Argo CD full sync
  -> PreSync migration Job: non-web Spring process
      -> migrator Secret을 DB_USERNAME·DB_PASSWORD로 mapping
      -> Flyway validation·migration
      -> afterMigrate callback으로 runtime 최소 권한 동기화
  -> 성공한 경우에만 Deployment rollout
      -> DB_USERNAME·DB_PASSWORD
      -> spring.flyway.enabled=false
```

Migration Job은 `spring.main.web-application-type=none`과 lazy initialization으로 HTTP server와
web 보안 bean을 만들지 않고 기존 image의 Flyway·JPA schema 검증을 실행한 뒤 종료합니다.
Migrator Secret 값만 Spring datasource의
`DB_USERNAME`·`DB_PASSWORD`로 mapping합니다. Job argument에서만
`spring.flyway.callback-locations=classpath:db/callback`을 활성화하고, `afterMigrate` SQL이
runtime 권한을 동기화합니다. Deployment는 `SPRING_FLYWAY_ENABLED=false`와 runtime
credential만 받아 replica 기동과 DDL을 분리합니다.

운영 migration은 다음 값을 명시합니다.

- Flyway validation: 적용된 version·checksum과 현재 source가 다르면 rollout 전에 실패합니다.
- Baseline 자동 수행 없음: 이력 없는 non-empty schema를 현재 기준에 편입하지 않습니다.
- 자동 `repair` 없음: checksum drift나 실패 이력을 source보다 먼저 다시 쓰지 않습니다.

PreSync Job은 자동 retry를 하지 않지만 Git sync 재시도 때 다시 실행될 수 있습니다. 이를
분산 exactly-once 실행으로 설명하지 않습니다. Flyway schema history가 적용된 version을
보호하고, 최신 상태에서 같은 Job을 다시 실행하면 migration `0건`으로 안전하게 끝나는 계약을
사용합니다. Argo CD selective sync는 hook을 실행하지 않으므로 staging 배포 경로로 사용하지
않습니다.

## Database role과 rollback

`carmunity_migrator`는 schema DDL과 Flyway history를 소유하고,
`carmunity_runtime`은 schema `USAGE`, application table의 DML과 sequence 사용 권한만
가집니다. Runtime role은 table 생성·변경·삭제와 role 관리를 할 수 없고
`flyway_schema_history`도 읽지 않습니다. 새 migration 성공 뒤 `afterMigrate` callback이 현재
application table·sequence 권한을 다시 동기화하므로 이후 replica가 늘어도 runtime Pod는 같은
최소 권한 identity만 공유합니다.

Staging JDBC URL은 `currentSchema=public`을 요구해 migration·runtime connection이
같은 schema를 소비합니다.

Runtime DML을 migrator의 default privilege로 미리 부여하지 않습니다. Migration이 실패해
history나 일부 object만 남은 순간에도 runtime 접근을 열지 않고, `migrate()`가 성공한 뒤의
`afterMigrate` callback만 현재 application object를 공개합니다. Role bootstrap은 두 login
role과 schema 권한만 최초 생성하며 기존 object ACL·Flyway history를 자동 인수하거나
감사하지 않습니다. Callback은 현재 table의 DML과 identity sequence `USAGE`만 runtime에
부여하고 `flyway_schema_history` 권한을 회수합니다. `afterMigrateApplied`가 아니라
`afterMigrate`를 사용하므로 callback 실패를 복구한 다음 full sync에서 migration 0건이어도
권한 단계를 다시 실행합니다.

Image digest revert는 database down migration이 아닙니다. 배포된 새 schema는 그대로 남으므로
application 변경은 이전·새 version이 함께 동작하는 expand/contract 순서를 사용해야 합니다.
Rollback 후보는 migration 전용 명령이 포함되고 현재 database history·checksum과 호환된다고
검증한 image로 제한합니다. 최초 전용 명령 도입 이전 image나 V7·V10 checksum 계약이 다른
과거 image를 자동 rollback 후보로 간주하지 않습니다.

## SQL 스타일 기준

Migration SQL은 읽는 사람이 테이블 구조와 무결성 규칙을 빠르게 파악할 수 있게 작성합니다.

기본 스타일은 다음을 사용합니다.

- SQL keyword와 type은 대문자로 작성합니다. 예: `CREATE TABLE`, `BIGINT`, `NOT NULL`, `CONSTRAINT`
- table, column, constraint, index 이름은 PostgreSQL 기준 `snake_case` 소문자로 작성합니다.
- 문자열 값과 enum-like 값은 실제 저장값 그대로 작성합니다. 예: `'ROLE_MEMBER'`, `'ACTIVE'`
- 함수 이름은 대문자로 작성합니다. 예: `NOW()`, `BTRIM()`, `CHAR_LENGTH()`
- 한 파일 안에서는 column, primary key, foreign key, unique, check constraint, index를 빈 줄로 구분합니다.
- `CREATE TABLE` 안에서는 먼저 column을 나열하고, 그 뒤에 constraint를 종류별로 묶습니다.
- partial unique index처럼 데이터 무결성 의미가 있는 index는 관련 테이블 migration에 함께 둡니다.
- index가 순수 성능 최적화이고 query pattern이 나중에 확정되는 경우에는 별도 migration으로 추가할 수 있습니다.

예시:

```sql
CREATE TABLE example (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY,
    name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_example PRIMARY KEY (id),

    CONSTRAINT uk_example_name UNIQUE (name),

    CONSTRAINT chk_example_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
```

## 주석 기준

Migration SQL의 주석은 필요한 곳에만 짧게 작성합니다.
프로젝트의 공통 언어 규칙에 따라 SQL 주석은 한국어로 작성합니다.
테이블명, 컬럼명, constraint 이름만으로 의미가 드러나는 내용은 주석으로 반복하지 않습니다.
여기서 `필요한 곳`은 작성자가 헷갈린다고 느끼는 곳이 아니라, DB가 장기적으로 보장하거나 제한하는 위험 경계입니다.
새 table, column group, constraint, FK, unique/index, `ALTER TABLE`, `DROP CONSTRAINT`, seed/update/delete block은 독자가 SQL을 한 줄씩 따라 읽기 전에 의도와 보장 범위를 알 수 있어야 합니다.

주석을 권장하는 경우는 다음과 같습니다.

- 새 table이 어떤 생명주기나 aggregate/persistence 경계를 소유합니다.
- column 묶음이 식별자, actor, token, 상태, 감사 시각처럼 서로 다른 책임을 갖습니다.
- check constraint가 도메인 상태 정합성을 표현합니다.
- foreign key가 단순 join이 아니라 actor 추적, role catalog, 소유권 같은 무결성 경계를 표현합니다.
- partial unique index가 단순 성능 최적화가 아니라 무결성 규칙을 표현합니다.
- 보안상 중요한 저장 정책을 드러내야 합니다. 예: token 원문 저장 금지
- 기존 check constraint를 교체하거나 enum-like 허용값을 확장합니다.
- 운영 배포에서 lock, backfill, destructive change 같은 주의가 필요합니다.

피해야 할 주석은 다음과 같습니다.

- SQL 문법을 그대로 설명하는 주석
- 컬럼명과 같은 말을 반복하는 주석
- 문서에 있어야 할 긴 설계 설명
- 시간이 지나면 맞지 않을 가능성이 큰 운영 추측

Flyway migration SQL을 만들거나 수정하면 완료 전 아래 검증을 실행합니다.

변경 migration 전체를 다시 읽고 위험 SQL 가까이에 불변식, lock·backfill·rollback 조건이 정확히 설명됐는지 source review로 확인합니다.

이 검증은 schema나 data를 바꾸는 SQL block이 주석 없이 시작되면 실패합니다.
검증 통과는 주석 품질의 최소 조건입니다.
통과하더라도 주석이 SQL이 실제로 보장하지 않는 내용을 말하거나, 보안/상태/무결성 경계를 뭉개면 완료로 보지 않습니다.

## JPA와의 관계

- 운영 스키마 생성/변경은 Flyway가 담당합니다.
- JPA `ddl-auto`로 운영 스키마를 자동 변경하지 않습니다.
- Entity 변경과 migration 변경은 같은 작업 단위에서 함께 검토합니다.
- Entity와 DB schema가 충돌하지 않는지 테스트로 확인합니다.

## 테스트 기준

- migration syntax는 가능한 실제 PostgreSQL에 가까운 환경에서 검증합니다.
- JPA mapping 변경이 있으면 migration과 함께 repository/integration 테스트를 작성합니다.
- migration이 실패하면 실패 원인과 복구 방식을 먼저 정리합니다.
- 테스트 DB 초기화 방식은 Flyway 기준으로 유지합니다.

## 피해야 할 패턴

- 이미 적용된 migration 파일을 수정합니다.
- 한 migration에 관련 없는 여러 변경을 섞습니다.
- 운영 데이터 삭제를 사전 검토 없이 수행합니다.
- `CREATE INDEX`가 큰 테이블 lock에 미치는 영향을 검토하지 않습니다.
- JPA `ddl-auto` 결과를 운영 migration 대신 사용합니다.

## 관련 참고

- [Redgate Flyway - Migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations)
- [Spring Boot - Database Initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)

## 관련 문서

- **현재 물리 schema**: [Account 인증/인가 Schema Reference](../project/schema/100-account-auth.md)
- **persistence 구현**: [Spring Data JPA](230-spring-data-jpa.md)
- **database 기준**: [PostgreSQL](250-postgresql.md)
- **migration test**: [테스트 스택](300-testing-stack.md)
- **현재 호환성 결함 owner**: [Auth Event Taxonomy 재설계](../work-items/improvements/2026/001-auth-event-taxonomy-redesign.md)
