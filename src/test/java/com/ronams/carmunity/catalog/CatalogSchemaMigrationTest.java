package com.ronams.carmunity.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 자동차 카탈로그 관계와 무결성 규칙을 Flyway가 실제 PostgreSQL schema로 만드는지 검증합니다.
 *
 * <p>이 테스트는 조회 코드가 전제하는 11개 테이블, 명시적 제약과 대표 자동차 fixture의 관계를 H2 대체 없이 고정합니다.
 */
@SpringBootTest
class CatalogSchemaMigrationTest extends PostgreSqlIntegrationTestSupport {

  private static final List<String> CATALOG_TABLES =
      List.of(
          "car_brand",
          "car_certification",
          "car_configuration",
          "car_configuration_option",
          "car_configuration_price",
          "car_generation",
          "car_model",
          "car_model_year",
          "car_powertrain",
          "car_powertrain_component",
          "car_vehicle_variant");

  @Autowired private JdbcTemplate jdbcTemplate;

  /** 설계에 없는 source·version 보조 table을 만들지 않고 자동차 제품 구조 11개만 생성합니다. */
  @Test
  void createsExactlyElevenCatalogTables() {
    List<String> tableNames =
        jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name LIKE 'car_%'
            ORDER BY table_name
            """,
            String.class);

    assertThat(tableNames).containsExactlyElementsOf(CATALOG_TABLES);
  }

  /** 카탈로그 초기 schema를 함께 배포·복구하는 다섯 업무 영역의 migration으로 적용합니다. */
  @Test
  void appliesCatalogSchemaInFiveCohesiveMigrations() {
    List<String> versions =
        jdbcTemplate.queryForList(
            """
            SELECT version
            FROM flyway_schema_history
            WHERE version::integer BETWEEN 13 AND 17
            ORDER BY installed_rank
            """,
            String.class);
    List<String> descriptions =
        jdbcTemplate.queryForList(
            """
            SELECT description
            FROM flyway_schema_history
            WHERE version::integer BETWEEN 13 AND 17
            ORDER BY installed_rank
            """,
            String.class);

    assertThat(versions).containsExactly("13", "14", "15", "16", "17");
    assertThat(descriptions)
        .containsExactly(
            "create car product hierarchy",
            "create car powertrain schema",
            "create car vehicle offering",
            "create car pricing and option history",
            "create car certification");
  }

  /** 설계가 이름으로 고정한 129개 constraint와 조회 index 네 개를 빠짐없이 생성합니다. */
  @Test
  void createsExactNamedConstraintsAndQueryIndexes() {
    Set<String> constraintNames =
        Set.copyOf(
            jdbcTemplate.queryForList(
                """
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name LIKE 'car_%'
                  AND constraint_name !~ '_not_null$'
                """,
                String.class));
    List<String> indexNames =
        jdbcTemplate.queryForList(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname LIKE 'idx_car_%'
            ORDER BY indexname
            """,
            String.class);

