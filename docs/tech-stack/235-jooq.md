# jOOQ 사용 가이드

이 문서는 jOOQ의 적용 범위, code generation과 카탈로그 read adapter의 SQL·매핑 기준을
소유합니다. 도입 결정의 후보 비교와 당시 근거는
[016 신차 카탈로그 DB 설계 결정 기록](../work-items/features/2026/016-new-car-catalog-query-api-design.md)이
소유합니다.

## 적용 범위

- jOOQ는 `catalog` 모듈의 복합 조회 전용 persistence adapter에만 사용합니다.
- account·authentication의 변경 중심 persistence는 기존 Spring Data JPA를 유지합니다.
- application port와 read model은 `DSLContext`, jOOQ `Record`와 생성 table type을 노출하지
  않습니다.
- schema migration owner는 Flyway입니다. jOOQ가 DDL을 생성하거나 migration을 대신하지
  않습니다.

## Code generation

`jooqCodegen`은 카탈로그 schema를 소유하는 Flyway V13~V17을 `DDLDatabase`로 읽어
`build/generated-sources/jooq`에 Java model을 생성합니다. 생성물은 build artifact이므로 Git에
commit하지 않습니다. `compileJava`는 `jooqCodegen` 뒤에 실행되어 migration을 해석할 수 없는
상태와 생성 model compile 오류를 먼저 드러냅니다.

전체 V1~V17을 입력하지 않습니다. V1~V12는 카탈로그 조회가 소비하지 않는 account·auth
schema이고, 일부 PostgreSQL 전용 DDL은 in-memory DDL simulation 대상이 아닙니다. 이 범위는
편의상 줄인 것이 아니라 카탈로그 adapter의 schema owner와 code generation 입력을 일치시킨
것입니다.

## Query와 mapping 기준

- 단순 조회는 생성 table·field를 사용하는 jOOQ DSL을 우선합니다.
- PostgreSQL의 CTE, lateral join, window function, array aggregate를 함께 사용하고 이미 실제
  실행 계획을 검증한 조회는 SQL을 그대로 유지할 수 있습니다. 이 경우 jOOQ가 binding,
  실행과 `Record` 변환을 담당하지만 SQL 문자열 자체의 column 이름까지 compile-time에
  검사한다고 주장하지 않습니다.
- Named parameter 변환은 catalog 내부의 단일 실행 경계가 Spring parser를 재사용해 담당하고,
  변환된 positional parameter의 binding과 실행은 jOOQ가 담당합니다. SQL literal이나
  PostgreSQL `::type` cast를 parameter로 오인하지 않아야 하며 실제 PostgreSQL 통합 test로
  검증합니다.
- 결과 행의 중복 제거와 다섯 묶음의 최종 graph 결합은 read model 의미를 아는 catalog
  adapter가 담당합니다. 생성 record나 persistence payload를 application으로 전달하지 않습니다.

## 성능과 검증

- 모델 page는 count와 content 두 SQL, 상세는 최대 다섯 SQL이라는 상한을 유지합니다.
- query 수는 jOOQ 실행 경계에서 세고, 대표 목록 SQL은 실제 PostgreSQL `EXPLAIN`으로
  검사합니다.
- JSONB, array, UUID, date와 numeric 변환은 H2가 아니라 Testcontainers PostgreSQL에서
  검증합니다.
- DSL 전환으로 SQL 형태가 바뀌면 기존 실행 계획을 재사용하지 않고 동일 fixture와
  `EXPLAIN`을 다시 확인합니다.

## 재검토 조건

- raw SQL column rename이 code generation만으로 차단되지 않는 문제가 반복되면 복합 SQL도
  생성 table·field 기반 DSL로 전환합니다.
- 다섯 상세 query의 조립 비용이 측정 가능한 병목이면 jOOQ `MULTISET` 단일 조회와 현재
  고정 묶음 조회의 실행 계획·전송량을 비교합니다.
- catalog에 단순 CRUD가 추가돼도 jOOQ를 자동 확장하지 않습니다. 변경 aggregate는 JPA와
  현재 transaction 경계를 먼저 비교합니다.

## 관련 문서

- **현재 적용 stack**: [현재 기술 스택](100-current-stack.md)
- **변경 persistence 기준**: [Spring Data JPA](230-spring-data-jpa.md)
- **schema owner**: [Flyway](240-flyway.md)
- **database 계약**: [PostgreSQL](250-postgresql.md)
- **현재 catalog 조회 경로**: [자동차 카탈로그 Schema Reference](../project/schema/110-car-catalog.md)
