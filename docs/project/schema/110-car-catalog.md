# 자동차 카탈로그 Schema Reference

이 문서는 `V13__create_car_catalog_schema.sql` 적용 뒤의 자동차 카탈로그 물리 schema를 요약합니다.
실행 가능한 source of truth는 migration이며, 설계 이유와 조회 계약은 015 work item이 소유합니다.

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

- migration: `V13__create_car_catalog_schema.sql`
- runtime 권한: 11개 테이블 `SELECT`만 허용, DML·`TRUNCATE`·sequence 권한 없음
- 삭제 정책: 제품 관계는 `RESTRICT`, configuration의 가격·옵션 이력만 `CASCADE`
- 외부 식별자: 공개 대상 7개 테이블은 UUID `public_id`, 내부 관계는 `BIGINT id`
- 기간 종료일: exclusive
- enum-like 값: `VARCHAR`와 명시적 `CHECK`

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
| 충전 | `max_ac_charge_kw`, `max_dc_charge_kw`, `fast_charge_minutes`, `fast_charge_from_percent`, `fast_charge_to_percent` |
| 감사 | `created_at`, `updated_at` |

- 식별·관계: `pk_car_powertrain`, `fk_car_powertrain_model_year`,
  `uk_car_powertrain_public_id`, `uk_car_powertrain_model_year_code`,
  `uk_car_powertrain_model_year_id`
- 동력 형식: `ICE`, `MHEV`, `HEV`, `PHEV`, `EREV`, `BEV`, `FCEV`
- 연료: `GASOLINE`, `DIESEL`, `LPG`, `ELECTRICITY`, `HYDROGEN`, `OTHER`
- 핵심 check: 동력 형식별 외부 에너지 조합, 구동·변속 허용값, 양수 성능,
  충전 시간·시작·종료 비율의 동시 입력, 감사 시각

### `car_powertrain_component`

| column group | columns |
| --- | --- |
| 식별·배치 | `id`, `powertrain_id`, `component_type`, `component_role`, `position`, `name`, `fuel_type`, `sort_order` |
| 엔진·성능 | `displacement_cc`, `cylinder_count`, `aspiration_type`, `max_power_kw`, `max_power_ps`, `max_power_rpm`, `max_torque_nm`, `max_torque_kgf_m`, `max_torque_rpm_from`, `max_torque_rpm_to` |
| 저장 장치 | `capacity_kwh`, `capacity_ah`, `voltage_v`, `capacity_l`, `capacity_kg`, `pressure_bar` |
| 제조 정보 | `manufacturer_name`, `cell_manufacturer_name`, `production_country_code`, `cell_type`, `chemistry` |
| 감사 | `created_at`, `updated_at` |

- 식별·관계: `pk_car_powertrain_component`, `fk_car_powertrain_component_powertrain`,
  `uk_car_powertrain_component_identity`
- 장치: `ENGINE`, `MOTOR`, `BATTERY`, `FUEL_TANK`, `FUEL_CELL`, `HYDROGEN_TANK`
- 핵심 check: 장치별 role, position, 엔진·연료탱크 연료 필수, 흡기 방식
  `NATURALLY_ASPIRATED`·`TURBO`·`SUPERCHARGED`, 양수 제원, 토크 회전수 순서, 국가 코드,
  장치별 필수 용량과 다른 장치 전용 column의 `NULL`, 감사 시각

## 기술 변형과 판매 구성

### `car_vehicle_variant`

| column group | columns |
| --- | --- |
| 식별·소유 | `id`, `public_id`, `model_year_id`, `powertrain_id`, `code`, `name` |
| 분류 | `body_type`, `roof_type`, `seating_capacity`, `door_count` |
| 치수·적재·중량 | `length_mm`, `width_mm`, `height_mm`, `wheelbase_mm`, `front_track_mm`, `rear_track_mm`, `ground_clearance_mm`, `cargo_capacity_l`, `max_cargo_capacity_l`, `curb_weight_kg`, `gross_vehicle_weight_kg` |
| 휠·섀시 | `wheel_size_inch`, `front_tire`, `rear_tire`, `front_suspension`, `rear_suspension`, `steering_type`, `front_brake`, `rear_brake` |
| 감사 | `created_at`, `updated_at` |

- 식별·관계: `pk_car_vehicle_variant`, `uk_car_vehicle_variant_public_id`,
  `uk_car_vehicle_variant_model_year_code`, `fk_car_vehicle_variant_model_year`,
  `fk_car_vehicle_variant_model_year_powertrain`
- 복합 FK는 variant와 powertrain의 `model_year_id` 일치를 강제합니다.
- 차체: `SEDAN`, `HATCHBACK`, `WAGON`, `COUPE`, `CONVERTIBLE`, `SUV`, `MPV`,
  `PICKUP`, `VAN`, `OTHER`
- 핵심 check: roof 허용값, 양수 치수·중량, 최대 적재량과 총중량 순서, 감사 시각

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
| 식별·조건 | `id`, `vehicle_variant_id`, `condition_code`, `condition_name`, `certification_type`, `temperature_condition`, `wheel_size_inch`, `tire_specification`, `built_in_camera`, `curb_weight_kg` |
| 측정값 | `efficiency_grade`, `city_efficiency`, `highway_efficiency`, `combined_efficiency`, `efficiency_unit`, `city_range_km`, `highway_range_km`, `combined_range_km`, `co2_g_per_km` |
| 감사 | `created_at`, `updated_at` |

- 식별·관계: `pk_car_certification`, `fk_car_certification_vehicle_variant`,
  `uk_car_certification_variant_condition`
- 인증 종류: `LIQUID_FUEL`, `ELECTRIC`, `HYDROGEN`
- 전기 인증만 `NORMAL` 또는 `LOW` 온도 조건을 필수로 가집니다.
- 단위와 필수값: 액체연료 `KM_PER_L`+복합 효율+CO₂, 전기 `KM_PER_KWH`+복합 주행거리,
  수소 `KM_PER_KG`+복합 효율+복합 주행거리

## 조회 Index

| index | 대상과 조건 |
| --- | --- |
| `idx_car_powertrain_model_year_filter` | `car_powertrain(model_year_id, propulsion_type, primary_fuel_type)` |
| `idx_car_variant_model_year_body` | `car_vehicle_variant(model_year_id, body_type, powertrain_id)` |
| `idx_car_configuration_variant_sales` | 공개 configuration의 variant·판매 기간 partial index |
| `idx_car_option_configuration` | `car_configuration_option(configuration_id, display_order)` |

## 관련 문서

- **실행 source**: `src/main/resources/db/migration/V13__create_car_catalog_schema.sql`
- **설계와 조회 계약**: [015 신차 카탈로그 schema·조회 API](../../work-items/features/2026/015-new-car-catalog-query-api.md),
  [DB 설계](../../work-items/features/2026/015-new-car-catalog-query-api-design.md)
- **migration 운영 기준**: [Flyway](../../tech-stack/240-flyway.md)
