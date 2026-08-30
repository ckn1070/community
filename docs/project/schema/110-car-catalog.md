# 자동차 카탈로그 Schema Reference

이 문서는 카탈로그 migration V13~V17 적용 뒤의 현재 관계형 구조, 테이블 분리 이유와 조회에
필요한 물리 계약을 소유합니다. 업무 용어·상태·공개 규칙은
[130 자동차 카탈로그 도메인](../../domain/130-car-catalog.md)이 소유하고, 실행 가능한 DDL의
source of truth는 migration입니다.

## 적용 범위

```text
car_brand
  └─ car_model
      └─ car_generation
          └─ car_model_year
              ├─ car_powertrain
              │   └─ car_powertrain_component
              └─ car_vehicle_variant ──> car_powertrain
                  ├─ car_configuration
                  │   ├─ car_configuration_price
                  │   └─ car_configuration_option
                  └─ car_certification
```

- migration:
  - `V13__create_car_product_hierarchy.sql`: 브랜드·모델·세대·연식
  - `V14__create_car_powertrain_schema.sql`: 동력계통·구성 장치
  - `V15__create_car_vehicle_offering.sql`: 기술 변형·판매 구성
  - `V16__create_car_pricing_and_option_history.sql`: 가격·선택 품목 이력
  - `V17__create_car_certification.sql`: 조건별 인증
- runtime 권한: 11개 테이블 `SELECT`만 허용, DML·`TRUNCATE`·sequence 권한 없음
- 삭제 정책: 제품 관계는 `RESTRICT`, configuration의 가격·옵션 이력만 `CASCADE`
- 외부 식별자: 공개 대상 7개 테이블은 UUID `public_id`, 내부 관계는 `BIGINT id`
- 기간 종료일: exclusive
- enum-like 값: `VARCHAR`와 명시적 `CHECK`

## 설계 범위와 분리 기준

현재 구조는 자동차의 모든 사실을 저장하는 백과사전 schema가 아닙니다. 브랜드·모델 조회와
상세 화면에서 사용하는 제품 식별, 주요 기술 사양, 판매 구성, 가격·옵션 이력과 인증 조건만
11개 테이블로 표현합니다.

새 테이블은 다음 중 하나 이상을 만족할 때만 분리합니다.

1. 부모 하나에 자식이 여러 개 존재합니다.
2. 독립된 생명주기나 적용 시점이 있습니다.
3. FK·UNIQUE·CHECK로 별도 무결성을 보호해야 합니다.
4. 검색·filter·정렬 또는 상세 묶음 조회의 독립 단위입니다.

| 테이블 | 한 행의 의미 | 분리 이유 | 더 합치거나 나누지 않은 이유 |
| --- | --- | --- | --- |
| `car_brand` | 제조 브랜드 | 여러 모델의 공통 소유자입니다. | 현재 한 시장이므로 국가 기준정보를 따로 만들지 않습니다. |
| `car_model` | 세대가 바뀌어도 유지되는 모델 | 검색·표시와 세대의 부모입니다. | 브랜드에 합치면 세대·연식 관계를 표현할 수 없습니다. |
| `car_generation` | 한 세대 또는 상품성 변경 단계 | 모델의 변화를 순서대로 보존합니다. | 판매 기간은 시장별 모델 연식이 소유합니다. |
| `car_model_year` | 한 시장의 연식·edition | 사양과 판매 기간이 세대 안에서도 달라집니다. | 시장·연식·edition이 함께 식별 기준입니다. |
| `car_powertrain` | 연식이 제공하는 동력계통 조합 | 검색용 동력 형식·연료와 시스템 성능을 소유합니다. | 개별 장치 값은 반복 가능한 component로 분리합니다. |
| `car_powertrain_component` | 엔진·모터·배터리·연료전지·탱크 한 개 | 같은 종류 장치가 여러 개일 수 있습니다. | 종류별 테이블은 공통 출력·용량·배치 칼럼과 조회를 반복시킵니다. |
| `car_vehicle_variant` | 차체·인승·휠·동력계통이 같은 기술 변형 | 같은 트림도 제원·인증 조건이 달라질 수 있습니다. | 차체·휠 조합을 더 나누면 현재 API의 join만 늘어납니다. |
| `car_configuration` | 실제 판매 트림·채널 | 기술 제원과 판매 기간·표시 정보의 생명주기가 다릅니다. | 독립 trim 사전은 현재 consumer가 없습니다. |
| `car_configuration_price` | 특정 날짜부터의 기본 가격 | 덮어쓰지 않고 과거 가격을 복원합니다. | 판매 구성과 다른 부모가 없으므로 범용 가격 테이블이 필요 없습니다. |
| `car_configuration_option` | 특정 날짜부터의 옵션 가격·선택 상태 | 품목별 변경 시점이 기본 가격과 다릅니다. | 범용 가격 테이블은 대상 FK와 선택 상태 제약을 모호하게 만듭니다. |
| `car_certification` | 기술 변형의 측정 조건별 공식 결과 | 휠·카메라·온도에 따라 값이 반복됩니다. | JSONB로 두면 단위·필수값·조건 중복을 DB가 보호하지 못합니다. |

