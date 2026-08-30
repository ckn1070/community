# 신차 카탈로그 DB 설계 결정 기록

## 문서 역할과 현재 기준

이 문서는 016 작업에서 신차 카탈로그 DB 구조를 선택한 질문, 조사 근거, 대안, trade-off와
재검토 조건을 보존합니다. 현재 업무 개념은
[130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md), 구현된 table·column·constraint·
index·조회 경로와 현재 설계 이유는
[110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)가 소유합니다.

아래의 상세 table·column은 구현 전에 합의한 목표 계약의 snapshot입니다. 현재 schema를
확인하거나 이후 변경을 반영하는 SOT가 아니며, 현재 migration과 schema reference가 바뀌어도
이 결정 기록을 현재형으로 맞추지 않습니다. 선택 자체를 다시 열 때만 당시 대안과 비교하기
위해 갱신합니다.

016의 현재 상태와 다음 작업은
[016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md) 본문이 소유합니다. 실제
브랜드 전체 데이터 범위와 적재 계획은
[017 자동차 카탈로그 초기 데이터 구축](017-car-catalog-initial-data.md)에서 확인합니다.

## 결정 질문과 선택 기준

사용자가 요청한 것은 가능한 자동차 항목을 나열하는 것이 아니라 다음 개발 단계에서 바로
사용할 수 있는 관계형 schema입니다. 설계 목표는 다음과 같습니다.

- 브랜드, 모델, 세대, 연식과 현재·과거 판매 차량을 구분합니다.
- 내연기관, 하이브리드, 전기차와 수소전기차의 주요 제원을 같은 조회 구조에서 표현합니다.
- 실제 구매 단위인 트림과 기술 제원이 달라지는 차량 변형을 구분합니다.
- 가격 변경 이력, 트림별 선택 품목과 조건별 정부 인증값을 잃지 않습니다.
- 검색·filter·정렬·무결성에 쓰는 값은 명시적인 관계와 typed column으로 저장합니다.
- 제조사마다 형식이 크게 다르고 조회 조건으로 사용하지 않는 설명형 정보만 제한된 JSONB로
  보존합니다.
- 과거 차량 조회에 필요하지 않은 모든 정정 시점의 이중 시간축, fact마다 별도 identity/version,
  field마다 source 테이블을 만들지 않습니다.

## 조회 기술 선택 결정

### 배경과 평가 기준

최초 구현은 별도 비교 기록 없이 `NamedParameterJdbcTemplate`을 브랜드 목록부터 사용했고,
모델 목록과 상세가 같은 실행 방식을 이어받았습니다. Raw SQL이 필요하다는 사실은 SQL 실행
기술을 결정하지 않으므로 다음 기준으로 후보를 다시 비교했습니다.

1. CTE, lateral join, window function, array aggregate와 JSONB를 사용하는 PostgreSQL SQL을
   손실 없이 표현합니다.
2. 목록 두 SQL과 상세 다섯 SQL의 실행 상한 및 실제 실행 계획을 관찰할 수 있습니다.
3. 11개 table·column 변경을 application compile 이전에 가능한 범위까지 감지합니다.
4. 평면 목록과 중첩 상세 read model의 변환 위치가 명확하고 persistence type이 application
   port를 넘지 않습니다.
5. Spring transaction에 참여하며 reactive stack이나 별도 runtime을 강제하지 않습니다.
6. 새 dependency, 설정, 생성물과 학습 비용보다 장기 변경 비용 감소가 커야 합니다.

### 후보 전체 비교

| 계열·후보 | 이 조회에서의 장점 | 비용·한계 | 판정 |
| --- | --- | --- | --- |
| plain JDBC | dependency가 없고 실행 경로가 직접적임 | connection·binding·예외·행 변환을 모두 직접 소유함 | Spring 사용 project에서 선택 이점 없음 |
| Spring JDBC `JdbcTemplate`·`NamedParameterJdbcTemplate`·`JdbcClient` | 기존 dependency, SQL과 query 수가 명시적임 | schema type 검사가 없고 상세 graph의 행 변환·조립을 Java가 전부 소유함 | 새 도구 금지 시 fallback |
| Spring Data JPA native projection | 기존 persistence 관례와 transaction을 유지하고 평면 projection이 간단함 | 복합 SQL은 결국 문자열이며 nested graph와 여러 결과 병합은 별도 mapping이 필요함 | 브랜드 같은 평면 조회에는 가능하지만 전체 read side의 공통 해법은 아님 |
| `EntityManager`·Hibernate native query | JPA 실행 경로에서 tuple·result mapping을 세밀하게 제어함 | provider API와 mapping 선언이 늘고 schema type 안정성은 얻지 못함 | 제외 |
| MyBatis | SQL과 `resultMap`, `association`, `collection`, type handler로 수동 builder 일부를 선언화함 | XML·mapper라는 두 번째 source와 type handler를 운영하고 다섯 조회의 최종 결합은 남음 | raw SQL·mapping 최소화가 최우선이면 2순위 |
| jOOQ + code generation | PostgreSQL 표현력, 통일된 query·record API, 생성 schema model, 실행 listener와 Spring transaction 통합을 함께 제공함 | dependency와 build-time generation이 추가되고 raw SQL 문자열은 자동 type 검사되지 않음 | **선택** |
| QueryDSL SQL | 생성 schema type과 Java DSL을 제공함 | Spring Boot 자동 구성과 PostgreSQL record mapping·관측 통합을 별도로 구성해야 함 | jOOQ보다 추가 접착 코드가 많아 제외 |
| Jdbi | SQL Object·row mapper·reducer가 Spring JDBC보다 간결할 수 있음 | schema code generation이 없고 project의 별도 persistence convention이 됨 | 제외 |
| Spring Data JDBC | 단순 aggregate 저장 모델과 repository가 간결함 | 이 조회는 aggregate CRUD가 아니라 여러 관계를 접는 read projection이어서 별도 SQL·조립이 남음 | 목적 불일치 |
| R2DBC | non-blocking DB I/O를 제공함 | 현재 전체 stack이 MVC·JDBC transaction이며 reactive 전환 비용이 조회 한 곳의 이점보다 큼 | 목적 불일치 |
| DB view·materialized view·function | 반복 SQL을 DB에 집중하거나 precompute할 수 있음 | 배포·권한·refresh lifecycle이 추가되고 현재 병목 증거가 없음 | 측정된 병목이 생길 때 재검토 |
| 검색엔진·cache·비정규화 read model | 검색·정렬을 독립 확장하고 반복 조회 latency를 낮추며 source DB 부하를 분리할 수 있음 | 현재 11개 table의 기준일 가격·판매 상태와 공개 여부를 별도 저장소에 동기화해야 하고 지연·누락·재처리·전체 재구축·source DB와의 일관성 검증 책임이 추가됨 | 실제 PostgreSQL 계획·latency·부하가 목표를 벗어나거나 전문 검색 요구가 생기기 전에는 제외하고, 발생 시 동기화 허용 지연·복구 시간·재구축 절차를 먼저 결정해 재검토 |

### 선택과 장점

카탈로그 read side만 Spring Boot 4.0.6이 관리하는 jOOQ 3.19.32를 사용하고
account·authentication의 변경 persistence는
Spring Data JPA를 유지합니다. Flyway V13~V17을 `DDLDatabase` 입력으로 사용해 compile 전에
schema model을 생성하며 generated source는 `build/` 아래에 두고 commit하지 않습니다.

이 선택으로 얻는 구체적 장점은 다음과 같습니다.

- Flyway catalog DDL이 code generation에서 해석되지 않거나 생성 type이 compile되지 않는
  상태를 build에서 차단합니다.
- SQL 실행과 결과를 jOOQ `DSLContext`·`Record` 한 API로 통일해 JDBC `ResultSet`과 checked
  exception을 query adapter 밖으로 제거합니다.
- jOOQ 실행 경계에서 query 수와 SQL을 관찰해 기존 목록 2개·상세 5개 상한과 `EXPLAIN` 검사를
  유지합니다.
- JPA entity graph나 lazy loading을 만들지 않아 read model이 변경 aggregate에 결합되지
  않습니다.
- 향후 단순 query는 생성 table·field 기반 DSL로, 중첩 graph는 `MULTISET`으로 이동할 수 있는
  경로가 생깁니다.

PostgreSQL 전용 복합 SQL을 모두 한 번에 DSL로 번역하지 않습니다. 브랜드와 상세의 모델·연식,
모델 검색에서 실제 table을 join하고 filter하는 `catalog_rows`는 generated field DSL로
전환했습니다. 검증된 CTE 집계·window와 상세의 세 wide child projection은 SQL 형태를 유지하고
jOOQ가 binding·실행·record mapping을 담당합니다. 따라서 raw SQL에 남은 column rename까지
compile-time에 잡는다고 과장하지 않습니다. 정확한 보호 범위와 전환 조건은
[235 jOOQ 사용 가이드](../../../tech-stack/235-jooq.md)가 소유합니다.

### 조회 구조 분리 결정

