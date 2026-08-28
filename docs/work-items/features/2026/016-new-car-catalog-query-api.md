# 신차 카탈로그 schema·조회 API

## 상태

In Progress

## 현재 위치와 다음 작업

catalog 공개 enum과 모듈 경계, Flyway V13~V17의 11개 테이블·129개 named constraint·4개 조회
index, 대표 fixture와 runtime 실제 statement 기반 SELECT-only 권한
검증까지 구현했습니다. 아직 조회 persistence, Web API와 실제
production 데이터는 없습니다.

현재 작업은 schema를 사용하는 조회 persistence와 application 규칙을 구현하는 것입니다. 그
다음에 Web API, Security와 OpenAPI를 구현합니다. 실제 브랜드 전체 데이터는 기능 구현을
검증한 뒤 [017 자동차 카탈로그 초기 데이터 구축](017-car-catalog-initial-data.md)에서 적재합니다.

## 달성할 결과

로그인하지 않은 사용자가 자동차 브랜드, 모델 목록과 모델 상세를 조회할 수 있는 관계형
schema와 읽기 전용 API를 구현합니다. 현재 판매 차량뿐 아니라 공개된 과거 세대·연식을 같은
구조에서 조회하며 다음 정보를 제공합니다.

- 브랜드, 모델, 세대와 모델 연식
- 차체, 인승, 주요 치수·중량·휠·타이어
- 내연기관·하이브리드·전기·수소전기 동력계통과 구성 장치
- 실제 판매 트림, 현재·과거 기본 가격과 유상 선택 품목
- 측정 조건별 연비·전비·수소 효율, CO₂와 인증 주행거리
- 기본 품목, 외장·내장 색상, 보증, 안전도와 권리가 확인된 media

첫 구현은 실제 production data 대신 대표 fixture로 schema와 조회를 검증합니다.

## 전체 개발 흐름

| 단계 | 만드는 결과 | 현재 상태 | 다음 단계 조건 |
| --- | --- | --- | --- |
| 1. 기능 범위 | 공개 브랜드·모델·상세 조회와 filter | 완료 | 아래 포함·제외 범위가 유지됨 |
| 2. 실제 자료 조사 | ICE/HEV·MPV·BEV·FCEV 정보 분류 | 완료 | 공식 자료가 요구하는 관계와 칼럼을 설명함 |
| 3. DB 설계 | 정확한 11개 테이블, 칼럼, FK·UNIQUE·CHECK·index | 완료 | 상세 설계가 독립 검증을 통과함 |
| 4. schema 개발 | Flyway migration과 실제 PostgreSQL test | **완료** | V13~V17·129개 제약·4개 index·7개 동력 graph 공개 전 검증·대표 fixture·실제 권한 검증 통과 |
| 5. 조회 개발 | 목록·상세 persistence와 application 규칙 | **다음 작업** | filter·가격·과거 조회와 SQL 상한 통과 |
| 6. 공개 API 개발 | Controller·Security·OpenAPI | 후속 | 비회원 세 GET과 오류 계약 통과 |
| 7. 전체 검증 | `check`, 영구 문서, 독립 구현 검증 | 후속 | blocking finding 0건 |
| 8. 실제 데이터 | 공개 범위 결정, 공식 자료 mapping, data migration | 후속 017 | 선택 범위의 누락 0건 |
| 9. Git·원격·배포 | commit, 별도 push와 CI·Sonar, 별도 staging 적용 | 조건부 | 각 단계의 명시 권한과 gate 충족 |

## 포함 범위

- 대한민국 시장의 일반 개인 구매 차량을 우선하는 schema
- 브랜드→모델→세대→연식→기술 변형→판매 구성 관계
- ICE, MHEV, HEV, PHEV, EREV, BEV, FCEV 동력 형식
- 엔진, 모터, 배터리, 액체 연료탱크, 연료전지와 수소탱크
- 차체·루프·인승·치수·중량·적재·휠·타이어·섀시 주요 제원
- 트림의 판매 기간, 적용 시작일별 기본 가격과 유상 옵션
- 휠·카메라·온도 조건별 연비·전비·수소 효율·CO₂·주행거리
- 공개 여부와 날짜로 계산하는 예정·판매 중·단종 상태
- 비회원 브랜드 목록, 모델 목록, 모델 상세 GET
- 검색어·브랜드·차체·동력 형식·연료·가격·판매 상태·기준일·정렬·page filter
- 대표 K5형 ICE/HEV, 카니발형 MPV, EV6형 BEV, 넥쏘형 FCEV test fixture