기본 품목·보증·안전도·media는 현재 독립 검색·정렬하지 않아 configuration의 제한된 JSONB
배열로 둡니다. 유상 색상은 가격과 선택 상태가 있으므로 option 행입니다. 공식 원문과 DB 값의
대응은 runtime FK가 아니라 017 초기 데이터 작업의 version-controlled mapping으로 관리합니다.
따라서 source document 테이블이나 fact별 identity·version·source 테이블은 두지 않습니다.

## 제품 계층

| table | 책임 | 주요 column | 식별·관계 constraint | 검증 constraint |
| --- | --- | --- | --- | --- |
| `car_brand` | 제조 브랜드 | `public_id`, `code`, `name_ko`, `name_en`, `country_code`, `homepage_url`, `display_order`, 감사 시각 | `pk_car_brand`, `uk_car_brand_public_id`, `uk_car_brand_code` | code·이름·국가 코드·표시 순서·감사 시각 check |
| `car_model` | 세대가 바뀌어도 유지되는 모델 | `brand_id`, `public_id`, `code`, 이름, `name_normalized`, `summary`, `display_order`, 감사 시각 | `pk_car_model`, `fk_car_model_brand`, `uk_car_model_public_id`, `uk_car_model_brand_code`, `uk_car_model_brand_name_normalized` | code·이름·정규화 이름·표시 순서·감사 시각 check |
| `car_generation` | 모델의 세대·변경 단계 | `model_id`, `public_id`, `code`, `name`, `generation_order`, `change_type`, 감사 시각 | `pk_car_generation`, `fk_car_generation_model`, `uk_car_generation_public_id`, `uk_car_generation_model_code`, `uk_car_generation_model_order` | `FULL_CHANGE`, `FACELIFT`, `MINOR_CHANGE`; 양수 순서·감사 시각 |
| `car_model_year` | 시장별 연식 edition과 판매 기간 | `generation_id`, `public_id`, `market_code`, `model_year`, `edition_code`, `display_name`, `released_on`, 판매 기간, `summary`, 감사 시각 | `pk_car_model_year`, `fk_car_model_year_generation`, `uk_car_model_year_public_id`, `uk_car_model_year_generation_market_year_edition` | 시장·연식 범위·필수 문자열·`start < end`·감사 시각 |

## 동력계통과 장치

### `car_powertrain`

| column group | columns |
| --- | --- |
| 식별·소유 | `id`, `public_id`, `model_year_id`, `code`, `name` |
| 에너지·구동 | `propulsion_type`, `primary_fuel_type`, `secondary_fuel_type`, `drivetrain_type`, `transmission_type`, `transmission_gears` |
| 합산 성능 | `system_max_power_kw`, `system_max_power_ps`, `system_max_torque_nm`, `zero_to_hundred_seconds`, `top_speed_kmh` |
| 충전 | `max_ac_charge_kw`, `ac_charge_connector`, `ac_charge_minutes`, `ac_charge_from_percent`, `ac_charge_to_percent`, `ac_charge_power_kw`, `max_dc_charge_kw`, `dc_charge_connector`, `fast_charge_minutes`, `fast_charge_from_percent`, `fast_charge_to_percent`, `fast_charge_power_kw` |
| 감사 | `created_at`, `updated_at` |

- 식별·관계: `pk_car_powertrain`, `fk_car_powertrain_model_year`,
  `uk_car_powertrain_public_id`, `uk_car_powertrain_model_year_code`,
  `uk_car_powertrain_model_year_id`