    assertThat(constraintNames).containsExactlyInAnyOrderElementsOf(EXPECTED_CONSTRAINTS);
    assertThat(indexNames)
        .containsExactly(
            "idx_car_configuration_variant_sales",
            "idx_car_option_configuration",
            "idx_car_powertrain_model_year_filter",
            "idx_car_variant_model_year_body");
  }

  /** 내연기관·하이브리드·전기·수소전기차가 요구하는 서로 다른 관계를 한 schema에 저장합니다. */
  @Test
  void storesRepresentativeVehicleGraphs() {
    jdbcTemplate.execute(REPRESENTATIVE_CATALOG_FIXTURE);

    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT propulsion_type, COUNT(*) AS count
                FROM car_powertrain
                WHERE id < 0
                GROUP BY propulsion_type
                ORDER BY propulsion_type
                """))
        .containsExactly(
            Map.of("propulsion_type", "BEV", "count", 1L),
            Map.of("propulsion_type", "FCEV", "count", 1L),
            Map.of("propulsion_type", "HEV", "count", 1L),
            Map.of("propulsion_type", "ICE", "count", 2L));

    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT DISTINCT component_type
                FROM car_powertrain_component
                WHERE id < 0
                ORDER BY component_type
                """,
                String.class))
        .containsExactly("BATTERY", "ENGINE", "FUEL_CELL", "FUEL_TANK", "HYDROGEN_TANK", "MOTOR");

    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT condition_code
                FROM car_certification
                WHERE id < 0
                ORDER BY condition_code
                """,
                String.class))
        .containsExactly(
            "BEV-LOW-TEST",
            "BEV-NORMAL-TEST",
            "CARNIVAL-7-TEST",
            "FCEV-TEST",
            "HEV-TEST",
            "ICE-TEST",
            "K5-ICE-TEST");

    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT condition_code || ':' || wheel_size_inch || ':' || built_in_camera
                FROM car_certification
                WHERE id IN (-211, -212, -213, -216)
                ORDER BY condition_code
                """,
                String.class))
        .containsExactly(
            "BEV-LOW-TEST:19.0:false",
            "BEV-NORMAL-TEST:20.0:false",
            "CARNIVAL-7-TEST:18.0:true",
            "ICE-TEST:19.0:false");

    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT configuration.id) = 6
                   AND COUNT(DISTINCT variant.id) = 6
                   AND COUNT(DISTINCT powertrain.id) = 5
                   AND COUNT(DISTINCT model_year.id) = 4
                   AND COUNT(DISTINCT generation.id) = 4
                   AND COUNT(DISTINCT model.id) = 4
                   AND COUNT(DISTINCT brand.id) = 1
                FROM car_configuration configuration
                JOIN car_vehicle_variant variant ON variant.id = configuration.vehicle_variant_id
                JOIN car_powertrain powertrain ON powertrain.id = variant.powertrain_id
                JOIN car_model_year model_year ON model_year.id = variant.model_year_id
                JOIN car_generation generation ON generation.id = model_year.generation_id
                JOIN car_model model ON model.id = generation.model_id
                JOIN car_brand brand ON brand.id = model.brand_id
                WHERE configuration.id < 0
                """,
                Boolean.class))
        .isTrue();

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT position FROM car_powertrain_component WHERE powertrain_id = -142 AND component_type = 'MOTOR' ORDER BY position",
                String.class))
        .containsExactly("FRONT", "REAR");
    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT seating_capacity || ':' || roof_type || ':' || wheel_size_inch
                FROM car_vehicle_variant
                WHERE model_year_id = -131
                ORDER BY seating_capacity
                """,
                String.class))
        .containsExactly("7:HIGH_ROOF:18.0", "9:STANDARD:19.0");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT warranties -> 0 ->> 'name' FROM car_configuration WHERE id = -183",
                String.class))
        .isEqualTo("연료전지");

    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT capacity_kwh = 84
                   AND capacity_ah = 230
                   AND voltage_v = 697
                   AND manufacturer_name = 'PACK-TEST'
                   AND cell_manufacturer_name = 'CELL-TEST'
                   AND production_country_code = 'KR'
                   AND cell_type = 'POUCH'
                   AND chemistry = 'NCM'
                FROM car_powertrain_component
                WHERE id = -157
                """,
                Boolean.class))
        .isTrue();

    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT CASE
                    WHEN as_of < sales_started_on THEN 'UPCOMING'
                    WHEN sales_ended_on IS NULL OR as_of < sales_ended_on THEN 'ON_SALE'
                    ELSE 'DISCONTINUED'
                END
                FROM car_configuration
                CROSS JOIN (VALUES
                    ('2025-12-31'::DATE), ('2026-01-01'::DATE), ('2027-01-01'::DATE)
                ) dates(as_of)
                WHERE id = -180
                ORDER BY as_of
                """,
                String.class))
        .containsExactly("UPCOMING", "ON_SALE", "DISCONTINUED");
  }

  /** variant는 같은 model year가 소유한 powertrain만 참조할 수 있습니다. */
  @Test
  void rejectsPowertrainFromAnotherModelYear() {
    long firstModelYearId = createMinimalModelYear("FIRST");
    long secondModelYearId = createMinimalModelYear("SECOND");
    long powertrainId = createMinimalPowertrain(firstModelYearId, "ICE");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO car_vehicle_variant (
                        public_id, model_year_id, powertrain_id, code, name, body_type,
                        seating_capacity, length_mm, width_mm, height_mm, wheelbase_mm
                    ) VALUES (?, ?, ?, ?, ?, 'SEDAN', 5, 4800, 1850, 1450, 2850)
                    """,
                    UUID.randomUUID(),
                    secondModelYearId,
                    powertrainId,
                    "CROSS-YEAR-" + UUID.randomUUID(),
                    "잘못된 연식 연결"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** 필수값 일부만 입력한 충전 조건, 장치 연료, 전기 인증 조건은 NULL 우회 없이 거부합니다. */
  @Test
  void rejectsIncompleteConditionalValues() {
    long modelYearId = createMinimalModelYear("CONDITIONAL");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO car_powertrain (
                        public_id, model_year_id, code, name, propulsion_type,
                        primary_fuel_type, drivetrain_type, fast_charge_minutes
                    ) VALUES (?, ?, ?, '잘못된 충전 조건', 'BEV', 'ELECTRICITY', 'RWD', 18)
                    """,
                    UUID.randomUUID(),
                    modelYearId,
                    "INVALID-CHARGE-" + UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);

    long powertrainId = createMinimalPowertrain(modelYearId, "ICE");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO car_powertrain_component (
                        powertrain_id, component_type, component_role, name, displacement_cc
                    ) VALUES (?, 'ENGINE', 'PRIMARY', '연료 없는 엔진', 1999)
                    """,
                    powertrainId))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO car_powertrain_component (
                        powertrain_id, component_type, component_role, name,
                        fuel_type, displacement_cc, aspiration_type
                    ) VALUES (?, 'ENGINE', 'PRIMARY', '잘못된 흡기 엔진', 'GASOLINE', 1999, 'UNKNOWN')
                    """,
                    powertrainId))
        .isInstanceOf(DataIntegrityViolationException.class);

    long variantId = createMinimalVariant(modelYearId, powertrainId);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO car_certification (
                        vehicle_variant_id, condition_code, condition_name, certification_type,
                        combined_efficiency, efficiency_unit, combined_range_km
                    ) VALUES (?, ?, '온도 조건 없는 전기 인증', 'ELECTRIC', 5.0, 'KM_PER_KWH', 400)
                    """,
                    variantId,
                    "INVALID-ELECTRIC-" + UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** 업무 식별자 중복, enum 밖의 값, 역전 기간·가격과 장치 전용 칼럼 혼용을 거부합니다. */
  @Test
  void rejectsRepresentativeConstraintViolations() {
    String duplicateCode = "DUPLICATE-" + UUID.randomUUID().toString().substring(0, 8);
    jdbcTemplate.update(
        "INSERT INTO car_brand (public_id, code, name_ko, country_code) VALUES (?, ?, '첫 브랜드', 'KR')",
        UUID.randomUUID(),
        duplicateCode);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO car_brand (public_id, code, name_ko, country_code) VALUES (?, ?, '중복 브랜드', 'KR')",
                    UUID.randomUUID(),
                    duplicateCode))
        .isInstanceOf(DataIntegrityViolationException.class);

    long modelYearId = createMinimalModelYear("CONSTRAINT");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO car_powertrain (
                        public_id, model_year_id, code, name, propulsion_type,
                        primary_fuel_type, drivetrain_type
                    ) VALUES (?, ?, ?, '잘못된 동력 형식', 'STEAM', 'OTHER', 'FWD')
                    """,
                    UUID.randomUUID(),
                    modelYearId,
                    "INVALID-" + UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);

    long powertrainId = createMinimalPowertrain(modelYearId, "ICE");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO car_powertrain_component (
                        powertrain_id, component_type, component_role, name
                    ) VALUES (?, 'BATTERY', 'ENERGY_STORAGE', '용량 없는 배터리')
                    """,
                    powertrainId))
        .isInstanceOf(DataIntegrityViolationException.class);

    long variantId = createMinimalVariant(modelYearId, powertrainId);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO car_configuration (
                        public_id, vehicle_variant_id, code, trim_name,
                        sales_started_on, sales_ended_on
                    ) VALUES (?, ?, ?, '역전 기간', '2026-12-31', '2026-01-01')
                    """,
                    UUID.randomUUID(),
                    variantId,
                    "INVALID-PERIOD-" + UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);

    long configurationId = createMinimalConfiguration(variantId);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO car_configuration_price (
                        configuration_id, effective_from,
                        price_before_tax_benefit, price_after_tax_benefit
                    ) VALUES (?, '2026-01-01', 30000000, 31000000)
                    """,
                    configurationId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** 기준일 가격과 옵션은 미래 이력을 제외한 뒤 가장 최근 행 하나만 선택합니다. */
  @Test
  void selectsEffectivePriceAndSelectableOptionByAsOfDate() {
    long modelYearId = createMinimalModelYear("TEMPORAL");
    long powertrainId = createMinimalPowertrain(modelYearId, "ICE");
    long variantId = createMinimalVariant(modelYearId, powertrainId);
    long configurationId = createMinimalConfiguration(variantId);

    jdbcTemplate.update(
        """
        INSERT INTO car_configuration_price (
            configuration_id, effective_from, price_before_tax_benefit
        ) VALUES (?, '2026-01-01', 30000000), (?, '2026-07-01', 32000000)
        """,
        configurationId,
        configurationId);
    jdbcTemplate.update(
        """
        INSERT INTO car_configuration_option (
            configuration_id, code, name, option_type, is_selectable,
            price_before_tax_benefit, effective_from
        ) VALUES
            (?, 'SUNROOF', '선루프', 'SINGLE', TRUE, 1000000, '2026-01-01'),
            (?, 'SUNROOF', '선루프', 'SINGLE', FALSE, NULL, '2026-08-01')
        """,
        configurationId,
        configurationId);

    assertThat(findEffectivePrice(configurationId, "2026-06-30")).isEqualTo(30000000L);
    assertThat(findEffectivePrice(configurationId, "2026-07-01")).isEqualTo(32000000L);
    assertThat(findSelectableOptionCodes(configurationId, "2026-07-31")).containsExactly("SUNROOF");
    assertThat(findSelectableOptionCodes(configurationId, "2026-08-01")).isEmpty();
  }

  private long createMinimalModelYear(String label) {
    String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
    Long brandId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO car_brand (public_id, code, name_ko, country_code)
            VALUES (?, ?, ?, 'KR') RETURNING id
            """,
            Long.class,
            UUID.randomUUID(),
            "BRAND-" + suffix,
            "브랜드 " + label);
    Long modelId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO car_model (public_id, brand_id, code, name_ko, name_normalized)
            VALUES (?, ?, ?, ?, ?) RETURNING id
            """,
            Long.class,
            UUID.randomUUID(),
            brandId,
            "MODEL-" + suffix,
            "모델 " + label,
            "model-" + suffix.toLowerCase());
    Long generationId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO car_generation (
                public_id, model_id, code, name, generation_order, change_type
            ) VALUES (?, ?, ?, ?, 1, 'FULL_CHANGE') RETURNING id
            """,
            Long.class,
            UUID.randomUUID(),
            modelId,
            "GEN-" + suffix,
            "세대 " + label);
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO car_model_year (
            public_id, generation_id, model_year, edition_code, display_name, sales_started_on
        ) VALUES (?, ?, 2026, ?, ?, '2026-01-01') RETURNING id
        """,
        Long.class,
        UUID.randomUUID(),
        generationId,
        "EDITION-" + suffix,
        "연식 " + label);
  }

  private long createMinimalPowertrain(long modelYearId, String propulsionType) {
    String primaryFuelType = propulsionType.equals("BEV") ? "ELECTRICITY" : "GASOLINE";
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO car_powertrain (
            public_id, model_year_id, code, name, propulsion_type,
            primary_fuel_type, drivetrain_type
        ) VALUES (?, ?, ?, ?, ?, ?, 'FWD') RETURNING id
        """,
        Long.class,
        UUID.randomUUID(),
        modelYearId,
        "POWERTRAIN-" + UUID.randomUUID(),
        "동력계통 " + propulsionType,
        propulsionType,
        primaryFuelType);
  }

  private long createMinimalVariant(long modelYearId, long powertrainId) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO car_vehicle_variant (
            public_id, model_year_id, powertrain_id, code, name, body_type,
            seating_capacity, length_mm, width_mm, height_mm, wheelbase_mm
        ) VALUES (?, ?, ?, ?, '기술 변형', 'SEDAN', 5, 4800, 1850, 1450, 2850)
        RETURNING id
        """,
        Long.class,
        UUID.randomUUID(),
        modelYearId,
        powertrainId,
        "VARIANT-" + UUID.randomUUID());
  }

  private long createMinimalConfiguration(long variantId) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO car_configuration (
            public_id, vehicle_variant_id, code, trim_name,
            sales_started_on, sales_ended_on, is_published
        ) VALUES (?, ?, ?, '테스트 트림', '2026-01-01', '2027-01-01', FALSE)
        RETURNING id
        """,
        Long.class,
        UUID.randomUUID(),
        variantId,
        "CONFIGURATION-" + UUID.randomUUID());
  }

  private long findEffectivePrice(long configurationId, String asOf) {
    return jdbcTemplate.queryForObject(
        """
        SELECT price_before_tax_benefit
        FROM car_configuration_price
        WHERE configuration_id = ?
          AND effective_from <= ?::DATE
        ORDER BY effective_from DESC
        LIMIT 1
        """,
        Long.class,
        configurationId,
        asOf);
  }

  private List<String> findSelectableOptionCodes(long configurationId, String asOf) {
    return jdbcTemplate.queryForList(
        """
        SELECT code
        FROM (
            SELECT DISTINCT ON (code) code, is_selectable
            FROM car_configuration_option
            WHERE configuration_id = ?
              AND effective_from <= ?::DATE
            ORDER BY code, effective_from DESC
        ) latest_option
        WHERE is_selectable
        ORDER BY code
        """,
        String.class,
        configurationId,
        asOf);
  }

  private static final String REPRESENTATIVE_CATALOG_FIXTURE =
      """
      -- 공식 수치가 아닌 관계 형태 검증용 fixture입니다.
      INSERT INTO car_brand (id, public_id, code, name_ko, country_code)
      VALUES (-100, '00000000-0000-0000-0000-000000000100', 'KOREAN-BRANDS-TEST', '국산 브랜드 테스트', 'KR');

      INSERT INTO car_model (id, public_id, brand_id, code, name_ko, name_normalized)
      VALUES
          (-110, '00000000-0000-0000-0000-000000000110', -100, 'K5-TEST', 'K5 테스트', 'k5-test'),
          (-111, '00000000-0000-0000-0000-000000000111', -100, 'CARNIVAL-TEST', '카니발 테스트', 'carnival-test'),
          (-112, '00000000-0000-0000-0000-000000000112', -100, 'EV6-TEST', 'EV6 테스트', 'ev6-test'),
          (-113, '00000000-0000-0000-0000-000000000113', -100, 'NEXO-TEST', '넥쏘 테스트', 'nexo-test');

      INSERT INTO car_generation (id, public_id, model_id, code, name, generation_order, change_type)
      SELECT id - 10, public_id, id, code || '-GEN', name_ko || ' 세대', 1, 'FULL_CHANGE'
      FROM car_model WHERE id BETWEEN -113 AND -110;

      INSERT INTO car_model_year (id, public_id, generation_id, model_year, edition_code, display_name, sales_started_on, sales_ended_on)
      SELECT id - 10, public_id, id, 2026, '2026-TEST', name || ' 2026', '2026-01-01', '2027-01-01'
      FROM car_generation WHERE id BETWEEN -123 AND -120;

      INSERT INTO car_powertrain (
          id, public_id, model_year_id, code, name, propulsion_type, primary_fuel_type,
          drivetrain_type, transmission_type, transmission_gears,
          fast_charge_minutes, fast_charge_from_percent, fast_charge_to_percent
      ) VALUES
          (-140, '00000000-0000-0000-0000-000000000140', -130, 'HEV-TEST', '하이브리드 테스트', 'HEV', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, NULL, NULL, NULL),
          (-141, '00000000-0000-0000-0000-000000000141', -131, 'ICE-TEST', '내연기관 테스트', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 8, NULL, NULL, NULL),
          (-142, '00000000-0000-0000-0000-000000000142', -132, 'BEV-TEST', '전기 테스트', 'BEV', 'ELECTRICITY', 'RWD', 'REDUCTION_GEAR', 1, 18, 10, 80),
          (-143, '00000000-0000-0000-0000-000000000143', -133, 'FCEV-TEST', '수소전기 테스트', 'FCEV', 'HYDROGEN', 'FWD', 'REDUCTION_GEAR', 1, NULL, NULL, NULL),
          (-144, '00000000-0000-0000-0000-000000000144', -130, 'K5-ICE-TEST', 'K5 내연기관 테스트', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 8, NULL, NULL, NULL);

      INSERT INTO car_powertrain_component (
          id, powertrain_id, component_type, component_role, position, name, fuel_type,
          displacement_cc, max_power_kw, capacity_kwh, capacity_l, capacity_kg, pressure_bar, sort_order
      ) VALUES
          (-150, -140, 'ENGINE', 'PRIMARY', 'FRONT', 'HEV 엔진', 'GASOLINE', 1999, 112, NULL, NULL, NULL, NULL, 0),
          (-151, -140, 'MOTOR', 'TRACTION', 'FRONT', 'HEV 모터', NULL, NULL, 38, NULL, NULL, NULL, NULL, 1),
          (-152, -140, 'BATTERY', 'ENERGY_STORAGE', 'CENTER', 'HEV 배터리', NULL, NULL, NULL, 1.5, NULL, NULL, NULL, 2),
          (-153, -140, 'FUEL_TANK', 'FUEL_STORAGE', 'REAR', 'HEV 연료탱크', 'GASOLINE', NULL, NULL, NULL, 50, NULL, NULL, 3),
          (-154, -141, 'ENGINE', 'PRIMARY', 'FRONT', 'ICE 엔진', 'GASOLINE', 3470, 216, NULL, NULL, NULL, NULL, 0),
          (-155, -141, 'FUEL_TANK', 'FUEL_STORAGE', 'REAR', 'ICE 연료탱크', 'GASOLINE', NULL, NULL, NULL, 72, NULL, NULL, 1),
          (-156, -142, 'MOTOR', 'TRACTION', 'REAR', 'BEV 모터', NULL, NULL, 168, NULL, NULL, NULL, NULL, 0),
          (-157, -142, 'BATTERY', 'ENERGY_STORAGE', 'CENTER', 'BEV 배터리', NULL, NULL, NULL, 84, NULL, NULL, NULL, 1),
          (-158, -143, 'MOTOR', 'TRACTION', 'FRONT', 'FCEV 모터', NULL, NULL, 120, NULL, NULL, NULL, NULL, 0),
          (-159, -143, 'BATTERY', 'ENERGY_STORAGE', 'CENTER', 'FCEV 배터리', NULL, NULL, NULL, 1.6, NULL, NULL, NULL, 1),
          (-160, -143, 'FUEL_CELL', 'PRIMARY', 'FRONT', '연료전지 스택', NULL, NULL, 95, NULL, NULL, NULL, NULL, 2),
          (-161, -143, 'HYDROGEN_TANK', 'FUEL_STORAGE', 'REAR', '수소탱크', NULL, NULL, NULL, NULL, NULL, 6.3, 700, 3),
          (-162, -144, 'ENGINE', 'PRIMARY', 'FRONT', 'K5 ICE 엔진', 'GASOLINE', 1999, 118, NULL, NULL, NULL, NULL, 0),
          (-163, -144, 'FUEL_TANK', 'FUEL_STORAGE', 'REAR', 'K5 ICE 연료탱크', 'GASOLINE', NULL, NULL, NULL, 50, NULL, NULL, 1),
          (-164, -142, 'MOTOR', 'TRACTION', 'FRONT', 'BEV 전륜 모터', NULL, NULL, 74, NULL, NULL, NULL, NULL, 1);

      UPDATE car_powertrain_component
      SET capacity_ah = 230,
          voltage_v = 697,
          manufacturer_name = 'PACK-TEST',
          cell_manufacturer_name = 'CELL-TEST',
          production_country_code = 'KR',
          cell_type = 'POUCH',
          chemistry = 'NCM'
      WHERE id = -157;

      INSERT INTO car_vehicle_variant (
          id, public_id, model_year_id, powertrain_id, code, name, body_type, roof_type,
          seating_capacity, length_mm, width_mm, height_mm, wheelbase_mm, wheel_size_inch
      ) VALUES
          (-170, '00000000-0000-0000-0000-000000000170', -130, -140, 'K5-HEV-TEST', 'K5 HEV 테스트', 'SEDAN', 'STANDARD', 5, 4905, 1860, 1445, 2850, 18),
          (-171, '00000000-0000-0000-0000-000000000171', -131, -141, 'CARNIVAL-ICE-TEST', '카니발 9인승 테스트', 'MPV', 'STANDARD', 9, 5155, 1995, 1775, 3090, 19),
          (-172, '00000000-0000-0000-0000-000000000172', -132, -142, 'EV6-BEV-TEST', 'EV6 테스트', 'SUV', 'STANDARD', 5, 4695, 1890, 1550, 2900, 20),
          (-173, '00000000-0000-0000-0000-000000000173', -133, -143, 'NEXO-FCEV-TEST', '넥쏘 테스트', 'SUV', 'STANDARD', 5, 4670, 1860, 1640, 2790, 19),
          (-174, '00000000-0000-0000-0000-000000000174', -130, -144, 'K5-ICE-TEST', 'K5 ICE 테스트', 'SEDAN', 'STANDARD', 5, 4905, 1860, 1445, 2850, 17),
          (-175, '00000000-0000-0000-0000-000000000175', -131, -141, 'CARNIVAL-7-HIGH-TEST', '카니발 7인승 하이루프 테스트', 'MPV', 'HIGH_ROOF', 7, 5155, 1995, 2045, 3090, 18);

      INSERT INTO car_configuration (
          id, public_id, vehicle_variant_id, code, trim_name, sales_started_on, sales_ended_on,
          is_published, warranties
      ) VALUES
          (-180, '00000000-0000-0000-0000-000000000180', -170, 'K5-TRIM-TEST', 'K5 트림 테스트', '2026-01-01', '2027-01-01', TRUE, '[]'),
          (-181, '00000000-0000-0000-0000-000000000181', -171, 'CARNIVAL-TRIM-TEST', '카니발 트림 테스트', '2026-01-01', '2027-01-01', TRUE, '[]'),
          (-182, '00000000-0000-0000-0000-000000000182', -172, 'EV6-TRIM-TEST', 'EV6 트림 테스트', '2026-01-01', '2027-01-01', TRUE, '[{"name":"배터리","years":10}]'),
          (-183, '00000000-0000-0000-0000-000000000183', -173, 'NEXO-TRIM-TEST', '넥쏘 트림 테스트', '2026-01-01', '2027-01-01', TRUE, '[{"name":"연료전지","years":10,"distanceKm":160000}]'),
          (-184, '00000000-0000-0000-0000-000000000184', -174, 'K5-ICE-TRIM-TEST', 'K5 ICE 트림 테스트', '2026-01-01', '2027-01-01', TRUE, '[]'),
          (-185, '00000000-0000-0000-0000-000000000185', -175, 'CARNIVAL-7-TRIM-TEST', '카니발 7인승 트림 테스트', '2026-01-01', '2027-01-01', TRUE, '[]');

      INSERT INTO car_configuration_price (id, configuration_id, effective_from, price_before_tax_benefit, price_after_tax_benefit)
      VALUES (-190, -180, '2026-01-01', 38000000, 36000000), (-191, -180, '2026-07-01', 38500000, 36500000);

      INSERT INTO car_configuration_option (
          id, configuration_id, code, name, option_type, is_selectable,
          price_before_tax_benefit, price_after_tax_benefit, effective_from
      ) VALUES
          (-200, -180, 'ADAS-TEST', '주행 보조 패키지', 'PACKAGE', TRUE, 1200000, 1200000, '2026-01-01'),
          (-201, -180, 'ADAS-TEST', '주행 보조 패키지', 'PACKAGE', FALSE, NULL, NULL, '2026-08-01');

      INSERT INTO car_certification (
          id, vehicle_variant_id, condition_code, condition_name, certification_type,
          temperature_condition, wheel_size_inch, built_in_camera,
          combined_efficiency, efficiency_unit, combined_range_km, co2_g_per_km
      ) VALUES
          (-210, -170, 'HEV-TEST', '하이브리드 인증 테스트', 'LIQUID_FUEL', NULL, 18, FALSE, 18.1, 'KM_PER_L', NULL, 88),
          (-211, -171, 'ICE-TEST', '내연기관 인증 테스트', 'LIQUID_FUEL', NULL, 19, FALSE, 9.0, 'KM_PER_L', NULL, 190),
          (-212, -172, 'BEV-NORMAL-TEST', '전기 20인치 상온 테스트', 'ELECTRIC', 'NORMAL', 20, FALSE, 4.8, 'KM_PER_KWH', 470, NULL),
          (-213, -172, 'BEV-LOW-TEST', '전기 19인치 저온 테스트', 'ELECTRIC', 'LOW', 19, FALSE, NULL, 'KM_PER_KWH', 330, NULL),
          (-214, -173, 'FCEV-TEST', '수소 인증 테스트', 'HYDROGEN', NULL, 19, FALSE, 96.2, 'KM_PER_KG', 600, NULL),
          (-215, -174, 'K5-ICE-TEST', 'K5 내연기관 인증 테스트', 'LIQUID_FUEL', NULL, 17, FALSE, 12.5, 'KM_PER_L', NULL, 130),
          (-216, -175, 'CARNIVAL-7-TEST', '카니발 7인승 18인치 빌트인 카메라 테스트', 'LIQUID_FUEL', NULL, 18, TRUE, 9.5, 'KM_PER_L', NULL, 180);
      """;

  private static final Set<String> EXPECTED_CONSTRAINTS =
      Set.of(
          """
          pk_car_brand
          uk_car_brand_public_id
          uk_car_brand_code
          chk_car_brand_code
          chk_car_brand_name_ko
          chk_car_brand_name_en
          chk_car_brand_country_code
          chk_car_brand_homepage_url
          chk_car_brand_display_order
          chk_car_brand_updated_at
          pk_car_model
          uk_car_model_public_id
          uk_car_model_brand_code
          uk_car_model_brand_name_normalized
          fk_car_model_brand
          chk_car_model_code
          chk_car_model_name_ko
          chk_car_model_name_en
          chk_car_model_name_normalized
          chk_car_model_display_order
          chk_car_model_updated_at
          pk_car_generation
          uk_car_generation_public_id
          uk_car_generation_model_code
          uk_car_generation_model_order
          fk_car_generation_model
          chk_car_generation_code
          chk_car_generation_name
          chk_car_generation_order
          chk_car_generation_change_type
          chk_car_generation_updated_at
          pk_car_model_year
          uk_car_model_year_public_id
          uk_car_model_year_generation_market_year_edition
          fk_car_model_year_generation
          chk_car_model_year_market_code
          chk_car_model_year_year
          chk_car_model_year_edition_code
          chk_car_model_year_display_name
          chk_car_model_year_sales_period
          chk_car_model_year_updated_at
          pk_car_powertrain
          uk_car_powertrain_public_id
          uk_car_powertrain_model_year_code
          uk_car_powertrain_model_year_id
          fk_car_powertrain_model_year
          chk_car_powertrain_code
          chk_car_powertrain_name
          chk_car_powertrain_propulsion_type
          chk_car_powertrain_primary_fuel_type
          chk_car_powertrain_secondary_fuel_type
          chk_car_powertrain_energy_combination
          chk_car_powertrain_drivetrain_type
          chk_car_powertrain_transmission_type
          chk_car_powertrain_transmission_gears
          chk_car_powertrain_positive_values
          chk_car_powertrain_fast_charge
          chk_car_powertrain_updated_at
          pk_car_powertrain_component
          uk_car_powertrain_component_identity
          fk_car_powertrain_component_powertrain
          chk_car_powertrain_component_type
          chk_car_powertrain_component_role
          chk_car_powertrain_component_position
          chk_car_powertrain_component_name
          chk_car_powertrain_component_fuel_type
          chk_car_powertrain_component_aspiration_type
          chk_car_powertrain_component_positive_values
          chk_car_powertrain_component_torque_rpm
          chk_car_powertrain_component_production_country
          chk_car_powertrain_component_sort_order
          chk_car_powertrain_component_type_values
          chk_car_powertrain_component_updated_at
          pk_car_vehicle_variant
          uk_car_vehicle_variant_public_id
          uk_car_vehicle_variant_model_year_code
          fk_car_vehicle_variant_model_year
          fk_car_vehicle_variant_model_year_powertrain
          chk_car_vehicle_variant_code
          chk_car_vehicle_variant_name
          chk_car_vehicle_variant_body_type
          chk_car_vehicle_variant_roof_type
          chk_car_vehicle_variant_positive_dimensions
          chk_car_vehicle_variant_cargo_capacity
          chk_car_vehicle_variant_weight
          chk_car_vehicle_variant_updated_at
          pk_car_configuration
          uk_car_configuration_public_id
          uk_car_configuration_variant_channel_code
          fk_car_configuration_vehicle_variant
          chk_car_configuration_code
          chk_car_configuration_trim_name
          chk_car_configuration_sales_channel
          chk_car_configuration_sales_period
          chk_car_configuration_display_order
          chk_car_configuration_standard_features
          chk_car_configuration_warranties
          chk_car_configuration_safety_ratings
          chk_car_configuration_media
          chk_car_configuration_updated_at
          pk_car_configuration_price
          uk_car_configuration_price_effective_from
          fk_car_configuration_price_configuration
          chk_car_configuration_price_currency_code
          chk_car_configuration_price_amount
          chk_car_configuration_price_source_label
          chk_car_configuration_price_updated_at
          pk_car_configuration_option
          uk_car_configuration_option_code_effective_from
          fk_car_configuration_option_configuration
          chk_car_configuration_option_code
          chk_car_configuration_option_name
          chk_car_configuration_option_type
          chk_car_configuration_option_category
          chk_car_configuration_option_selectable_price
          chk_car_configuration_option_currency_code
          chk_car_configuration_option_included_items
          chk_car_configuration_option_display_order
          chk_car_configuration_option_updated_at
          pk_car_certification
          uk_car_certification_variant_condition
          fk_car_certification_vehicle_variant
          chk_car_certification_condition_code
          chk_car_certification_condition_name
          chk_car_certification_type
          chk_car_certification_temperature
          chk_car_certification_positive_values
          chk_car_certification_measurement
          chk_car_certification_updated_at
          """
              .strip()
              .split("\\R"));
}
