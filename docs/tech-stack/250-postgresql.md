# PostgreSQL 사용 가이드

이 문서는 PostgreSQL 데이터 모델링, 제약조건, 인덱스, JSON 사용 기준을 정의합니다.

## 목적

- 데이터 무결성을 애플리케이션과 DB 양쪽에서 지킵니다.
- PostgreSQL 기능을 필요한 곳에 명확히 사용합니다.
- 성능과 유지보수성을 함께 고려한 schema/query를 작성합니다.

## 기본 원칙

- DB는 단순 저장소가 아니라 데이터 무결성의 마지막 방어선입니다.
- primary key, foreign key, unique, not null, check constraint를 적극 사용합니다.
- index는 query pattern을 기준으로 설계합니다.
- migration 없이 schema를 변경하지 않습니다.
- PostgreSQL 전용 기능을 사용할 때는 portability보다 얻는 이득이 명확해야 합니다.

## 제약조건 기준

PostgreSQL 공식 문서는 check, not-null, unique, primary key, foreign key 같은 constraint를 제공합니다.

프로젝트 기준:

- 필수 값은 `NOT NULL`을 사용합니다.
- 중복되면 안 되는 business key는 `UNIQUE`를 사용합니다.
- 참조 무결성은 가능한 foreign key로 표현합니다.
- 상태 값의 유효 범위는 enum 또는 check constraint를 검토합니다.
- 애플리케이션 검증만 믿고 DB 제약을 생략하지 않습니다.

## 인덱스 기준

PostgreSQL 공식 문서는 B-tree, Hash, GiST, SP-GiST, GIN, BRIN 등 여러 index type을 설명합니다.

프로젝트 기준:

- 기본 equality/range/order query는 B-tree를 우선 검토합니다.
- JSONB containment 또는 key search는 GIN index를 검토합니다.
- index는 쓰기 비용과 저장 비용을 함께 고려합니다.
- foreign key column은 join/delete/update 패턴에 따라 index를 검토합니다.
- pagination과 sorting query는 where/order by 조합을 기준으로 index를 설계합니다.
- 사용하지 않는 index는 제거 후보로 봅니다.

## JSON/JSONB 기준

PostgreSQL은 `json`과 `jsonb`를 모두 제공합니다.
공식 문서는 `json`은 입력 텍스트를 보존하고, `jsonb`는 분해된 binary 형식으로 저장되어 처리와 indexing에 유리하다고 설명합니다.

프로젝트 기준:

- query가 필요한 JSON 데이터는 `jsonb`를 우선 검토합니다.
- 정규화 가능한 핵심 도메인 데이터는 column/table로 모델링합니다.
- JSONB는 유연한 부가 속성, 외부 payload snapshot, 검색 빈도가 낮은 metadata에 사용합니다.
- JSONB 내부 필드를 자주 조회하면 expression index 또는 schema 분리를 검토합니다.
- JSONB 문서가 지나치게 커지면 lock contention과 update 비용을 고려합니다.

## 트랜잭션과 동시성

- application usecase 단위로 transaction 경계를 잡습니다.
- 긴 transaction을 피합니다.
- 외부 API 호출을 DB transaction 안에 오래 묶지 않습니다.
- 동시 수정 가능성이 있는 데이터는 optimistic/pessimistic locking을 검토합니다.
- unique constraint와 transaction isolation으로 race condition을 방어합니다.

## 명명 규칙

기본 명명 규칙은 다음을 사용합니다.

- table: `snake_case` 복수형 또는 단수형 중 프로젝트에서 하나로 통일합니다.
- column: `snake_case`
- primary key: `pk_<table>`
- foreign key: `fk_<from_table>_<to_table>`
- unique: `uk_<table>_<columns>`
- index: `idx_<table>_<columns>`
- check: `chk_<table>_<rule>`

초기 기본값은 table 단수형입니다.
예: `account`, `article`, `comment`

## JPA와의 관계

- DB schema는 Flyway migration으로 생성합니다.
- JPA annotation은 schema 의도를 코드에서도 드러내는 보조 수단으로 사용합니다.
- DB constraint와 Entity validation이 충돌하지 않게 합니다.
- PostgreSQL 예약어를 table/column 이름으로 사용하지 않습니다.
- PostgreSQL 전용 type 사용 시 JPA mapping 전략을 문서화합니다.

## 테스트 기준

- PostgreSQL 전용 SQL은 실제 PostgreSQL에 가까운 테스트 환경을 사용합니다.
- constraint 위반 테스트는 application validation과 DB constraint를 모두 고려합니다.
- index는 단순 존재보다 query pattern과 실행 계획으로 검토합니다.
- migration 테스트는 Flyway와 함께 수행합니다.

## 피해야 할 패턴

- 모든 유연성을 이유로 JSONB에 핵심 도메인 데이터를 넣습니다.
- constraint 없이 application validation만 둡니다.
- query pattern 없이 index를 추가합니다.
- 운영 테이블에 큰 lock을 유발하는 migration을 무검토로 적용합니다.
- nullable column으로 상태 의미를 암묵적으로 표현합니다.

## 관련 참고

- [PostgreSQL - Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html)
- [PostgreSQL - Indexes](https://www.postgresql.org/docs/current/indexes.html)
- [PostgreSQL - JSON Types](https://www.postgresql.org/docs/current/datatype-json.html)

## 관련 문서

- **persistence 구현**: [Spring Data JPA](230-spring-data-jpa.md)
- **schema migration**: [Flyway](240-flyway.md)
- **database 경계**: [경계와 매핑 규칙](../architecture/220-boundary-and-mapping-rules.md)