조회 실행 기술과 application 경계를 별개의 질문으로 비교했습니다. 세 조회를 한
`CatalogReadPort`에 두면 각 service가 사용하지 않는 두 능력에도 의존하고, 하나의 adapter가
브랜드 집계·모델 검색·상세 graph라는 서로 다른 변경 축과 성능 계약을 함께 소유합니다. 반대로
SQL 하나마다 Port와 Adapter를 만들면 상세 한 use case를 완성하는 다섯 query의 transaction과
조립 책임이 흩어집니다.

따라서 독립 consumer와 계약이 있는 브랜드 목록, 모델 검색, 모델 상세를 각각
`CatalogBrandReadPort`, `CatalogModelSearchPort`, `CatalogModelDetailReadPort`로 분리하고 각
Port를 jOOQ adapter 하나가 구현합니다. 상세 adapter는 다섯 query를 한 transaction 안에서
조정하되, 결과 graph 중복 제거는 `CatalogModelDetailAssembler`, JSONB 변환은
`CatalogJsonConverter`에 맡깁니다. 이 경계는 interface 개수나 SQL 개수가 아니라 consumer,
변경 이유와 독립 검증 가능성으로 정했습니다.

`CatalogJooqQuery`처럼 `DSLContext.fetch`를 전달만 하는 wrapper는 제거했습니다. Query 수는
wrapper 호출이 아니라 jOOQ execute listener에서 실제 statement를 관찰합니다. 공통 binding,
예외 변환이나 관측 정책이 adapter마다 달라지는 문제가 생기기 전에는 새 실행 wrapper를 만들지
않습니다. 일반 재사용 기준은 [200 헥사고날 아키텍처 적용 원칙](../../../architecture/200-hexagonal-architecture.md),
현재 jOOQ 경계는 [235 jOOQ 사용 가이드](../../../tech-stack/235-jooq.md)가 소유합니다.

### 비용과 재검토 조건

- `compileJava` 전에 code generation이 실행되므로 build 시간이 늘어납니다. catalog migration
  수가 커져 증분 build 비용이 문제가 되면 generation cache와 task input을 측정해 조정합니다.
- 전체 migration V1~V17이 아니라 catalog owner인 V13~V17만 생성 입력입니다. 다른 schema를
  query하기 시작하면 해당 owner migration을 명시적으로 추가합니다.
- raw SQL column drift가 실제 결함으로 반복되면 해당 query를 생성 field 기반 DSL로 옮깁니다.
- 중첩 graph의 수동 조립 비용이 병목으로 측정되면 jOOQ `MULTISET`, MyBatis `resultMap`, 현재
  고정 묶음 query를 실행 계획·전송량·변경 비용으로 다시 비교합니다.
- PostgreSQL 검색 latency·DB 부하가 운영 목표를 벗어나거나 형태소·오타 허용·연관도 같은 전문
  검색 요구가 생기면 검색엔진·cache·비정규화 read model을 재검토합니다. 이때 허용 가능한
  동기화 지연, 실패 event 재처리, 전체 재구축 시간과 source DB 대조 방법을 수용 기준에 먼저
  포함합니다.

## 결정 당시 대표 자료 검토

대표 자료는 특정 차량의 값을 seed로 확정하기 위해서가 아니라, schema가 실제 정보 형태를
빠뜨리지 않는지 확인하기 위해 사용했습니다.

| 대표 유형 | 빠지기 쉬운 정보 | 저장 위치 | 판정 |
| --- | --- | --- | --- |
| K5 가솔린·하이브리드 세단 | 연식별 동력계통·트림, 세제 혜택 전·후 가격, 기본·선택 품목 | powertrain, configuration, price, option | 현재 범위에 포함 |
| 카니발 MPV | 인승·루프·휠별 치수와 공차중량, 빌트인 캠 조건별 연비·CO₂ | vehicle_variant, certification | 현재 범위에 포함 |
| EV6형 전기차 | 배터리 총·사용 가능 용량과 Ah·V·제조 정보, 전·후 모터, AC·DC 대표 충전 조건과 단자, 휠별 전비·주행거리 | powertrain, component, certification | 현재 범위에 포함 |
| 견인 가능한 승용·SUV | 제동·비제동 견인중량, 루프 적재중량, 최소 회전반경 | vehicle_variant | 현재 범위에 포함 |
| 국내·해외 인증 자료 | 같은 효율·주행거리 수치에 적용된 인증 기준 | certification | 현재 범위에 포함 |
| 넥쏘 수소전기차 | 모터·연료전지·배터리·수소 저장량, km/kg·항속거리와 보증 | component, certification, configuration JSONB | 현재 범위에 포함 |
| 공통 판매 정보 | 판매 채널, 판매 기간, 적용일별 기본 가격과 옵션 가격·선택 가능 상태 | configuration, price, option | 현재 범위에 포함 |
| 상세 타이어 등급·옵션 조합·세금·보조금 | 타이어 상품별 효율 등급, 옵션 필수·배타 규칙, 실구매 비용 | 없음 | 현재 API 범위 밖이며 필요할 때 별도 설계 |

대표 자료 기준으로 현재 조회 범위에 필요한 정보 분류에는 막는 누락이 없습니다. 다만 모든
제조사·과거 연식의 원문 항목까지 빠짐없다고 확정하는 단계는 아닙니다. 실제 전체 누락 검증은
017의 공식 자료 inventory에서 `원문 항목 → table.column` 대응이 불가능한 항목을 발견하는
방식으로 수행하며, 반복되는 핵심값이 나오면 데이터 적재 전에 이 schema를 보완합니다.