- `id`는 PK로 이미 유일하지만 PostgreSQL은 복합 FK가 참조하는 열 조합에도 `UNIQUE`를
  요구하므로 `(model_year_id, id)`를 별도 참조 key로 둡니다.
- 동력 형식: `ICE`, `MHEV`, `HEV`, `PHEV`, `EREV`, `BEV`, `FCEV`
- 연료: `GASOLINE`, `DIESEL`, `LPG`, `ELECTRICITY`, `HYDROGEN`, `OTHER`
- 핵심 check: 동력 형식별 외부 에너지 조합, 구동·변속 허용값, 양수 성능, AC·DC 대표 충전
  시간·시작·종료 비율·충전기 출력의 묶음 입력, 충전 가능 동력 형식, 감사 시각

### `car_powertrain_component`

| column group | columns |
| --- | --- |
| 식별·배치 | `id`, `powertrain_id`, `component_type`, `component_role`, `position`, `name`, `fuel_type`, `sort_order` |
| 엔진·성능 | `displacement_cc`, `cylinder_count`, `aspiration_type`, `max_power_kw`, `max_power_ps`, `max_power_rpm`, `max_torque_nm`, `max_torque_kgf_m`, `max_torque_rpm_from`, `max_torque_rpm_to` |
| 저장 장치 | `capacity_kwh`, `usable_capacity_kwh`, `capacity_ah`, `voltage_v`, `capacity_l`, `capacity_kg`, `pressure_bar` |
| 제조 정보 | `manufacturer_name`, `cell_manufacturer_name`, `production_country_code`, `cell_type`, `chemistry` |
| 감사 | `created_at`, `updated_at` |

- 식별·관계: `pk_car_powertrain_component`, `fk_car_powertrain_component_powertrain`,
  `uk_car_powertrain_component_identity`
- 장치: `ENGINE`, `MOTOR`, `BATTERY`, `FUEL_TANK`, `FUEL_CELL`, `HYDROGEN_TANK`
- 핵심 check: 장치별 role, position, 엔진·연료탱크 연료 필수, 흡기 방식
  `NATURALLY_ASPIRATED`·`TURBO`·`SUPERCHARGED`, 양수 제원, 토크 회전수 순서, 국가 코드,
  사용 가능 배터리 용량이 총 용량 이하인지, 장치별 필수 용량과 다른 장치 전용 column의 `NULL`,
  감사 시각

## 기술 변형과 판매 구성

### `car_vehicle_variant`

| column group | columns |
| --- | --- |
| 식별·소유 | `id`, `public_id`, `model_year_id`, `powertrain_id`, `code`, `name` |
| 분류 | `body_type`, `roof_type`, `seating_capacity`, `door_count` |
| 치수·적재·중량 | `length_mm`, `width_mm`, `height_mm`, `wheelbase_mm`, `front_track_mm`, `rear_track_mm`, `ground_clearance_mm`, `cargo_capacity_l`, `max_cargo_capacity_l`, `curb_weight_kg`, `gross_vehicle_weight_kg`, `max_braked_towing_capacity_kg`, `max_unbraked_towing_capacity_kg`, `max_roof_load_kg`, `minimum_turning_radius_m` |
| 휠·섀시 | `wheel_size_inch`, `front_tire`, `rear_tire`, `front_suspension`, `rear_suspension`, `steering_type`, `front_brake`, `rear_brake` |
| 감사 | `created_at`, `updated_at` |

- 식별·관계: `pk_car_vehicle_variant`, `uk_car_vehicle_variant_public_id`,
  `uk_car_vehicle_variant_model_year_code`, `fk_car_vehicle_variant_model_year`,
  `fk_car_vehicle_variant_model_year_powertrain`
- `(model_year_id, powertrain_id)` 복합 FK는 variant가 참조한 powertrain의
  `model_year_id`와 variant의 값을 함께 대조합니다.
- 차체: `SEDAN`, `HATCHBACK`, `WAGON`, `COUPE`, `CONVERTIBLE`, `SUV`, `MPV`,
  `PICKUP`, `VAN`, `OTHER`
- 핵심 check: roof 허용값, 양수 치수·중량, 최대 적재량·총중량·견인중량 순서, 감사 시각

### `car_configuration`