## 제외 범위

- production 전체 차량 데이터와 자료 입력 도구
- 관리자 등록·수정·삭제 API와 crawler
- 다국가·다통화 판매, 재고·대리점·구매·견적·보조금 계산
- VIN 단위 차량, 소유 차량과 중고차 매물
- 옵션 간 필수·배타 조합을 계산하는 견적 규칙
- 보증·안전도·media 검색
- 모든 사실의 bitemporal 정정 이력과 field별 source assertion
- 실제 병목 근거가 없는 cache·검색엔진

## DB 계약

상세 칼럼과 제약은 [신차 카탈로그 조회 기능 DB 설계](016-new-car-catalog-query-api-design.md)가
소유합니다. 구현 대상은 그 설계에 정의된 자동차 제품 구조 11개 테이블입니다. 기존 7개
초안의 핵심 분류를 유지하면서 모델 연식, 기술 변형, 가격 이력과 선택 품목을 보완했습니다.
공식 자료와 DB 값의 대응은 017 초기 데이터 작업의
version-controlled mapping에서 관리하며 runtime schema에 source 테이블이나 FK를 두지
않습니다. fact마다 identity/version/source 테이블을 추가하는 157개 구조는 구현 대상이 아닙니다.

## 공개 API 계약

### 주소와 입력

| 요청 | 선택 입력 | 기본값·제약 |
| --- | --- | --- |
| `GET /api/v1/car/brands` | 없음 | 공개 configuration이 있는 브랜드만 표시 순서·이름 순 |
| `GET /api/v1/car/models` | `q`, `brandId`, `bodyType`, `propulsionType`, `fuelType`, `minPrice`, `maxPrice`, `salesStatus`, `asOf`, `sort`, `page`, `size` | `salesStatus=ON_SALE`, `asOf=Asia/Seoul 현재 날짜`, `sort=NAME_ASC`, `page=0`, `size=20`, 최대 100 |
| `GET /api/v1/car/models/{carModelId}` | `modelYearId`, `asOf` | 연식 생략 시 공개 configuration이 있는 판매 중 최신 연식, 없으면 가장 최근 연식 |

공통 입력 규칙은 다음과 같습니다.

