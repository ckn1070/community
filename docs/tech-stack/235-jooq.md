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

`jooqCodegen`은 카탈로그 schema를 소유하는 Flyway V15~V19를 `DDLDatabase`로 읽어
`build/generated-sources/jooq`에 Java model을 생성합니다. 생성물은 build artifact이므로 Git에
commit하지 않습니다. 생성 package는
`catalog.internal.adapter.out.persistence.jooq.generated`로 두어 Spring Modulith가 독립 업무
모듈로 오인하지 않게 하고, catalog persistence 구현 밖에서 의존하지 않습니다. `compileJava`는
`jooqCodegen` 뒤에 실행되어 migration을 해석할 수 없는 상태와 생성 model compile 오류를 먼저
드러냅니다.

전체 V1~V17을 입력하지 않습니다. V1~V12는 카탈로그 조회가 소비하지 않는 account·auth
schema이고, 일부 PostgreSQL 전용 DDL은 in-memory DDL simulation 대상이 아닙니다. 이 범위는
편의상 줄인 것이 아니라 카탈로그 adapter의 schema owner와 code generation 입력을 일치시킨
것입니다.

## Query와 mapping 기준

- 단순 조회는 생성 table·field를 사용하는 jOOQ DSL을 우선합니다.
- 복합 조회도 생성 table·field를 query construction에 실제로 연결합니다. Code generation을
  실행만 하고 query가 table·column 문자열만 사용하면 schema 변경의 compile-time 안전성을
  얻었다고 보지 않습니다.
- PostgreSQL의 CTE, lateral join, window function, array aggregate처럼 DSL만으로 읽기 어렵거나
  검증된 실행 계획을 불필요하게 바꾸는 부분은 plain SQL로 제한할 수 있습니다. 이때 관계와
  filter의 기반 query는 generated field DSL로 먼저 구성하고, CTE가 만든 논리 column을 접는
  집계·window처럼 schema table과 직접 결합하지 않는 부분만 문자열로 남기는 것을 우선합니다.
  Wide projection을 raw SQL로 유지하면 그 column은 compile-time 보호 밖이라는 비용을
  명시하고 실제 PostgreSQL 통합 test로 보완합니다.
- Adapter는 Spring이 주입한 `DSLContext`를 직접 사용합니다. Binding·예외 변환·관측처럼
  jOOQ가 이미 제공하는 기능을 단순 재전달하는 project wrapper는 두지 않습니다. 공통
  불변식이 새로 생길 때만 그 불변식의 이름과 테스트를 가진 경계를 추가합니다.
- 한 use case를 완성하는 고정 묶음 query는 하나의 adapter가 조정할 수 있지만, 여러 결과의
  중복 제거·부모 자식 결합·JSONB 변환은 query 실행과 분리해 각각 독립 테스트 가능한
  assembler·converter가 담당합니다. 생성 `Record`나 persistence payload는 application port를
  넘지 않습니다.

현재 브랜드 조회, 모델 검색의 `catalog_rows`, 상세의 모델·연식 query는 generated table·field
DSL로 구성합니다. 모델 검색의 `eligible`·연식 집계·대표 연식 window는 DSL이 만든 CTE의 논리
column을 plain SQL로 접습니다. 상세의 동력계통·변형·판매 구성은 많은 nullable projection과
자식 정렬을 그대로 검증하기 위해 세 wide SQL을 유지하며, 이 세 SQL의 column drift는 code
generation이 아니라 PostgreSQL 통합 test가 탐지합니다.

## 성능과 검증

- 모델 page는 count와 content 두 SQL, 상세는 최대 다섯 SQL이라는 상한을 유지합니다.
- query 수는 wrapper 호출 횟수가 아니라 jOOQ의 실제 execute listener에서 세고, 대표 목록
  SQL은 실제 PostgreSQL `EXPLAIN`으로 검사합니다.
- JSONB, array, UUID, date와 numeric 변환은 H2가 아니라 Testcontainers PostgreSQL에서
  검증합니다.
- DSL 전환으로 SQL 형태가 바뀌면 기존 실행 계획을 재사용하지 않고 동일 fixture와
  `EXPLAIN`을 다시 확인합니다.

## 재검토 조건

- plain SQL template이 늘어나 생성 model로 보호되지 않는 column·join이 핵심 경로의 대부분을
  차지하면 해당 query를 생성 table·field 기반 DSL로 더 옮깁니다.
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