| column group | columns |
| --- | --- |
| 식별·판매 | `id`, `public_id`, `vehicle_variant_id`, `code`, `trim_name`, `sales_channel`, `sales_started_on`, `sales_ended_on`, `display_order`, `is_published` |
| 표시 정보 | `standard_features`, `warranties`, `safety_ratings`, `media` |
| 감사 | `created_at`, `updated_at` |

- 식별·관계: `pk_car_configuration`, `fk_car_configuration_vehicle_variant`,
  `uk_car_configuration_public_id`, `uk_car_configuration_variant_channel_code`
- 판매 채널: `PRIVATE`, `BUSINESS`, `RENTAL`, `DISABLED`
- 네 JSONB column은 최상위 배열만 허용합니다.
- 판매 기간·표시 순서·감사 시각을 check합니다.

## 가격과 선택 품목 이력

| table | columns | 식별·관계 | 핵심 check |
| --- | --- | --- | --- |
| `car_configuration_price` | `id`, `configuration_id`, `effective_from`, `currency_code`, `price_before_tax_benefit`, `price_after_tax_benefit`, `source_price_label`, 감사 시각 | `pk_car_configuration_price`, `fk_car_configuration_price_configuration`, `uk_car_configuration_price_effective_from` | 통화 코드, 0 이상 금액, 세제 혜택 후 가격이 이전 가격 이하, label·감사 시각 |
| `car_configuration_option` | `id`, `configuration_id`, `code`, `name`, `option_type`, `category`, `is_selectable`, 두 가격, `currency_code`, `included_items`, `effective_from`, `display_order`, 감사 시각 | `pk_car_configuration_option`, `fk_car_configuration_option_configuration`, `uk_car_configuration_option_code_effective_from` | option 종류, 선택 가능 행의 가격 필수, 선택 불가 행의 가격 금지, 통화 코드, JSON 배열, 표시 순서·감사 시각 |

- option 종류: `SINGLE`, `PACKAGE`, `EXTERIOR_COLOR`, `INTERIOR_COLOR`
- 가격은 `effective_from <= asOf`인 최신 한 행을 선택합니다.
- option은 같은 조건의 `(configuration_id, code)`별 최신 행을 고른 뒤 `is_selectable=true`만 노출합니다.

## 조건별 인증

### `car_certification`

| column group | columns |
| --- | --- |
| 식별·조건 | `id`, `vehicle_variant_id`, `condition_code`, `condition_name`, `certification_type`, `certification_standard`, `temperature_condition`, `wheel_size_inch`, `tire_specification`, `built_in_camera`, `curb_weight_kg` |
| 측정값 | `efficiency_grade`, `city_efficiency`, `highway_efficiency`, `combined_efficiency`, `efficiency_unit`, `city_range_km`, `highway_range_km`, `combined_range_km`, `co2_g_per_km` |
| 감사 | `created_at`, `updated_at` |

- 식별·관계: `pk_car_certification`, `fk_car_certification_vehicle_variant`,
  `uk_car_certification_variant_condition`
- 인증 종류: `LIQUID_FUEL`, `ELECTRIC`, `HYDROGEN`
- 인증 기준: 비어 있지 않은 source-backed code. 현재 알려진 값만으로 enum을 닫지 않습니다.
- 전기 인증만 `NORMAL` 또는 `LOW` 온도 조건을 필수로 가집니다.
- 단위와 필수값: 액체연료 `KM_PER_L`+복합 효율+CO₂, 전기 `KM_PER_KWH`+복합 주행거리,
  수소 `KM_PER_KG`+복합 효율+복합 주행거리

## 무결성 책임 경계

모든 규칙을 DB constraint로 밀어 넣지 않습니다. 현재 책임은 다음과 같습니다.

| 책임 | 현재 보장 |
| --- | --- |
| DB | 부모 소속, 중복 식별자, enum-like 허용값, 양수·기간·단위·장치별 칼럼, variant와 powertrain의 연식 조합 |
| 조회 application | 검색 정규화와 literal escape, 같은 configuration filter, 기준일 가격·옵션, 대표 연식과 안정 정렬 |
| 017 데이터 검수 | 동력 형식별 필수 component 완전성, 부모·자식 판매 기간 포함, 가격·옵션 적용일, 인증 조건과 원문 대응 |

여러 자식 행을 세어야 하는 필수 component 조합과 부모 기간을 읽어야 하는 포함 관계는 단일
row `CHECK`로 흉내 내지 않습니다. runtime role은 11개 테이블의 `SELECT`만 허용하며 쓰기와
identity sequence 사용은 거부합니다.

