package com.ronams.carmunity.catalog.internal.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogReadPort;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 공개 카탈로그 query adapter가 실제 PostgreSQL의 공개·기준일 계약을 지키는지 검증합니다. */
@SpringBootTest
@Transactional
class CatalogQueryPersistenceAdapterTest extends PostgreSqlIntegrationTestSupport {

  private static final LocalDate AS_OF = LocalDate.of(2026, 8, 29);

  @Autowired private CatalogReadPort catalogReadPort;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** 기준일에 판매 중인 공개 configuration이 있는 브랜드만 모델 수와 함께 반환합니다. */
  @Test
  void listsOnlyBrandsWithPublishedOnSaleModels() {
    CatalogFixture published = createPublishedIceModel("ALPHA", "알파", "A1", "알파원", 20_000_000L);
    createUnpublishedModel("ALPHA", "A2", "알파투");
    createPublishedIceModel("BETA", "베타", "B1", "베타원", 30_000_000L);
    jdbcTemplate.update("UPDATE car_brand SET display_order = 1 WHERE code = 'ALPHA'");
    jdbcTemplate.update("UPDATE car_brand SET display_order = 2 WHERE code = 'BETA'");

    List<CatalogBrandSummary> brands = catalogReadPort.listBrands(AS_OF);

    assertThat(brands)
        .extracting(
            CatalogBrandSummary::brandId,
            CatalogBrandSummary::name,
            CatalogBrandSummary::onSaleModelCount)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(published.brandPublicId(), "알파", 1L),
            org.assertj.core.groups.Tuple.tuple(findBrandPublicId("BETA"), "베타", 1L));
  }

  private CatalogFixture createPublishedIceModel(
      String brandCode, String brandName, String modelCode, String modelName, long price) {
    long brandId = findOrCreateBrand(brandCode, brandName);
    UUID brandPublicId = findBrandPublicId(brandCode);
    long modelId = createModel(brandId, modelCode, modelName, modelName.toLowerCase());
    long modelYearId = createModelYear(modelId, modelCode);
    long powertrainId = createIcePowertrain(modelYearId, modelCode);
    long variantId = createVariant(modelYearId, powertrainId, modelCode);
    long configurationId = createConfiguration(variantId, modelCode, true);
    jdbcTemplate.update(
        "INSERT INTO car_configuration_price (configuration_id, effective_from, price_before_tax_benefit) VALUES (?, '2026-01-01', ?)",
        configurationId,
        price);
    return new CatalogFixture(brandPublicId);
  }

  private void createUnpublishedModel(String brandCode, String modelCode, String modelName) {
    long brandId = findOrCreateBrand(brandCode, brandCode);
    long modelId = createModel(brandId, modelCode, modelName, modelName.toLowerCase());
    long modelYearId = createModelYear(modelId, modelCode);
    long powertrainId = createIcePowertrain(modelYearId, modelCode);
    long variantId = createVariant(modelYearId, powertrainId, modelCode);
    createConfiguration(variantId, modelCode, false);
  }

  private long findOrCreateBrand(String code, String name) {
    List<Long> existing =
        jdbcTemplate.queryForList("SELECT id FROM car_brand WHERE code = ?", Long.class, code);
    if (!existing.isEmpty()) {
      return existing.getFirst();
    }
    return jdbcTemplate.queryForObject(
        "INSERT INTO car_brand (public_id, code, name_ko, country_code) VALUES (?, ?, ?, 'KR') RETURNING id",
        Long.class,
        UUID.randomUUID(),
        code,
        name);
  }

  private UUID findBrandPublicId(String code) {
    return jdbcTemplate.queryForObject(
        "SELECT public_id FROM car_brand WHERE code = ?", UUID.class, code);
  }

  private long createModel(long brandId, String code, String name, String normalizedName) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO car_model (public_id, brand_id, code, name_ko, name_normalized) VALUES (?, ?, ?, ?, ?) RETURNING id",
        Long.class,
        UUID.randomUUID(),
        brandId,
        code,
        name,
        normalizedName);
  }

  private long createModelYear(long modelId, String suffix) {
    long generationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO car_generation (public_id, model_id, code, name, generation_order, change_type) VALUES (?, ?, ?, ?, 1, 'FULL_CHANGE') RETURNING id",
            Long.class,
            UUID.randomUUID(),
            modelId,
            suffix + "-GEN",
            suffix + " 세대");
    return jdbcTemplate.queryForObject(
        "INSERT INTO car_model_year (public_id, generation_id, model_year, edition_code, display_name, released_on, sales_started_on, sales_ended_on) VALUES (?, ?, 2026, ?, ?, '2026-01-01', '2026-01-01', '2027-01-01') RETURNING id",
        Long.class,
        UUID.randomUUID(),
        generationId,
        suffix + "-2026",
        suffix + " 2026");
  }

  private long createIcePowertrain(long modelYearId, String suffix) {
    long powertrainId =
        jdbcTemplate.queryForObject(
            "INSERT INTO car_powertrain (public_id, model_year_id, code, name, propulsion_type, primary_fuel_type, drivetrain_type) VALUES (?, ?, ?, '가솔린', 'ICE', 'GASOLINE', 'FWD') RETURNING id",
            Long.class,
            UUID.randomUUID(),
            modelYearId,
            suffix + "-ICE");
    jdbcTemplate.update(
        "INSERT INTO car_powertrain_component (powertrain_id, component_type, component_role, name, fuel_type, displacement_cc) VALUES (?, 'ENGINE', 'PRIMARY', '엔진', 'GASOLINE', 1999)",
        powertrainId);
    jdbcTemplate.update(
        "INSERT INTO car_powertrain_component (powertrain_id, component_type, component_role, name, fuel_type, capacity_l) VALUES (?, 'FUEL_TANK', 'FUEL_STORAGE', '연료탱크', 'GASOLINE', 50)",
        powertrainId);
    return powertrainId;
  }

  private long createVariant(long modelYearId, long powertrainId, String suffix) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO car_vehicle_variant (public_id, model_year_id, powertrain_id, code, name, body_type, seating_capacity, length_mm, width_mm, height_mm, wheelbase_mm) VALUES (?, ?, ?, ?, ?, 'SEDAN', 5, 4800, 1850, 1450, 2850) RETURNING id",
        Long.class,
        UUID.randomUUID(),
        modelYearId,
        powertrainId,
        suffix + "-VARIANT",
        suffix + " 변형");
  }

  private long createConfiguration(long variantId, String suffix, boolean published) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO car_configuration (public_id, vehicle_variant_id, code, trim_name, sales_started_on, sales_ended_on, is_published) VALUES (?, ?, ?, '기본', '2026-01-01', '2027-01-01', ?) RETURNING id",
        Long.class,
        UUID.randomUUID(),
        variantId,
        suffix + "-CONFIGURATION",
        published);
  }

  private record CatalogFixture(UUID brandPublicId) {}
}