공식 자료는 [K5 가격·옵션](https://www.kia.com/kr/vehicles/k5/price/private),
[카니발 제원](https://www.kia.com/kr/vehicles/carnival/specification),
[카니발 공식 catalog](https://www.kia.com/content/dam/kwp/kr/ko/vehicles/pdf/catalog/catalog_carnival.pdf),
[EV6 제원](https://www.kia.com/kr/vehicles/ev6/specification),
[EV6 충전 제원](https://www.kia.com/uk/new-cars/ev6/specification/),
[아이오닉 5 충전 제원](https://www.hyundai.com/in/en/find-a-car/ioniq-5/specification),
[EX30 배터리·견인·루프 제원](https://www.volvocars.com/us/cars/ex30-electric/specifications/),
[넥쏘 제품 정보](https://www.hyundai.com/content/hyundai/kr/ko/vehicles/nexo/highlights.html)를
대조했습니다. 값은 제조사의 연식 변경에 따라 바뀔 수 있으므로 실제 적재 시 다시 확인합니다.

## 선택한 테이블 분리 기준

새 테이블은 아래 조건 중 하나 이상을 만족할 때만 만듭니다.

1. 부모 한 행에 자식이 여러 개 존재합니다. 예: 동력 장치, 가격 이력, 선택 품목, 인증 조건.
2. 독립적인 생명주기나 유효 시점이 있습니다. 예: 연식, 판매 구성, 가격.
3. FK·UNIQUE·CHECK로 별도 무결성을 보호해야 합니다. 예: 모델 소속 세대, 구성 소속 변형.
4. API의 검색·filter·정렬 조건으로 독립 조회됩니다. 예: 차체, 동력 형식, 연료, 가격.

다음 정보는 별도 테이블로 만들지 않습니다.

- 트림명은 판매 구성 밖에서 독립적으로 쓰이지 않으므로 `car_configuration.trim_name`에 둡니다.
- 국가와 시장은 첫 범위가 대한민국 하나이므로 `country_code`, `market_code`, `currency_code`로
  저장합니다. 두 번째 시장을 지원할 때만 기준정보 테이블을 추가합니다.
- 기본 품목, 보증, 안전도와 media는 첫 API에서 표시만 하고 독립 검색하지 않으므로
  configuration의 제한된 JSONB에 둡니다. 색상은 configuration마다 여러 선택지가 있고 유상
  색상도 있으므로 option 관계의 명시적 행으로 저장합니다.
- 제조사별 희소 수치는 첫 schema에 범용 EAV 테이블을 만들지 않습니다. 두 제조사 이상에서
  반복되며 비교 요구가 확인되면 typed column으로 승격합니다.

## 선택한 관계와 대안 비교

```text
car_brand
    └─ car_model
        └─ car_generation
            └─ car_model_year
                ├─ car_powertrain
                │   └─ car_powertrain_component
                └─ car_vehicle_variant ── car_powertrain
                    ├─ car_certification
                    └─ car_configuration
                        ├─ car_configuration_price
                        └─ car_configuration_option
```

물리 테이블은 정확히 11개이며 모두 자동차 제품 구조를 표현합니다. 공식 자료와 DB 값의
대응은 후속 초기 데이터 작업의 version-controlled mapping에서 관리하고 runtime schema에는
넣지 않습니다. 현재 157개 후보처럼 각 사실에 identity/version/source 테이블을 세 개씩 만들지
않습니다.

| 대안 | 장점 | 부족한 점 | 판정 |
| --- | --- | --- | --- |
| 기존 7개 | 작고 칼럼이 명시되어 바로 시작 가능 | 모델 연식, 차체·인승·휠 변형, 가격 이력과 옵션이 없어 실제 K5·카니발·EV6 자료를 손실함 | 핵심 5개 구조를 유지하고 보완함 |
| 선택한 11개 | configuration 하나에서 가격·옵션·인증까지 조회 가능하고 runtime에 쓰이지 않는 출처 FK가 없음 | JSONB 표시 정보는 독립 검색할 수 없고 다국가·견적 규칙은 후속 확장이 필요함 | 첫 카탈로그 조회의 구현안 |
| fact별 identity/version/source 157개 이상 | 모든 정정 시점과 field별 근거를 이론상 복원 가능 | 제품이 요구하지 않은 감사 체계를 위해 migration·join·trigger·입력 작업이 폭증하고 실제 칼럼 설계가 사라짐 | 제외함 |

### 테이블별 책임과 분리 이유

| 테이블                        | 한 행이 나타내는 것             | 분리한 이유                            | 더 합치거나 나누지 않은 이유                     |
| -------------------------- | ----------------------- | --------------------------------- | ------------------------------------ |
| `car_brand`                | 자동차 브랜드                 | 여러 모델의 공통 소유자                     | 국가 기준정보는 현재 한 시장이라 별도 테이블 불필요        |
| `car_model`                | K5·카니발·EV6 같은 모델        | 세대가 바뀌어도 유지되는 검색·표시 단위            | 브랜드에 합치면 세대·연식 관계를 표현할 수 없음          |
| `car_generation`           | 한 세대 또는 상품성 변경 단계       | 한 모델 아래 여러 세대·facelift를 시간 순서로 보존 | 판매 기간은 실제 판매 단위인 model year가 소유      |
| `car_model_year`           | 한 시장의 모델 연식·edition     | 같은 세대 안에서도 사양·판매 기간이 달라짐          | market·연식·edition이 함께 한 행의 식별 기준     |
| `car_powertrain`           | 연식이 제공하는 동력계통 조합        | 검색에 쓰는 동력 형식·연료와 제조사 시스템 출력을 소유   | 엔진·모터를 한 행에 반복하지 않고 component로 분리    |
| `car_powertrain_component` | 엔진·모터·배터리·탱크 한 개        | 한 동력계통에 같은 종류 장치가 여러 개 존재         | 종류별 테이블로 쪼개면 공통 출력·용량 관계가 반복됨        |
| `car_vehicle_variant`      | 차체·인승·휠·동력계통이 결합된 기술 변형 | 같은 트림명이어도 제원·인증 조건이 달라질 수 있음      | 차체·휠 조합을 별도 테이블로 나누면 첫 API의 join만 증가 |
| `car_configuration`        | 실제 판매 트림·채널             | 기술 변형과 판매 기간·기본 품목의 생명주기가 다름      | 독립 trim 사전은 현재 소비자가 없어 만들지 않음        |
| `car_configuration_price`  | 특정 날짜부터 적용된 기본 가격       | 트림 판매 기간 안에서 가격만 독립적으로 변경됨        | configuration에 덮어쓰면 과거 가격을 잃음        |
| `car_configuration_option` | 특정 날짜부터의 옵션 가격·선택 상태    | 한 트림에 옵션이 여러 개이고 옵션별 변경 시점이 다름    | 범용 가격 테이블로 합치면 대상 FK와 제약이 모호해짐       |
| `car_certification`        | 한 기술 변형의 측정 조건별 공식 인증값  | 휠·카메라·온도 조건마다 효율·주행거리가 달라짐        | 조건을 JSONB로 두면 단위·필수값·중복을 DB가 보호하지 못함 |

### 겹쳐 보이는 값의 책임

| 값 | 각각의 책임 | 유지·제거 판정 |
| --- | --- | --- |
| `id`, `public_id`, `code` | `id`는 내부 FK, `public_id`는 외부 API 식별자, `code`는 부모 범위 안의 안정된 업무 key | 역할이 달라 유지 |
| 각 테이블의 `created_at`, `updated_at` | 해당 행이 DB에 생성·정정된 시각 | 행 단위 정정 추적에 필요하므로 각 물리 테이블에 명시 |
| model year의 `released_on`과 `sales_started_on` | `released_on`은 공식 출시 발표일, `sales_started_on`은 실제 판매 범위의 시작 | 둘이 다를 수 있어 유지 |
| model year와 configuration의 판매 기간 | 연식 기간은 해당 edition의 시장 판매 범위, configuration 기간은 트림·채널별 판매 범위 | 둘 다 유지하되 기간 포함 관계는 017 원문 mapping에서 검토 |
| price와 option의 `effective_from` | 기본 가격 변경과 옵션 가격·선택 상태 변경은 서로 독립적으로 발생 | 둘 다 유지; 하나의 공통 version 행으로 묶지 않음 |
| price와 option의 가격 칼럼 | price는 차량 기본 가격, option은 기본 가격에 더하는 개별 선택 품목 금액 | 의미와 FK가 달라 유지; 범용 가격 테이블로 합치지 않음 |
| powertrain과 component의 출력·연료 | powertrain은 제조사가 발표한 시스템 전체 값, component는 개별 장치 값 | 합산으로 대체할 수 없어 둘 다 유지 |
| `max_*_charge_kw`와 `*_charge_power_kw` | 전자는 차량의 최대 수전 능력, 후자는 대표 충전 시간의 외부 충전기 조건 | 240 kW 차량을 350 kW 충전기로 측정할 수 있어 둘 다 유지하고 대소 관계를 강제하지 않음 |
| 배터리 `capacity_kwh`와 `usable_capacity_kwh` | 전자는 제조사가 공개한 총 용량, 후자는 사용 가능 용량 | 서로 계산하지 않고 공개된 값만 유지; 사용 가능 용량은 총 용량 이하로 검증 |
| variant와 certification의 휠·타이어·공차중량 | variant는 차량 제원, certification은 공식 측정 조건 | 의미가 달라 둘 다 유지하고 조건 차이는 017 원문 mapping에서 확인 |
| 상위 행의 공개 여부 | 현재 API는 공개된 configuration이 있을 때만 brand·model·year를 노출 | 상위 `is_published`는 중복이므로 제거 |

## 선택 당시 공통 저장 계약

- 내부 조인은 `BIGINT GENERATED BY DEFAULT AS IDENTITY` PK를 사용합니다.
- API에 노출하는 브랜드·모델·세대·연식·동력계통·차량 변형·판매 구성은 `UUID public_id`를
  별도 `UNIQUE NOT NULL`로 가집니다.
- 이름·code·URL 문자열은 빈 문자열을 허용하지 않습니다.
- `created_at`, `updated_at`은 `TIMESTAMPTZ NOT NULL DEFAULT NOW()`이며
  `updated_at >= created_at`을 검사합니다.
- 판매 종료일은 exclusive입니다. `sales_ended_on IS NULL OR sales_started_on < sales_ended_on`을
  검사합니다.
- `sales_ended_on=NULL`은 판매 종료를 확인하지 못했다는 뜻이 아니라 종료되지 않은 기간이라는
  뜻입니다. 종료 여부나 날짜를 확인할 수 없는 과거 행은 공개하지 않고 016에서 `BLOCKED`로
  처리합니다.
- `is_published`는 제조사의 공개 여부가 아니라 Carmunity가 해당 판매 구성을 비회원 API에
  노출해도 된다고 검수한 상태입니다. 브랜드·모델·세대·연식의 노출 여부는 공개된 판매 구성의
  존재로 계산하고 상위 테이블에 같은 flag를 반복하지 않습니다.
- 날짜에 따른 `UPCOMING`, `ON_SALE`, `DISCONTINUED` 상태는 판매 시작·종료일로 계산하고 별도
  상태 칼럼으로 중복 저장하지 않습니다.
- enum은 Java enum과 동일한 `VARCHAR` + `CHECK`로 고정합니다.

## 선택 당시 목표 table·column

이 절은 구현 입력을 복원하기 위한 historical snapshot입니다. 현재 물리 구조를 확인할 때는
[110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)를 사용합니다.

### 1. `car_brand`

| column | type·null | 의미 |
| --- | --- | --- |
| `id` | `BIGINT`, PK | 내부 식별자 |
| `public_id` | `UUID NOT NULL`, UNIQUE | 공개 식별자 |
| `code` | `VARCHAR(40) NOT NULL`, UNIQUE | 변경하지 않는 내부 code |
| `name_ko` | `VARCHAR(80) NOT NULL` | 국내 표시 이름 |
| `name_en` | `VARCHAR(80) NULL` | 영문 표시 이름 |
| `country_code` | `CHAR(2) NOT NULL` | 브랜드 기준 ISO 3166-1 alpha-2 |
| `homepage_url` | `TEXT NULL` | 공식 홈페이지 |
| `display_order` | `INTEGER NOT NULL DEFAULT 0` | 브랜드 목록 순서 |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL` | 생성·수정 시각 |

`display_order >= 0`, `country_code` 대문자 두 글자를 검사합니다.

### 2. `car_model`

| column                     | type·null                    | 의미                 |
| -------------------------- | ---------------------------- | ------------------ |
| `id`                       | `BIGINT`, PK                 | 내부 식별자             |
| `public_id`                | `UUID NOT NULL`, UNIQUE      | 공개 식별자             |
| `brand_id`                 | `BIGINT NOT NULL`, FK        | 소속 브랜드             |
| `code`                     | `VARCHAR(60) NOT NULL`       | 브랜드 안의 안정 code     |
| `name_ko`                  | `VARCHAR(100) NOT NULL`      | K5·카니발 같은 표시 이름    |
| `name_en`                  | `VARCHAR(100) NULL`          | 영문 이름              |
| `name_normalized`          | `VARCHAR(100) NOT NULL`      | NFKC·소문자·공백 정리 검색값 |
| `summary`                  | `TEXT NULL`                  | 모델 요약              |
| `display_order`            | `INTEGER NOT NULL DEFAULT 0` | 브랜드 안의 표시 순서       |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL`       | 생성·수정 시각           |

`(brand_id, code)`와 `(brand_id, name_normalized)`를 UNIQUE로 둡니다. 첫 데이터 규모의
literal 부분 검색은 작은 `car_model` 전체를 scan합니다. 일반 B-tree가 선행 wildcard 검색을
가속한다고 가정하지 않으며, 실행 계획에서 필요성이 확인되기 전에는 trigram extension과
전용 index를 추가하지 않습니다.

### 3. `car_generation`

facelift를 별도 version 체계로 만들지 않고, 실제로 이름·차체·상품 구성이 달라지는 상품성 변경
단계를 generation 행으로 보존합니다.

| column | type·null | 의미 |
| --- | --- | --- |
| `id` | `BIGINT`, PK | 내부 식별자 |
| `public_id` | `UUID NOT NULL`, UNIQUE | 공개 식별자 |
| `model_id` | `BIGINT NOT NULL`, FK | 소속 모델 |
| `code` | `VARCHAR(60) NOT NULL` | 제조사 project code 또는 내부 code |
| `name` | `VARCHAR(120) NOT NULL` | `3세대`, `더 뉴 K5` 같은 이름 |
| `generation_order` | `SMALLINT NOT NULL` | 모델 안의 시간 순서 |
| `change_type` | `VARCHAR(20) NOT NULL` | `FULL_CHANGE`, `FACELIFT`, `MINOR_CHANGE` |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL` | 생성·수정 시각 |

`(model_id, code)`, `(model_id, generation_order)`를 UNIQUE로 둡니다. 판매 기간은 실제 시장·연식
단위인 model year가 소유하며 generation에는 같은 날짜를 반복하지 않습니다.

### 4. `car_model_year`

| column | type·null | 의미 |
| --- | --- | --- |
| `id` | `BIGINT`, PK | 내부 식별자 |
| `public_id` | `UUID NOT NULL`, UNIQUE | 공개 식별자 |
| `generation_id` | `BIGINT NOT NULL`, FK | 소속 세대 |
| `market_code` | `CHAR(2) NOT NULL DEFAULT 'KR'` | 판매 시장 |
| `model_year` | `SMALLINT NOT NULL` | 연식 연도 |
| `edition_code` | `VARCHAR(40) NOT NULL` | 같은 연도의 edition 구분 code |
| `display_name` | `VARCHAR(120) NOT NULL` | `The 2027 Carnival` 같은 표시명 |
| `released_on` | `DATE NULL` | 공식 출시일 |
| `sales_started_on` | `DATE NOT NULL` | 해당 연식·edition의 시장 판매 시작일 |
| `sales_ended_on` | `DATE NULL` | 해당 연식·edition의 시장 판매 종료일 exclusive |
| `summary` | `TEXT NULL` | 연식 요약 |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL` | 생성·수정 시각 |

`model_year BETWEEN 1950 AND 2100`, 시장 code 형식과 판매 기간을 검사합니다.
`(generation_id, market_code, model_year, edition_code)`를 UNIQUE로 둡니다.

### 5. `car_powertrain`

한 행은 `2.0 가솔린`, `1.6 터보 하이브리드`, `롱레인지 4WD`, `수소전기` 같은 조합입니다.
개별 장치 값은 component에 두고, 검색과 비교에 필요한 조합 전체 값만 여기에 둡니다.

| column | type·null | 의미 |
| --- | --- | --- |
| `id` | `BIGINT`, PK | 내부 식별자 |
| `public_id` | `UUID NOT NULL`, UNIQUE | 공개 식별자 |
| `model_year_id` | `BIGINT NOT NULL`, FK | 소속 모델 연식 |
| `code` | `VARCHAR(60) NOT NULL` | 연식 안의 안정 code |
| `name` | `VARCHAR(120) NOT NULL` | 제조사 표시 이름 |
| `propulsion_type` | `VARCHAR(12) NOT NULL` | `ICE`, `MHEV`, `HEV`, `PHEV`, `EREV`, `BEV`, `FCEV` |
| `primary_fuel_type` | `VARCHAR(16) NOT NULL` | `GASOLINE`, `DIESEL`, `LPG`, `ELECTRICITY`, `HYDROGEN`, `OTHER` |
| `secondary_fuel_type` | `VARCHAR(16) NULL` | PHEV·EREV의 두 번째 외부 에너지 |
| `drivetrain_type` | `VARCHAR(8) NOT NULL` | `FWD`, `RWD`, `AWD`, `4WD` |
| `transmission_type` | `VARCHAR(20) NULL` | `MANUAL`, `AUTOMATIC`, `DCT`, `CVT`, `REDUCTION_GEAR`, `OTHER` |
| `transmission_gears` | `SMALLINT NULL` | 변속 단수 |
| `system_max_power_kw` | `NUMERIC(8,2) NULL` | 동력계통 합산 최고 출력 |
| `system_max_power_ps` | `NUMERIC(8,2) NULL` | 공식 자료의 PS 합산 출력 |
| `system_max_torque_nm` | `NUMERIC(8,2) NULL` | 동력계통 합산 최대 토크 |
| `zero_to_hundred_seconds` | `NUMERIC(5,2) NULL` | 공식 0→100 km/h |
| `top_speed_kmh` | `SMALLINT NULL` | 공식 최고속도 |
| `max_ac_charge_kw` | `NUMERIC(7,2) NULL` | 최대 완속 충전 출력 |
| `ac_charge_connector` | `VARCHAR(40) NULL` | 공식 AC 충전 단자 규격 |
| `ac_charge_minutes` | `SMALLINT NULL` | 아래 SOC·출력 조건의 공식 AC 충전 시간 |
| `ac_charge_from_percent` | `SMALLINT NULL` | AC 충전 시작 SOC |
| `ac_charge_to_percent` | `SMALLINT NULL` | AC 충전 종료 SOC |
| `ac_charge_power_kw` | `NUMERIC(7,2) NULL` | AC 시간 측정에 사용한 충전기 출력 |
| `max_dc_charge_kw` | `NUMERIC(7,2) NULL` | 최대 급속 충전 출력 |
| `dc_charge_connector` | `VARCHAR(40) NULL` | 공식 DC 충전 단자 규격 |
| `fast_charge_minutes` | `SMALLINT NULL` | 아래 SOC 구간의 공식 충전 시간 |
| `fast_charge_from_percent` | `SMALLINT NULL` | 급속 충전 시작 SOC |
| `fast_charge_to_percent` | `SMALLINT NULL` | 급속 충전 종료 SOC |
| `fast_charge_power_kw` | `NUMERIC(7,2) NULL` | 급속 시간 측정에 사용한 충전기 출력 |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL` | 생성·수정 시각 |

`(model_year_id, code)`를 UNIQUE로 둡니다. AC·DC 대표 충전 시간은 각각 시간·시작 SOC·종료
SOC·충전기 출력을 한 묶음으로 입력하고 `0 <= from < to <= 100`과 양수를 검사합니다. 제조사가
공개한 대표 AC 조건 한 개와 대표 DC 조건 한 개만 저장하므로 조건별 행을 위한 별도 테이블은
만들지 않습니다. `max_*_charge_kw`는 차량이 받을 수 있는 최대 출력이고 `*_charge_power_kw`는
공식 시간 측정에 사용한 충전기 출력이므로 서로 대소 관계를 강제하지 않습니다. 충전 관련 값은
외부 충전이 가능한 `PHEV`, `EREV`, `BEV`에만 허용합니다. `PHEV`, `EREV`만 두 fuel을 요구하고
`BEV`는 `ELECTRICITY`, `FCEV`는 `HYDROGEN`을 primary로 요구합니다.

### 6. `car_powertrain_component`

한 powertrain에 하이브리드의 엔진·모터·배터리, AWD 전기차의 앞·뒤 모터처럼 여러 장치가
존재하므로 1:N으로 분리합니다.

| column | type·null | 의미 |
| --- | --- | --- |
| `id` | `BIGINT`, PK | 내부 식별자 |
| `powertrain_id` | `BIGINT NOT NULL`, FK | 소속 동력계통 |
| `component_type` | `VARCHAR(20) NOT NULL` | `ENGINE`, `MOTOR`, `BATTERY`, `FUEL_TANK`, `FUEL_CELL`, `HYDROGEN_TANK` |
| `component_role` | `VARCHAR(20) NOT NULL` | `PRIMARY`, `TRACTION`, `GENERATOR`, `STARTER_GENERATOR`, `RANGE_EXTENDER`, `ENERGY_STORAGE`, `FUEL_STORAGE` |
| `position` | `VARCHAR(12) NOT NULL DEFAULT 'NA'` | `FRONT`, `REAR`, `CENTER`, `INTEGRATED`, `NA` |
| `name` | `VARCHAR(120) NOT NULL` | 장치 표시 이름 |
| `fuel_type` | `VARCHAR(16) NULL` | 엔진·연료탱크의 연료 |
| `displacement_cc` | `INTEGER NULL` | 엔진 배기량 |
| `cylinder_count` | `SMALLINT NULL` | 실린더 수 |
| `aspiration_type` | `VARCHAR(20) NULL` | `NATURALLY_ASPIRATED`, `TURBO`, `SUPERCHARGED` |
| `max_power_kw` | `NUMERIC(8,2) NULL` | 장치 최고 출력 kW |
| `max_power_ps` | `NUMERIC(8,2) NULL` | 공식 장치 출력 PS |
| `max_power_rpm` | `INTEGER NULL` | 공식 최고 출력 발생 회전수 |
| `max_torque_nm` | `NUMERIC(8,2) NULL` | 장치 최대 토크 |
| `max_torque_kgf_m` | `NUMERIC(8,2) NULL` | 공식 자료의 kgf·m 최대 토크 |
| `max_torque_rpm_from`, `max_torque_rpm_to` | `INTEGER NULL` | 최대 토크 발생 회전수 또는 구간 |
| `capacity_kwh` | `NUMERIC(8,2) NULL` | 제조사가 gross 또는 nominal로 공개한 배터리 총 용량 |
| `usable_capacity_kwh` | `NUMERIC(8,2) NULL` | 제조사가 별도로 공개한 사용 가능 용량 |
| `capacity_ah` | `NUMERIC(8,2) NULL` | 배터리 전하량 |
| `voltage_v` | `NUMERIC(8,2) NULL` | 배터리 정격전압 |
| `capacity_l` | `NUMERIC(8,2) NULL` | 액체 연료·수소 탱크 부피 |
| `capacity_kg` | `NUMERIC(8,3) NULL` | 수소 저장 질량 |
| `pressure_bar` | `NUMERIC(8,2) NULL` | 수소 저장 압력 |
| `manufacturer_name` | `VARCHAR(120) NULL` | 배터리 pack 또는 일반 component 제조사 |
| `cell_manufacturer_name` | `VARCHAR(120) NULL` | 배터리 cell 제조사 |
| `production_country_code` | `CHAR(2) NULL` | component 생산 국가 |
| `cell_type` | `VARCHAR(40) NULL` | 배터리 원통형·각형·파우치형 등 |
| `chemistry` | `VARCHAR(40) NULL` | NCM·LFP 등 공개된 주요 화학계 |
| `sort_order` | `INTEGER NOT NULL DEFAULT 0` | 상세 표시 순서 |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL` | 생성·수정 시각 |

`(powertrain_id, component_type, component_role, position, name)`를 UNIQUE로 둡니다. 모든 수치는 양수이고
토크 회전수 구간은 `from <= to`, 생산 국가는 대문자 두 글자를 검사하며 `aspiration_type`은
`NATURALLY_ASPIRATED`, `TURBO`, `SUPERCHARGED`만 허용합니다.
`usable_capacity_kwh`는 배터리에만 허용하며 `capacity_kwh`가 있을 때 그 값 이하여야 합니다.
`ENGINE`은 `displacement_cc`, `BATTERY`는 `capacity_kwh`와 `capacity_ah` 중 하나 이상,
`FUEL_TANK`는 `capacity_l`,
`HYDROGEN_TANK`는 `capacity_kg`, `FUEL_CELL`은 `max_power_kw`를 필수로 하는 CHECK를 둡니다.
다른 종류의 전용 용량 칼럼이 채워지는 것도 거부합니다. role은 엔진의 `PRIMARY`·
`RANGE_EXTENDER`, 모터의 `TRACTION`·`GENERATOR`·`STARTER_GENERATOR`, 배터리의
`ENERGY_STORAGE`, 탱크의 `FUEL_STORAGE`, 연료전지의 `PRIMARY` 조합만 허용합니다.

동력 형식별 대표 자료를 입력할 때 사용하는 장치 조합은 다음과 같습니다. 이 조합은 한 행의
`CHECK`로 강제하지 않으며, 017 초기 데이터 작업에서 공식 자료 mapping을 검토합니다.

| propulsion | 필수 component | 외부 energy column |
| --- | --- | --- |
| `ICE`, `MHEV` | `ENGINE`, `FUEL_TANK`; MHEV는 `MOTOR`, `BATTERY`도 필수 | 액체 연료 하나 |
| `HEV` | `ENGINE`, `MOTOR`, `BATTERY`, `FUEL_TANK` | 액체 연료 하나 |
| `PHEV` | `ENGINE`, `MOTOR`, `BATTERY`, `FUEL_TANK` | 액체 연료 + `ELECTRICITY` |
| `EREV` | range-extender `ENGINE`, traction `MOTOR`, `BATTERY`, `FUEL_TANK` | 액체 연료 + `ELECTRICITY` |
| `BEV` | traction `MOTOR` 하나 이상, `BATTERY` | `ELECTRICITY` |
| `FCEV` | traction `MOTOR`, `BATTERY`, `FUEL_CELL`, `HYDROGEN_TANK` | `HYDROGEN` |

### 7. `car_vehicle_variant`

트림이 달라도 기술 제원이 같을 수 있고, 카니발처럼 인승·루프·휠에 따라 제원과 인증이
달라질 수 있습니다. 이 차이를 configuration에 반복하지 않고 기술 변형으로 분리합니다.

| column | type·null | 의미 |
| --- | --- | --- |
| `id` | `BIGINT`, PK | 내부 식별자 |
| `public_id` | `UUID NOT NULL`, UNIQUE | 공개 식별자 |
| `model_year_id` | `BIGINT NOT NULL`, FK | 소속 모델 연식 |
| `powertrain_id` | `BIGINT NOT NULL`, FK | 동력계통 |
| `code` | `VARCHAR(80) NOT NULL` | 연식 안의 기술 변형 code |
| `name` | `VARCHAR(160) NOT NULL` | `3.5 가솔린 9인승 18인치` 같은 표시명 |
| `body_type` | `VARCHAR(16) NOT NULL` | `SEDAN`, `HATCHBACK`, `WAGON`, `COUPE`, `CONVERTIBLE`, `SUV`, `MPV`, `PICKUP`, `VAN`, `OTHER` |
| `roof_type` | `VARCHAR(16) NOT NULL DEFAULT 'STANDARD'` | `STANDARD`, `HIGH_ROOF`, `OPEN`, `OTHER` |
| `seating_capacity` | `SMALLINT NOT NULL` | 승차 인원 |
| `door_count` | `SMALLINT NULL` | 문 수 |
| `length_mm`, `width_mm`, `height_mm` | `INTEGER NOT NULL` | 전장·전폭·전고 |
| `wheelbase_mm` | `INTEGER NOT NULL` | 축거 |
| `front_track_mm`, `rear_track_mm` | `INTEGER NULL` | 앞·뒤 윤거 |
| `ground_clearance_mm` | `INTEGER NULL` | 최저 지상고 |
| `cargo_capacity_l` | `NUMERIC(8,2) NULL` | 기본 좌석 상태 적재 용량 |
| `max_cargo_capacity_l` | `NUMERIC(8,2) NULL` | 공식 최대 적재 용량 |
| `curb_weight_kg` | `INTEGER NULL` | 대표 공차중량 |
| `gross_vehicle_weight_kg` | `INTEGER NULL` | 차량 총중량 |
| `max_braked_towing_capacity_kg` | `INTEGER NULL` | 제동 장치가 있는 피견인차의 최대 견인중량 |
| `max_unbraked_towing_capacity_kg` | `INTEGER NULL` | 제동 장치가 없는 피견인차의 최대 견인중량 |
| `max_roof_load_kg` | `INTEGER NULL` | 주행 중 허용되는 최대 루프 적재중량 |
| `minimum_turning_radius_m` | `NUMERIC(5,2) NULL` | 제조사가 공개한 최소 회전반경 |
| `wheel_size_inch` | `NUMERIC(4,1) NULL` | 기본 휠 지름 |
| `front_tire` | `VARCHAR(40) NULL` | 앞 타이어 규격 |
| `rear_tire` | `VARCHAR(40) NULL` | 뒤 타이어 규격 |
| `front_suspension`, `rear_suspension` | `VARCHAR(100) NULL` | 현가장치 |
| `steering_type` | `VARCHAR(100) NULL` | 조향장치 |
| `front_brake`, `rear_brake` | `VARCHAR(100) NULL` | 제동장치 |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL` | 생성·수정 시각 |

`(model_year_id, code)`를 UNIQUE로 둡니다. `car_powertrain(model_year_id, id)`에 composite
UNIQUE를 추가하고 `car_vehicle_variant(model_year_id, powertrain_id)`가 이를 참조하게 하여
동력계통이 같은 모델 연식에 속함을 DB가 보장합니다. 인원·치수·중량은 양수이고 최대 적재
용량은 기본 적재 용량 이상이어야 합니다. 두 견인중량이 모두 있으면 제동 견인중량이 비제동
견인중량 이상이어야 합니다.

### 8. `car_configuration`

한 행은 사용자가 실제 가격표에서 선택하는 트림입니다. 독립 trim 테이블을 만들지 않고
기술 변형 아래에 트림명과 판매 기간을 둡니다.

| column | type·null | 의미 |
| --- | --- | --- |
| `id` | `BIGINT`, PK | 내부 식별자 |
| `public_id` | `UUID NOT NULL`, UNIQUE | 공개 식별자 |
| `vehicle_variant_id` | `BIGINT NOT NULL`, FK | 기술 변형 |
| `code` | `VARCHAR(80) NOT NULL` | 변형 안의 안정 code |
| `trim_name` | `VARCHAR(120) NOT NULL` | 프레스티지·노블레스 같은 트림명 |
| `sales_channel` | `VARCHAR(16) NOT NULL DEFAULT 'PRIVATE'` | `PRIVATE`, `BUSINESS`, `RENTAL`, `DISABLED` |
| `sales_started_on` | `DATE NOT NULL` | 해당 트림·판매 채널의 판매 시작일 |
| `sales_ended_on` | `DATE NULL` | 해당 트림·판매 채널의 판매 종료일 exclusive |
| `display_order` | `INTEGER NOT NULL DEFAULT 0` | 가격표 표시 순서 |
| `is_published` | `BOOLEAN NOT NULL DEFAULT FALSE` | Carmunity 비회원 API 공개 승인 여부 |
| `standard_features` | `JSONB NOT NULL DEFAULT '[]'` | category·name·nullable description 배열 |
| `warranties` | `JSONB NOT NULL DEFAULT '[]'` | name·nullable years·distanceKm·note 배열 |
| `safety_ratings` | `JSONB NOT NULL DEFAULT '[]'` | organization·rating·nullable assessedOn·note 배열 |
| `media` | `JSONB NOT NULL DEFAULT '[]'` | 권리가 확인된 type·url·alt·sortOrder 배열 |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL` | 생성·수정 시각 |

`(vehicle_variant_id, sales_channel, code)`를 UNIQUE로 두고 기간·표시 순서를 검사합니다. 각
JSONB는 top-level array 형식 CHECK를 둡니다. 배열 원소의 상세 key·type과 URL 형식은 017의
공식 자료 mapping에서 검토합니다. JSONB의 값은 첫 API에서 filter·정렬·핵심 무결성에 쓰지
않습니다. 그 요구가 생기면 해당 항목만 관계형 테이블로 승격합니다.

configuration의 판매 기간은 소속 model year의 판매 기간 안에 있어야 합니다. 부모 행을 읽는
규칙은 단일 row CHECK로 흉내 내지 않으며, 017 초기 데이터 작업의 공식 자료 mapping에서 두
기간의 포함 관계를 검토합니다.

### 9. `car_configuration_price`

가격은 configuration에 덮어쓰지 않고 적용 시작일마다 새 행을 추가합니다. 종료일은 다음
가격 행의 `effective_from`으로 결정하므로 겹치는 기간과 별도 range extension이 필요 없습니다.

| column | type·null | 의미 |
| --- | --- | --- |
| `id` | `BIGINT`, PK | 내부 식별자 |
| `configuration_id` | `BIGINT NOT NULL`, FK | 판매 구성 |
| `effective_from` | `DATE NOT NULL` | 가격 적용 시작일 |
| `currency_code` | `CHAR(3) NOT NULL DEFAULT 'KRW'` | ISO 4217 통화 |
| `price_before_tax_benefit` | `BIGINT NOT NULL` | 세제 혜택 전 기본 가격 |
| `price_after_tax_benefit` | `BIGINT NULL` | 공식 자료가 제공한 세제 혜택 후 가격 |
| `source_price_label` | `VARCHAR(120) NULL` | 원문 가격 기준 문구 |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL` | 생성·수정 시각 |

`(configuration_id, effective_from)`를 UNIQUE로 둡니다. 가격은 0 이상이고 혜택 후 가격이
있으면 혜택 전 가격 이하여야 합니다. 기준일 가격은 `effective_from <= :asOf` 중 가장 최근 한
행입니다. `effective_from`이 configuration 판매 기간 밖인지 017 초기 데이터 mapping에서
검토합니다.

### 10. `car_configuration_option`

선택 품목은 configuration마다 여러 개이고 가격과 선택 가능 상태가 바뀔 수 있으므로 JSONB가
아닌 관계형 자식으로 저장합니다. 가격과 동일하게 option code별 적용 시작 행을 추가하고
종료 range는 만들지 않습니다.

| column | type·null | 의미 |
| --- | --- | --- |
| `id` | `BIGINT`, PK | 내부 식별자 |
| `configuration_id` | `BIGINT NOT NULL`, FK | 선택 가능한 판매 구성 |
| `code` | `VARCHAR(80) NOT NULL` | configuration 안의 안정 code |
| `name` | `VARCHAR(160) NOT NULL` | 선택 품목·패키지·유상 색상명 |
| `option_type` | `VARCHAR(20) NOT NULL` | `SINGLE`, `PACKAGE`, `EXTERIOR_COLOR`, `INTERIOR_COLOR` |
| `category` | `VARCHAR(60) NULL` | 안전·편의·외장 같은 표시 분류 |
| `is_selectable` | `BOOLEAN NOT NULL` | 해당 시작일부터 선택 가능한지 여부 |
| `price_before_tax_benefit` | `BIGINT NULL` | 선택 가능할 때의 세제 혜택 전 추가 금액 |
| `price_after_tax_benefit` | `BIGINT NULL` | 공식 자료가 제공한 혜택 후 추가 금액 |
| `currency_code` | `CHAR(3) NOT NULL DEFAULT 'KRW'` | 통화 |
| `included_items` | `JSONB NOT NULL DEFAULT '[]'` | package에 포함된 표시용 품목명 배열 |
| `effective_from` | `DATE NOT NULL` | 가격·선택 상태 적용 시작일 |
| `display_order` | `INTEGER NOT NULL DEFAULT 0` | 표시 순서 |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL` | 생성·수정 시각 |

`(configuration_id, code, effective_from)`을 UNIQUE로 둡니다. `is_selectable=true`이면 혜택 전
가격을 필수로 하고 0 이상인지 검사하며, false이면 두 가격을 null로 강제합니다. 혜택 후 가격은
혜택 전 가격 이하여야 하고 `included_items`는 JSON array여야 합니다. 기준일에는
`effective_from <= :asOf`인 code별 가장 최근 한 행만 선택하고 `is_selectable=true`인 항목만
반환하므로 같은 option code가 중복되지 않습니다. 첫 범위는 옵션 간 필수·배타 조합을 계산하지
않고 공식적으로 선택 가능한 옵션 목록만 제공합니다. `effective_from`이 configuration 판매
기간 밖인 옵션 행도 017 초기 데이터 mapping에서 검토합니다.

### 11. `car_certification`

같은 차량 변형도 휠, 빌트인 캠, 온도 조건에 따라 인증값이 여러 행이므로 1:N으로 분리합니다.

| column | type·null | 의미 |
| --- | --- | --- |
| `id` | `BIGINT`, PK | 내부 식별자 |
| `vehicle_variant_id` | `BIGINT NOT NULL`, FK | 인증 대상 기술 변형 |
| `condition_code` | `VARCHAR(100) NOT NULL` | 변형 안의 안정 조건 code |
| `condition_name` | `VARCHAR(180) NOT NULL` | 사람이 읽는 측정 조건 |
| `certification_type` | `VARCHAR(16) NOT NULL` | `LIQUID_FUEL`, `ELECTRIC`, `HYDROGEN` |
| `certification_standard` | `VARCHAR(32) NOT NULL` | `KOREA`, `WLTP`, `EPA`처럼 수치에 적용된 인증 기준 code |
| `temperature_condition` | `VARCHAR(12) NULL` | 전기차 주행거리의 `NORMAL`, `LOW` |
| `wheel_size_inch` | `NUMERIC(4,1) NULL` | 인증 휠 크기 |
| `tire_specification` | `VARCHAR(80) NULL` | 인증 타이어 규격 |
| `built_in_camera` | `BOOLEAN NOT NULL DEFAULT FALSE` | 빌트인 캠 조건 |
| `curb_weight_kg` | `INTEGER NULL` | 인증 공차중량 |
| `efficiency_grade` | `SMALLINT NULL` | 에너지 소비효율 등급 |
| `city_efficiency` | `NUMERIC(8,3) NULL` | 도심 효율 |
| `highway_efficiency` | `NUMERIC(8,3) NULL` | 고속도로 효율 |
| `combined_efficiency` | `NUMERIC(8,3) NULL` | 복합 효율; 저온 주행거리만 공개된 행은 null |
| `efficiency_unit` | `VARCHAR(16) NOT NULL` | `KM_PER_L`, `KM_PER_KWH`, `KM_PER_KG` |
| `city_range_km` | `NUMERIC(8,2) NULL` | 도심 인증 주행거리 |
| `highway_range_km` | `NUMERIC(8,2) NULL` | 고속도로 인증 주행거리 |
| `combined_range_km` | `NUMERIC(8,2) NULL` | 복합 인증 주행거리 |
| `co2_g_per_km` | `NUMERIC(8,2) NULL` | 복합 CO₂ 배출량 |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL` | 생성·수정 시각 |

`(vehicle_variant_id, condition_code)`를 UNIQUE로 둡니다. `certification_standard`는 비어 있지
않은 source-backed code로 입력하며 현재 알려진 값만으로 enum을 닫지 않습니다. 수치는 0 이상이고
값이 있는 효율은 양수입니다. 인증 종류와 단위를 각각 `LIQUID_FUEL→KM_PER_L`, `ELECTRIC→KM_PER_KWH`,
`HYDROGEN→KM_PER_KG`로 결속합니다. 액체 연료·수소와 `ELECTRIC/NORMAL`은 복합 효율을,
`ELECTRIC`, `HYDROGEN`은 복합 주행거리를 필수로 합니다. `ELECTRIC/LOW`는 공식 자료가 저온
주행거리만 공개하는 경우 효율을 null로 둘 수 있고, 액체 연료는 `co2_g_per_km`를 필수로 합니다.
인증 조건의 휠·타이어·공차중량이 연결된 variant 제원과 다르면 그 차이가 실제 측정 조건인지
원문 mapping으로 확인합니다.

## 선택 당시 FK 삭제 정책

| 부모 → 자식 | 삭제 정책 | 이유 |
| --- | --- | --- |
| brand → model → generation → model_year | `RESTRICT` | 과거 차량을 cascade로 잃지 않음 |
| model_year → powertrain·vehicle_variant | `RESTRICT` | 연식 상세 graph 보호 |
| powertrain → component·vehicle_variant | `RESTRICT` | 판매·인증 중인 동력계통 보호 |
| vehicle_variant → configuration·certification | `RESTRICT` | 기술 제원과 판매 구성 보존 |
| configuration → price·option | `CASCADE` | 아직 공개되지 않은 잘못된 fixture 삭제 편의; production 공개 후에는 configuration 자체를 삭제하지 않음 |

## 선택 당시 runtime DB 권한

현재 `afterMigrate__grant_runtime_privileges.sql`은 모든 application table과 sequence에 generic
권한을 부여한 뒤 catalog에 다음 revoke를 적용해 runtime을 읽기 전용으로 제한합니다.

- 위 11개 table에는 `SELECT`만 남기고 `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, `REFERENCES`,
  `TRIGGER`를 `carmunity_runtime`에서 revoke합니다.
- identity가 만드는 아래 11개 sequence의 `USAGE`, `SELECT`, `UPDATE`를 모두 revoke합니다.

```text
car_brand_id_seq
car_model_id_seq
car_generation_id_seq
car_model_year_id_seq
car_powertrain_id_seq
car_powertrain_component_id_seq
car_vehicle_variant_id_seq
car_configuration_id_seq
car_configuration_price_id_seq
car_configuration_option_id_seq
car_certification_id_seq
```

기존 account/auth table과 sequence의 권한은 바꾸지 않습니다. migration process integration test는
runtime 자격증명으로 11개 table 각각의 `SELECT` 성공, `INSERT`·`UPDATE`·`DELETE`·`TRUNCATE`
실패와 11개 sequence 각각의 `nextval` 실패를 검사합니다. migrator는 schema와 후속 검토된 data
migration을 적용할 권한을 유지합니다.

## 선택 당시 인덱스 계획

PK와 UNIQUE가 만드는 index 외에 첫 목록·상세 query에 필요한 것만 추가합니다.

| index | column·조건 | 사용하는 조회 |
| --- | --- | --- |
| `idx_car_powertrain_model_year_filter` | `model_year_id, propulsion_type, primary_fuel_type` | 동력·연료 filter |
| `idx_car_variant_model_year_body` | `model_year_id, body_type, powertrain_id` | 차체·동력 filter와 상세 join |
| `idx_car_configuration_variant_sales` | `vehicle_variant_id, sales_channel, sales_started_on, sales_ended_on WHERE is_published` | 기준일 판매 구성 |
| `idx_car_option_configuration` | `configuration_id, display_order` | 상세 옵션 |

model year 선택은 `(generation_id, market_code, model_year, edition_code)` UNIQUE, 현재 가격은
`(configuration_id, effective_from)` UNIQUE, 인증 상세는
`(vehicle_variant_id, condition_code)` UNIQUE가 만든 B-tree의 선두 칼럼과 역방향 scan을 먼저
사용합니다. 같은 칼럼을 다시 시작하는 전용 index는 실제 실행 계획이 부족함을 증명할 때만
추가합니다.

실제 fixture query의 `EXPLAIN (ANALYZE, BUFFERS)`에서 사용되지 않는 후보 index는 추가하지
않습니다. 첫 데이터 규모에서 cache와 검색엔진은 사용하지 않습니다.

## 선택한 migration 경계

11개 테이블을 FK 선행 순서에 따라 다음 다섯 migration으로 구현하기로 선택했습니다.

| migration | 함께 생성하는 table | 이 경계에서 함께 실패·재시도하는 이유 |
| --- | --- | --- |
| `V13__create_car_product_hierarchy.sql` | `car_brand`, `car_model`, `car_generation`, `car_model_year` | 뒤의 모든 카탈로그 행이 의존하는 제품 식별 계층이며 일부 계층만으로는 조회 입력이 성립하지 않음 |
| `V14__create_car_powertrain_schema.sql` | `car_powertrain`, `car_powertrain_component` | 동력계통 본체와 반복 장치가 하나의 기술 사양을 구성함 |
| `V15__create_car_vehicle_offering.sql` | `car_vehicle_variant`, `car_configuration` | 기술 변형과 공개 가능한 판매 구성이 함께 있어야 현재 조회 대상이 됨 |
| `V16__create_car_pricing_and_option_history.sql` | `car_configuration_price`, `car_configuration_option` | 판매 구성에 종속된 두 기준일 이력을 같은 상업 정보 단계에서 복구함 |
| `V17__create_car_certification.sql` | `car_certification` | 판매 구성과 독립적으로 보완·재시도할 수 있는 조건별 측정 정보임 |

기능 전체를 V13 하나에 두면 뒤쪽 DDL 오류도 11개 테이블 전체를 rollback·재적용하게 되고,
서로 다른 다섯 책임이 한 검토 단위에 섞입니다. 반대로 테이블마다 V13~V23으로 나누면
application이 소비하지 않는 부모만의 중간 checkpoint가 생기고 version과 검토 단위만
늘어납니다. FK 선행 순서는 지키되 내부 테이블이 함께 있어야 의미가 완성되는 다섯 단위를
각각 PostgreSQL transaction과 Flyway 실패 재시작 경계로 선택했습니다.

## 미구현 Web API 목표 계약

이 절은 016에서 앞으로 구현할 Web adapter의 목표 계약입니다. 현재 구현 상태는
[016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md)가 소유합니다.

### 주소와 입력

| 요청 | 선택 입력 | 기본값·제약 |
| --- | --- | --- |
| `GET /api/v1/car/brands` | 없음 | `Asia/Seoul` 현재 날짜에 모델 연식과 공개 configuration이 모두 판매 중인 브랜드만 표시 순서·이름 순 |
| `GET /api/v1/car/models` | `q`, `brandId`, `bodyType`, `propulsionType`, `fuelType`, `minPrice`, `maxPrice`, `salesStatus`, `asOf`, `sort`, `page`, `size` | `salesStatus=ON_SALE`, `asOf=Asia/Seoul 현재 날짜`, `sort=NAME_ASC`, `page=0`, `size=20`, 최대 100 |
| `GET /api/v1/car/models/{carModelId}` | `modelYearId`, `asOf` | 연식 생략 시 공개 configuration이 있는 판매 중 최신 연식, 없으면 가장 최근 연식 |

- `minPrice`, `maxPrice`는 0 이상 int64이고 `minPrice <= maxPrice`입니다.
- `asOf`는 ISO-8601 날짜, `page >= 0`, `1 <= size <= 100`입니다.
- `q`는 Unicode NFKC, 앞뒤 공백 제거와 연속 공백 축약 뒤 1~100 code point입니다.
- `%`, `_`, `\\`는 wildcard가 아니라 literal 문자로 escape합니다.
- filter 여러 개는 같은 공개 판매 구성에서 모두 만족해야 합니다.
- 정렬은 `NAME_ASC`, `PRICE_ASC`, `PRICE_DESC`, `RELEASED_DESC`이며 마지막 동률은 모델
  `public_id`로 고정합니다.
- 공개 enum은 `BodyType`, `PropulsionType`, `FuelType`, `SalesStatus`의 application 계약을
  그대로 사용합니다.

### 공통 응답 표현

- 금액은 정수 원 단위이고 `currencyCode=KRW`를 함께 반환합니다.
- 종료일은 exclusive인 `salesEndedOn`으로 반환합니다.
- 내부 DB id는 공개하지 않습니다.
- 배열은 값이 없으면 `[]`, 선택 단일값은 없으면 `null`입니다.
- 날짜는 `YYYY-MM-DD`, UUID는 표준 문자열입니다.
- page 응답은 `items`, `page`, `size`, `totalElements`, `totalPages`를 가집니다.

### 응답별 필수 정보

| 응답 | 필수 정보 |
| --- | --- |
| 브랜드 목록 | `brandId`, `name`, nullable `nameEn`, `countryCode`, 기준일 판매 중인 `onSaleModelCount` |
| 모델 목록 | 모델·브랜드 공개 id와 이름, 대표 연식 id·연식·edition, 차체·동력·연료 집합, 판매 상태·출시일, 기준일 시작 가격·가격 기준·통화 |
| 모델 상세 | 모델·브랜드, 세대, 연식, 선택 연식, 동력계통과 component, 기술 변형과 인증값, 판매 구성, 기준일 가격과 선택 가능한 옵션 |

표시 가격은 configuration별 `effective_from <= asOf`인 가장 최근 가격을 사용합니다. 세제 혜택
후 가격이 있으면 그 값을, 없으면 혜택 전 가격을 사용합니다. 가격이 없는 구성은 가격 filter와
최솟값에서 제외하며 모든 구성에 가격이 없으면 `startPrice=null`입니다.

대표 연식은 filter를 만족한 판매 구성의 연식 중 `ON_SALE` → `UPCOMING` →
`DISCONTINUED`, `releasedOn DESC NULLS LAST`, `modelYear DESC`, `salesStartedOn DESC`,
`representativeModelYearId ASC` 순으로 하나를 선택합니다. 특정 `salesStatus` 요청은 먼저 해당
상태만 남깁니다.

상세 요청에서 `modelYearId`를 지정하면 공개된 단종 연식도 반환합니다. 생략하면 목록과 같은
우선순위를 filter 없는 공개 configuration에 적용합니다. 옵션은
`(configuration_id, code)`별 기준일 이전 최신 행 가운데 `is_selectable=true`인 항목만
반환합니다.

### 오류·Security·완료 조건

기존 RFC 9457 `ProblemDetail` 형식을 사용합니다. malformed UUID·enum·날짜와 범위 오류는
400, 없거나 공개 판매 구성이 없는 모델·연식은 404입니다. 세 GET만 비회원에게 허용하며 같은
주소의 쓰기 method와 기존 보호 API의 401·403·405 계약은 유지합니다. 예상하지 못한 schema
모순과 서버 오류는 500입니다.

Web API 단계는 Controller·request·response DTO, validation, Security allow-list와 OpenAPI를
구현하고 다음을 자동 검증하면 완료됩니다.

- 검색과 단일·조합 filter가 같은 판매 구성 기준으로 동작합니다.
- 대표 연식·가격·정렬·page 동률 순서가 유일하고 안정적입니다.
- 상세 응답이 component·variant·certification·configuration·price·option을 빠뜨리지 않습니다.
- 내부 id가 노출되지 않고 OpenAPI와 실제 JSON nullability가 일치합니다.
- 목록·상세 SQL 수가 결과 행 수에 비례하지 않습니다.

## 선택 당시 조회 경로

### 브랜드 목록

기준일에 `is_published=true`인 판매 구성이 하나 이상 존재하는 브랜드만 조회하고, 같은 조건을
만족하는 모델 수를 집계합니다. 제조사가 자료를 공개했는지와 Carmunity API 노출 여부를
혼동하지 않으며 브랜드 목록에서 내부 id는 반환하지 않습니다.

### 모델 목록

```text
brand → model → generation → model_year
      → vehicle_variant → powertrain → configuration
      → 기준일 이전의 가장 최근 configuration_price
```

- 검색어는 `car_model.name_normalized`에 literal 부분 일치합니다.
- 차체·동력·연료 filter는 같은 `car_vehicle_variant`와 그 `car_powertrain`에서 만족해야 합니다.
- 가격 filter는 같은 configuration의 기준일 현재 가격을 사용합니다.
- 모델 하나에 여러 연식·변형·트림이 있어도 모델은 한 행만 반환합니다.
- filter를 모두 만족한 configuration 집합에서 모델 연식 상태를 `ON_SALE` → `UPCOMING` →
  `DISCONTINUED` 순으로 우선하고, 같은 상태에서는 `released_on DESC NULLS LAST` →
  `model_year DESC` → `sales_started_on DESC` → `public_id ASC`로 대표 연식을 하나 선택합니다.
  특정 `salesStatus` 요청은 그 상태의 후보만 남긴 뒤 같은 동률 순서를 적용합니다.
- 모델의 `salesStatus`, 출시일과 연식 표시는 선택된 대표 연식의 값입니다. `ALL`이어도 판매 중인
  연식이 있으면 예정·단종 연식보다 판매 중 연식을 대표로 사용합니다.
- `startPrice`는 조건을 만족하는 공개 configuration 현재 가격의 최솟값입니다.
- 세제 혜택 후 가격이 있으면 표시 가격으로 사용하고 없으면 혜택 전 가격을 사용하며 basis를
  함께 반환합니다.

### 모델 상세

모델·세대·연식을 먼저 읽고, 선택 연식의 powertrain/component, variant/certification,
configuration/price/option을 각각 묶음 조회합니다. 결과 수만큼 SQL이 늘어나는 N+1을 허용하지
않습니다. 한 read-only transaction에서 읽어 연식·가격이 서로 다른 시점의 값으로 섞이지 않게
합니다. 상세에서 `modelYearId`를 생략하면 위 상태·동률 순서를 filter 없는 공개 configuration에
적용합니다.

## 선택 당시 과거 정보와 정정 처리

- 연식과 configuration은 판매 시작·종료일을 가지며 단종 뒤에도 행을 유지합니다.
- 가격은 `effective_from`이 다른 행을 추가해 이력을 보존합니다.
- 옵션 선택 기간은 option 행에 보존하고, 공식 자료의 확인일은 후속 초기 데이터 mapping에
  기록합니다.
- 잘못 입력한 값의 정정 이력 전체를 제품 기능으로 복원해야 한다는 요구는 없습니다. 정정은
  새 Flyway data migration에서 UPDATE하고 Git/Flyway 변경 이력으로 감사합니다.
- 모든 사실에 별도 기록 시간축과 tombstone을 두는 bitemporal 구조는 만들지 않습니다. 향후
  관리자 catalog writer와 법적 감사 요구가 생기면 그 writer 범위에서 다시 설계합니다.

## 재검토 조건

- 두 번째 국가·통화·시장 출시가 실제 범위에 들어오면 market과 currency 기준정보를 분리합니다.
- 옵션 간 필수·배타 조합을 견적 기능에서 계산해야 하면 option identity와 compatibility rule을
  추가합니다.
- 보증·안전도·media를 검색하거나 독립 갱신해야 하면 해당 JSONB만 관계형 테이블로
  승격합니다.
- 상용·특장차가 범위에 들어오면 축 수, 화물 적재중량과 body equipment를 typed column으로
  추가합니다. 일반 승용·SUV의 견인중량은 현재 variant에 포함되어 있습니다.
- runtime 관리자 입력과 모든 정정 시점 복원이 제품 요구가 되면 writer 권한·감사·version
  정책을 별도 설계합니다.
- 판매 구성 없이 브랜드·모델 소개만 먼저 공개해야 하면 configuration에서 파생하는 공개
  규칙을 다시 열고, 필요한 상위 단위에만 별도 공개 상태를 둡니다.
- 세대 자체의 공식 판매 기간을 API에서 제공해야 하면 model year 기간과 다른 독립 원문이
  있는지 확인한 뒤 generation 기간 칼럼을 다시 검토합니다.
- 실제 query plan이 현재 index로 목표를 충족하지 못할 때만 trigram, cache 또는 search read
  model을 검토합니다.

## 관련 문서

- **현재 업무 개념**: [130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md)
- **현재 물리 구조와 조회 경로**: [110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)
- **작업 상태·API·개발 계획**: [016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md)
- **후속 실제 데이터**: [017 자동차 카탈로그 초기 데이터 구축](017-car-catalog-initial-data.md)
- **제품 범위**: [200 제품 기능 범위](../../../product/200-feature-scope.md)
- **현재 코드 구조**: [210 모듈 지도](../../../project/210-module-map.md)
- **DB 기준**: [230 Spring Data JPA](../../../tech-stack/230-spring-data-jpa.md),
  [240 Flyway](../../../tech-stack/240-flyway.md),
  [250 PostgreSQL](../../../tech-stack/250-postgresql.md)