## 조회 경로

카탈로그 조회 adapter는 jOOQ를 사용합니다. Flyway V13~V17에서 build-time schema model을
생성하고 브랜드, 모델 검색의 기반 관계, 상세의 모델·연식 조회는 generated table·field DSL로
구성합니다. CTE 논리 column 집계와 상세 wide projection의 PostgreSQL SQL은 검증된 형태를
유지한 채 jOOQ가 parameter binding, 실행과 record 변환을 담당합니다. 적용 범위와 raw SQL·DSL 경계는
[jOOQ 사용 가이드](../../tech-stack/235-jooq.md)가 소유합니다.

### 브랜드 목록

기준일에 공개된 판매 중 configuration이 하나 이상 있는 브랜드만 반환합니다. 모델 연식과
configuration의 판매 기간을 모두 만족하는 모델 수를 브랜드별로 집계합니다.

### 모델 목록

```text
brand → model → generation → model_year
      → vehicle_variant → powertrain → configuration
      → 기준일 이전의 최신 configuration_price
```

- 검색어·브랜드·차체·동력·연료·가격·판매 상태 filter는 같은 configuration 행에서 시작합니다.
- 모델 하나에 여러 연식·변형·구성이 있어도 모델은 한 행만 반환합니다.
- 조건을 만족한 연식 중 판매 중, 예정, 단종 순서와 출시일·연식·판매 시작일·공개 UUID
  동률 기준으로 대표 연식 한 건을 선택합니다.
- 시작 가격은 조건을 만족한 공개 configuration의 기준일 표시 가격 최솟값입니다.

목록은 전체 개수와 page content 두 query로 읽습니다. 현재 literal 부분 검색은 작은
`car_model` 범위를 scan하며, 실제 실행 계획에서 필요성이 확인되기 전에는 trigram·검색엔진을
추가하지 않습니다.

### 모델 상세

모델·세대·연식을 먼저 읽고 선택 연식의 powertrain/component, variant/certification,
configuration/price/option을 고정된 묶음 query로 읽습니다. 현재 persistence는 상세를 최대
다섯 query로 읽고 전용 assembler가 graph 중복을 제거하며, 결과 행 수에 따라 SQL 수가
증가하지 않습니다. JSONB 변환도 query 실행과 분리합니다. 한 read-only transaction 안에서
같은 기준일의 가격·옵션과 연식을 읽습니다.

## JSONB 승격 기준

`standard_features`, `warranties`, `safety_ratings`, `media`, `included_items`는 표시 전용
배열입니다. 다음 중 하나가 실제 요구가 될 때 해당 정보만 typed column이나 관계형 자식으로
승격합니다.

- 독립 검색·filter·정렬
- 항목별 FK·UNIQUE·CHECK
- 판매 구성과 다른 갱신 주기
- 여러 기능이 공유하는 안정된 identity

희소한 제조사 수치가 한 번 등장했다는 이유로 EAV나 새 테이블을 만들지 않습니다. 두 개 이상
제조사에서 반복되고 비교·조회 요구가 확인되면 typed column을 우선 검토합니다.

## 조회 Index

| index | 대상과 조건 |
| --- | --- |
| `idx_car_powertrain_model_year_filter` | `car_powertrain(model_year_id, propulsion_type, primary_fuel_type)` |
| `idx_car_variant_model_year_body` | `car_vehicle_variant(model_year_id, body_type, powertrain_id)` |
| `idx_car_configuration_variant_sales` | 공개 configuration의 variant·판매 기간 partial index |
| `idx_car_option_configuration` | `car_configuration_option(configuration_id, display_order)` |

## 관련 문서

- **실행 source**: 위에 열거한 V13~V17 migration
- **현재 업무 개념과 조회 의미**: [130 자동차 카탈로그 도메인](../../domain/130-car-catalog.md)
- **작업 상태와 공개 조회 API**: [016 신차 카탈로그 schema·조회 API](../../work-items/features/2026/016-new-car-catalog-query-api.md)
- **결정 근거**: [016 DB 설계 결정 기록](../../work-items/features/2026/016-new-car-catalog-query-api-design.md)
- **migration 운영 기준**: [240 Flyway](../../tech-stack/240-flyway.md)
- **조회 구현 기준**: [235 jOOQ](../../tech-stack/235-jooq.md)