- `minPrice`, `maxPrice`는 0 이상 int64이고 `minPrice <= maxPrice`입니다.
- `asOf`는 ISO-8601 날짜입니다.
- `page >= 0`, `1 <= size <= 100`입니다.
- `q`는 Unicode NFKC, 앞뒤 공백 제거, 연속 공백 축약 뒤 1~100 code point입니다.
- `%`, `_`, `\`는 검색 wildcard가 아니라 literal 문자로 escape합니다.
- filter 여러 개는 같은 공개 판매 구성에서 모두 만족해야 합니다.
- 정렬은 `NAME_ASC`, `PRICE_ASC`, `PRICE_DESC`, `RELEASED_DESC`이며 마지막 동률은 모델
  `public_id`로 고정합니다.

공개 enum은 다음과 같습니다.

- `BodyType`: `SEDAN`, `HATCHBACK`, `WAGON`, `COUPE`, `CONVERTIBLE`, `SUV`, `MPV`,
  `PICKUP`, `VAN`, `OTHER`
- `PropulsionType`: `ICE`, `MHEV`, `HEV`, `PHEV`, `EREV`, `BEV`, `FCEV`
- `FuelType`: `GASOLINE`, `DIESEL`, `LPG`, `ELECTRICITY`, `HYDROGEN`, `OTHER`
- `SalesStatus`: `UPCOMING`, `ON_SALE`, `DISCONTINUED`, `ALL`

### 공통 표현

- 금액은 정수 원 단위이고 `currencyCode=KRW`를 함께 반환합니다.
- 종료일은 exclusive인 `salesEndedOn`으로 반환합니다.
- 내부 DB id는 공개 응답에 포함하지 않습니다.
- 배열은 항목이 없으면 `[]`, 선택 단일값은 없으면 `null`입니다.
- 날짜는 `YYYY-MM-DD`, UUID는 표준 문자열입니다.
- page 응답은 `items`, `page`, `size`, `totalElements`, `totalPages`를 가집니다.

### `GET /api/v1/car/brands`

200 응답의 각 항목은 다음 값을 가집니다.

| field | null | 의미 |
| --- | --- | --- |
| `brandId` | 불가 | 브랜드 공개 UUID |
| `name` | 불가 | 국내 표시 이름 |
| `nameEn` | 가능 | 영문 이름 |
| `countryCode` | 불가 | 브랜드 국가 code |
| `onSaleModelCount` | 불가 | 공개 configuration이 있고 기준일 판매 중인 모델 수 |

### `GET /api/v1/car/models`

각 page item은 다음 값을 가집니다.

| field | null | 의미 |
| --- | --- | --- |
| `modelId` | 불가 | 모델 공개 UUID |
| `brandId`, `brandName` | 불가 | 브랜드 식별자와 이름 |
| `name`, `nameEn` | `nameEn`만 가능 | 모델 이름 |
| `representativeModelYearId` | 불가 | 아래 우선순위로 선택한 대표 연식 UUID |
| `modelYear`, `editionName` | 불가 | 대표 연식과 표시명 |
| `bodyTypes` | 불가 | 조건을 만족한 차체 종류 집합 |
| `propulsionTypes` | 불가 | 조건을 만족한 동력 형식 집합 |
| `fuelTypes` | 불가 | primary·secondary 외부 에너지 집합 |
| `salesStatus` | 불가 | 기준일 상태 |
| `releasedOn` | 가능 | 대표 연식 출시일 |
| `startPrice` | 가능 | 공개 판매 구성 현재 표시 가격 최솟값 |
| `priceBasis` | 가능 | `AFTER_TAX_BENEFIT` 또는 `BEFORE_TAX_BENEFIT` |
| `currencyCode` | `startPrice`가 null이면 가능 | `KRW` |

표시 가격은 configuration별로 `effective_from <= asOf`인 가장 최근 가격을 찾고, 세제 혜택 후
가격이 있으면 그 값을, 없으면 세제 혜택 전 가격을 사용합니다. 가격이 없는 구성은 가격
filter와 최솟값에서 제외하고, 모델의 모든 구성에 가격이 없으면 `startPrice=null`입니다.

대표 연식은 모든 filter를 만족한 configuration이 속한 모델 연식 중 다음 순서로 한 개를
선택합니다.

1. 상태는 `ON_SALE` → `UPCOMING` → `DISCONTINUED` 순입니다.
2. 같은 상태에서는 `releasedOn DESC NULLS LAST`입니다.
3. 출시일도 같거나 모두 null이면 `modelYear DESC`, `salesStartedOn DESC`,
   `representativeModelYearId ASC` 순입니다.

`salesStatus`가 `ON_SALE`, `UPCOMING`, `DISCONTINUED`이면 먼저 해당 상태만 남깁니다. `ALL`이면
위 세 상태 전체에서 우선순위를 적용합니다. 모델 item의 `salesStatus`, `releasedOn`,
`modelYear`, `editionName`은 대표 연식의 값입니다. 따라서 판매 중 연식과 예정 연식이 함께
있을 때 `ALL`도 판매 중 연식을 대표로 반환합니다.

### `GET /api/v1/car/models/{carModelId}`

상세 응답은 다음 묶음으로 구성합니다.

| object | 주요 field | DB 테이블 |
| --- | --- | --- |
| `model` | 모델·브랜드 id/name, summary | brand, model |
| `generations[]` | 세대 id/name/changeType | generation |
| `modelYears[]` | 연식 id/year/edition, 출시·판매 기간, 상태 | model_year |
| `selectedModelYear` | 선택 연식의 위 값 | model_year |
| `powertrains[]` | 동력 형식, 연료, 구동, 변속, 합산 출력·토크, 충전 | powertrain |
| `powertrains[].components[]` | 엔진·모터·배터리·연료전지·탱크의 용량·출력·토크·회전수와 공개된 배터리 제조·cell 정보 | component |
| `variants[]` | 차체·루프·인승·치수·중량·적재·휠·타이어·섀시 | vehicle_variant |
| `variants[].certifications[]` | 조건, 효율·단위, CO₂와 주행거리 | certification |
| `variants[].configurations[]` | 트림, 판매 기간·상태, 기본 품목·보증·안전도·media | configuration |
| `configurations[].price` | 기준일 기본 가격 두 종류와 표시 가격 | configuration_price |
| `configurations[].options[]` | 기준일에 선택 가능한 품목·package·외장/내장 색상의 이름·종류·추가 가격·포함 품목 | configuration_option |

`modelYearId`를 지정하면 단종 연식도 공개 상태라면 반환합니다. 생략하면 filter 없는 공개
configuration을 대상으로 목록과 같은 상태·출시일·연식·판매 시작일·UUID 순서를 적용합니다.
모델에 속하지 않는 연식 UUID와 없는 모델은 404입니다.

옵션은 `(configuration_id, code)`별로 `effective_from <= asOf`인 가장 최근 행 하나를 선택하고
`is_selectable=true`인 항목만 반환합니다. 선택 불가 전환 뒤에는 과거 행을 함께 반환하지 않으며,
가격이 바뀌면 같은 code의 새 effective 행으로 조회 결과를 교체합니다.

### 상태 계산

- `asOf < sales_started_on`: `UPCOMING`
- `sales_started_on <= asOf`이고 `sales_ended_on IS NULL OR asOf < sales_ended_on`: `ON_SALE`
- `sales_ended_on IS NOT NULL AND sales_ended_on <= asOf`: `DISCONTINUED`

모델 연식과 configuration의 기간을 함께 만족하는 경우만 판매 중으로 봅니다.

### 오류

기존 RFC 9457 `ProblemDetail` 형식을 사용합니다.

| 상황 | status |
| --- | --- |
| malformed UUID·enum·날짜, 입력 범위 오류 | 400 |
| 없거나 공개 configuration이 없는 모델·연식 | 404 |
| 비회원의 쓰기 method, 다른 보호 API | 기존 Security 계약의 401·403·405 |
| schema 모순이나 예상하지 못한 서버 오류 | 500 |

## 수용 기준

### DB와 자료 표현

- migration 적용 후 실제 PostgreSQL에 설계의 11개 테이블만 생성됩니다.
- 모든 명시 FK, UNIQUE, CHECK와 index가 이름까지 검증됩니다.
- runtime role은 11개 table의 `SELECT`만 성공하고 각 table의 DML·TRUNCATE와 11개 identity
  sequence의 `nextval`이 거부됩니다. 기존 비-catalog 권한은 회귀하지 않습니다.
- 같은 모델·세대·연식 code 중복과 다른 연식의 powertrain을 연결한 variant가 거부됩니다.
- 대표 fixture는 ICE/HEV, BEV와 FCEV의 장치 조합을 표현합니다. DB는 component 종류별 전용
  칼럼 규칙을 강제하고, 동력 형식별 필수 자식의 완전성은 017 원문 mapping에서 검토합니다.
- 카니발형 인승·루프·휠 변형을 트림 중복 없이 표현합니다.
- K5형 같은 트림의 세제 혜택 전·후 가격과 유상 선택 품목을 표현합니다.
- EV6형 kWh·Ah·V, 배터리 제조사·생산국·cell 형태·화학계, 전·후 모터와 휠별 전비·
  주행거리를 표현합니다.
- 넥쏘형 연료전지·수소 저장량, km/kg와 보증을 표현합니다.
- 단종 세대·연식·configuration을 삭제하지 않고 기준일 상태를 복원합니다.
- 가격 변경은 `effective_from`이 다른 행으로 보존되고 기준일의 가장 최근 한 행을 선택합니다.
- 옵션 가격·선택 상태 변경도 `effective_from`이 다른 행으로 보존되고 code별 가장 최근 한 행만
  기준일 상세에 반환합니다.

### 조회와 API

- 세 공개 GET만 비회원에게 허용하고 같은 주소의 쓰기 method는 공개하지 않습니다.
- 검색·모든 단일/조합 filter가 같은 판매 구성 기준으로 동작합니다.
- 목록 모델이 중복되지 않고 가격·출시일·이름 정렬과 page 동률 순서가 안정적입니다.
- 판매 중·예정·단종 연식이 함께 있는 모델에서 대표 연식과 모델 상태가 위 우선순위로
  유일하게 결정됩니다.
- 상세가 powertrain component, variant, configuration, option과 인증 조건을 빠뜨리지 않습니다.
- 목록·상세 SQL 수가 결과 행 수에 비례하지 않습니다.
- 내부 id가 응답에 노출되지 않고 OpenAPI와 실제 JSON nullability가 일치합니다.
- 대표 fixture 성공은 production catalog 데이터 완료로 보고하지 않습니다.

## 개발 계획

### 작업 1. catalog 계약과 대표 fixture를 정의합니다

- `catalog` module 경계와 공개 enum·UUID·날짜·가격 type을 만듭니다.
- K5형 ICE/HEV, 카니발형 MPV, EV6형 BEV, 넥쏘형 FCEV fixture를 정의합니다.
- 잘못된 component 전용 값, 단일 행 기간, 가격과 FK가 실패하는 테스트를 먼저 작성합니다.

완료 결과는 schema 구현이 만족해야 할 자동화된 입력·실패 계약입니다.

### 작업 2. V13~V17에 카탈로그 테이블을 구현합니다

- 현재 최신 V12 다음에서 브랜드부터 인증까지 11개 테이블을 FK 의존 순서의 5개
  migration으로 만듭니다.

| migration | 함께 생성하는 table | 이 경계에서 함께 실패·재시도하는 이유 |
| --- | --- | --- |
| `V13__create_car_product_hierarchy.sql` | `car_brand`, `car_model`, `car_generation`, `car_model_year` | 뒤의 모든 카탈로그 행이 의존하는 제품 식별 계층이며 일부 계층만으로는 조회 입력이 성립하지 않음 |
| `V14__create_car_powertrain_schema.sql` | `car_powertrain`, `car_powertrain_component` | 동력계통 본체와 반복 장치가 하나의 기술 사양을 구성함 |
| `V15__create_car_vehicle_offering.sql` | `car_vehicle_variant`, `car_configuration` | 기술 변형과 공개 가능한 판매 구성이 함께 있어야 현재 조회 대상이 됨 |
| `V16__create_car_pricing_and_option_history.sql` | `car_configuration_price`, `car_configuration_option` | 판매 구성에 종속된 두 기준일 이력을 같은 상업 정보 단계에서 복구함 |
| `V17__create_car_certification.sql` | `car_certification` | 판매 구성과 독립적으로 보완·재시도할 수 있는 조건별 측정 정보임 |

기능 전체를 V13 하나에 두면 뒤쪽 DDL 오류도 11개 테이블 전체를 rollback·재적용하게 되고,
검토 시 서로 다른 다섯 책임이 한 파일에 섞입니다. 반대로 테이블마다 V13~V23으로 나누면
application이 소비하지 않는 부모만의 중간 checkpoint가 생기고 version·검토 단위만 늘어납니다.
따라서 FK 선행 순서는 유지하되, 내부 table이 함께 있어야 의미가 완성되는 다섯 단위마다
PostgreSQL transaction과 Flyway 실패 재시작 지점을 둡니다.
- 기존 Flyway callback의 generic grant 뒤에 11개 catalog table의 DML·TRUNCATE·REFERENCES·
  TRIGGER와 11개 identity sequence의 모든 권한을 exact 이름으로 revoke합니다. 기존 비-catalog
  table과 sequence 권한은 유지합니다.

완료 결과는 빈 DB에서 한 번에 적용되고 `ddl-auto=validate`가 통과하는 schema입니다.

### 작업 3. PostgreSQL schema·fixture test를 닫습니다

- Testcontainers PostgreSQL에서 migration과 모든 positive/negative 제약을 검증합니다.
- migration process test의 runtime 연결로 11개 table별 SELECT 성공과 DML·TRUNCATE 실패,
  11개 sequence별 `nextval` 실패, 기존 비-catalog 권한 유지를 검증합니다.
- 대표 fixture를 test transaction에 입력하고 11개 관계를 역방향으로도 대조합니다.
- price 기준일 조회와 판매 상태 경계를 SQL 수준에서 검증합니다.
- 같은 option code의 가격 변경·선택 불가 전환 전후에 기준일별 한 행 또는 0행만 반환되는지
  검증합니다.

완료 결과는 문서가 아니라 실제 DB가 자동차 구조를 강제한다는 증거입니다.

### 작업 4. 조회 persistence와 application 규칙을 구현합니다

- 브랜드 목록, 모델 page와 모델 상세를 projection·묶음 조회로 구현합니다.
- 검색 정규화·escape, 같은 구성 filter, 현재 가격과 대표 연식 선택을 application에 둡니다.
- 기준일·판매 종료 exclusive 경계, 판매 중·예정·단종 연식 공존, nullable 출시일과 모든
  대표 연식 동률 단계, 가격 null과 목록 동률 정렬을 테스트합니다.
- 목록과 상세 query count 상한, 대표 query의 실행 계획을 검증합니다.

완료 결과는 JPA entity나 내부 id를 노출하지 않는 조회 결과입니다.

### 작업 5. Web·Security·OpenAPI를 구현합니다

- 세 GET Controller, request validation과 response DTO를 만듭니다.
- GET method만 anonymous allow-list에 추가합니다.
- 200·400·404·401·403·405·500 계약과 OpenAPI schema를 테스트합니다.

완료 결과는 비회원이 문서화된 계약으로 조회할 수 있는 API입니다.

### 작업 6. 전체 검증과 영구 문서를 갱신합니다

- 좁은 테스트 뒤 `./gradlew check --no-daemon --rerun-tasks --console=plain`을 실행합니다.
- 실제 migration을 기준으로 domain, module map, schema reference, Security·Flyway·OpenAPI 문서를
  갱신합니다.
- `verify-dev`로 최초 목적부터 final local 후보를 독립 검증합니다.

완료 결과는 코드·DB·테스트·영구 문서가 같은 11개 테이블과 API를 설명하는 상태입니다.

### 작업 7. 실제 초기 데이터 작업으로 인계합니다

- 016에서 최초 브랜드와 과거 기간을 확정합니다.
- 공식 가격표·catalog·제원·인증 자료의 원문 값을 11개 테이블의 칼럼에 mapping합니다.
- 선택한 범위의 누락을 검수한 뒤 별도 data migration으로 적용합니다.

## 전환·복구·배포 경계

- schema migration과 production data migration을 분리합니다.
- 적용된 Flyway 파일은 수정하지 않고 정정은 다음 migration에서 수행합니다.
- rollback은 이미 적용된 schema를 무단으로 down migration하지 않고 호환 가능한 application
  image와 forward fix를 사용합니다.
- catalog runtime role은 조회만 허용합니다. 쓰기 API가 필요하면 writer role과 감사 정책을
  별도 설계합니다.
- 계획 checkpoint, 구현 commit, push와 staging 배포는 각각 별도 권한을 사용합니다.

## 구현 후 갱신할 영구 문서

| 문서 | 반영할 실제 결과 |
| --- | --- |
| `docs/domain/130-car-catalog.md`와 router | 모델·세대·연식·변형·판매 구성·가격 의미 |
| `docs/project/210-module-map.md` | 구현된 `catalog` module과 의존 |
| `docs/project/schema/110-car-catalog.md`와 router | 11개 테이블의 실제 column·constraint·index |
| `docs/tech-stack/220-spring-security.md` | 세 공개 GET allow-list |
| `docs/tech-stack/240-flyway.md` | catalog migration과 SELECT-only 권한 |
| `docs/tech-stack/260-openapi.md` | 공개 조회·오류 schema |
| `docs/product/200-feature-scope.md`, `docs/project/100-project-overview.md` | 구현 완료와 실제 data 미적재 상태 |

## 관련 문서

- **물리 DB 상세**: [016 신차 카탈로그 조회 기능 DB 설계](016-new-car-catalog-query-api-design.md)
- **후속 production data**: [017 자동차 카탈로그 초기 데이터 구축](017-car-catalog-initial-data.md)
- **제품 방향**: [200 제품 기능 범위](../../../product/200-feature-scope.md)
- **현재 코드 구조**: [210 모듈 지도](../../../project/210-module-map.md)
- **API 기준**: [230 REST API 설계](../../../architecture/230-rest-api-design.md)
- **DB 기준**: [230 Spring Data JPA](../../../tech-stack/230-spring-data-jpa.md),
  [240 Flyway](../../../tech-stack/240-flyway.md),
  [250 PostgreSQL](../../../tech-stack/250-postgresql.md)
