package com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelPage;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogBrandReadPort;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelDetailReadPort;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelSearchPort;
import com.ronams.carmunity.catalog.internal.application.query.CatalogModelSort;
import com.ronams.carmunity.catalog.internal.application.query.SearchCatalogModelsQuery;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 공개 카탈로그 query adapter가 실제 PostgreSQL의 공개·기준일 계약을 지키는지 검증합니다. */
@SpringBootTest
@Transactional
@Import(CatalogJooqReadAdapterTest.QueryObservationConfig.class)
class CatalogJooqReadAdapterTest extends PostgreSqlIntegrationTestSupport {

  private static final LocalDate AS_OF = LocalDate.of(2026, 8, 29);

  @Autowired private CatalogBrandReadPort catalogBrandReadPort;
  @Autowired private CatalogModelSearchPort catalogModelSearchPort;
  @Autowired private CatalogModelDetailReadPort catalogModelDetailReadPort;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private CatalogQueryProbe catalogQueryProbe;

  /** 기준일에 판매 중인 공개 configuration이 있는 브랜드만 모델 수와 함께 반환합니다. */
  @Test
  void listsOnlyBrandsWithPublishedOnSaleModels() {
    CatalogFixture published = createPublishedIceModel("ALPHA", "알파", "A1", "알파원", 20_000_000L);
    createUnpublishedModel("ALPHA", "A2", "알파투");
    createPublishedIceModel("BETA", "베타", "B1", "베타원", 30_000_000L);
    jdbcTemplate.update("UPDATE car_brand SET display_order = 1 WHERE code = 'ALPHA'");
    jdbcTemplate.update("UPDATE car_brand SET display_order = 2 WHERE code = 'BETA'");

    List<CatalogBrandSummary> brands = catalogBrandReadPort.list(AS_OF);

    assertThat(brands)
        .extracting(
            CatalogBrandSummary::brandId,
            CatalogBrandSummary::name,
            CatalogBrandSummary::onSaleModelCount)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(published.brandPublicId(), "알파", 1L),
            org.assertj.core.groups.Tuple.tuple(findBrandPublicId("BETA"), "베타", 1L),
            org.assertj.core.groups.Tuple.tuple(
                UUID.fromString("d43787ba-f8d6-5b47-8d99-28da57b4c810"), "기아", 1L));
  }

  /** 복합 filter와 기준일 가격을 같은 공개 configuration에 적용하고 모델을 한 번만 반환합니다. */
  @Test
  void searchesModelsWithSameConfigurationFiltersAndCurrentPrice() {
    CatalogFixture bev =
        createPublishedBevModel("KIA-TEST", "기아 테스트", "EV6", "EV6 100%_", 55_000_000L);
    createPublishedIceModel("KIA-TEST", "기아 테스트", "K5", "K5", 35_000_000L);
    jdbcTemplate.update(
        "INSERT INTO car_configuration_price (configuration_id, effective_from, price_before_tax_benefit, price_after_tax_benefit) VALUES (?, '2026-08-01', 54000000, 52000000)",
        bev.configurationId());

    SearchCatalogModelsQuery query =
        new SearchCatalogModelsQuery(
            "  ＥＶ6   100%_  ",
            bev.brandPublicId(),
            BodyType.SUV,
            PropulsionType.BEV,
            FuelType.ELECTRICITY,
            50_000_000L,
            53_000_000L,
            SalesStatus.ON_SALE,
            AS_OF,
            CatalogModelSort.PRICE_ASC,
            0,
            20);

    catalogQueryProbe.clear();
    CatalogModelPage page = catalogModelSearchPort.search(query);

    assertThat(page.totalElements()).isOne();
    assertThat(page.totalPages()).isOne();
    assertThat(page.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.modelId()).isEqualTo(bev.modelPublicId());
              assertThat(item.representativeModelYearId()).isEqualTo(bev.modelYearPublicId());
              assertThat(item.bodyTypes()).containsExactly(BodyType.SUV);
              assertThat(item.propulsionTypes()).containsExactly(PropulsionType.BEV);
              assertThat(item.fuelTypes()).containsExactly(FuelType.ELECTRICITY);
              assertThat(item.startPrice()).isEqualTo(52_000_000L);
              assertThat(item.priceBasis().name()).isEqualTo("AFTER_TAX_BENEFIT");
              assertThat(item.salesStatus()).isEqualTo(SalesStatus.ON_SALE);
            });
    assertThat(executedCatalogQueryCount()).isEqualTo(2);
    assertThat(modelPageExecutionPlan()).isNotEmpty();
  }

  /** 서로 다른 공개 configuration의 차체와 동력 형식은 한 모델 안에서도 조합하지 않습니다. */
  @Test
  void doesNotMixFiltersAcrossConfigurations() {
    CatalogFixture bev =
        createPublishedBevModel("KIA-TEST", "기아 테스트", "MIXED", "혼합 모델", 55_000_000L);
    long modelYearId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM car_model_year WHERE public_id = ?",
            Long.class,
            bev.modelYearPublicId());
    long icePowertrainId = createIcePowertrain(modelYearId, "MIXED-SECOND");
    long sedanVariantId = createVariant(modelYearId, icePowertrainId, "MIXED-SECOND");
    createConfiguration(sedanVariantId, "MIXED-SECOND", true);

    SearchCatalogModelsQuery impossibleCombination =
        new SearchCatalogModelsQuery(
            null,
            bev.brandPublicId(),
            BodyType.SUV,
            PropulsionType.ICE,
            FuelType.GASOLINE,
            null,
            null,
            SalesStatus.ON_SALE,
            AS_OF,
            CatalogModelSort.NAME_ASC,
            0,
            20);

    assertThat(catalogModelSearchPort.search(impossibleCombination).items()).isEmpty();
  }

  /** ALL 목록은 같은 연식의 판매 중·예정 configuration을 한 연식으로 합쳐 사양 집합을 보존합니다. */
  @Test
  void rollsUpAllConfigurationStatusesWithinRepresentativeModelYear() {
    CatalogFixture bev =
        createPublishedBevModel("KIA-TEST", "기아 테스트", "ALL-STATUS", "전체 상태", 55_000_000L);
    long modelYearId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM car_model_year WHERE public_id = ?",
            Long.class,
            bev.modelYearPublicId());
    long icePowertrainId = createIcePowertrain(modelYearId, "ALL-STATUS-SECOND");
    long sedanVariantId = createVariant(modelYearId, icePowertrainId, "ALL-STATUS-SECOND");
    long upcomingConfigurationId = createConfiguration(sedanVariantId, "ALL-STATUS-SECOND", true);
    jdbcTemplate.update(
        "UPDATE car_configuration SET sales_started_on = '2026-09-01', sales_ended_on = '2027-01-01' WHERE id = ?",
        upcomingConfigurationId);

    SearchCatalogModelsQuery allStatuses =
        new SearchCatalogModelsQuery(
            null,
            bev.brandPublicId(),
            null,
            null,
            null,
            null,
            null,
            SalesStatus.ALL,
            AS_OF,
            CatalogModelSort.NAME_ASC,
            0,
            20);

    assertThat(catalogModelSearchPort.search(allStatuses).items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.salesStatus()).isEqualTo(SalesStatus.ON_SALE);
              assertThat(item.bodyTypes()).containsExactlyInAnyOrder(BodyType.SUV, BodyType.SEDAN);
              assertThat(item.propulsionTypes())
                  .containsExactlyInAnyOrder(PropulsionType.BEV, PropulsionType.ICE);
              assertThat(item.fuelTypes())
                  .containsExactlyInAnyOrder(FuelType.ELECTRICITY, FuelType.GASOLINE);
            });
  }

  /** V18의 K5 실제 데이터가 공개 상세 조회에서 가격·옵션·인증까지 함께 노출됩니다. */
  @Test
  void readsSeededK5ThroughPublicCatalogPort() {
    CatalogModelDetail detail =
        catalogModelDetailReadPort
            .find(
                UUID.fromString("68ad71d8-fae9-54fb-8835-9b202d5e5eed"),
                UUID.fromString("9f90d6bf-d830-52e0-a377-53b08f110e71"),
                AS_OF)
            .orElseThrow();

    assertThat(detail.model().name()).isEqualTo("K5");
    assertThat(detail.selectedModelYear().modelYear()).isEqualTo(2027);
    assertThat(detail.powertrains())
        .filteredOn(
            powertrain ->
                powertrain
                    .powertrainId()
                    .equals(UUID.fromString("4a94ba5f-94fc-54d5-928d-1dc89384caef")))
        .singleElement()
        .satisfies(
            powertrain -> {
              assertThat(powertrain.name()).isEqualTo("스마트스트림 G2.0 가솔린");
              assertThat(powertrain.components())
                  .singleElement()
                  .extracting(CatalogModelDetail.PowertrainComponent::displacementCc)
                  .isEqualTo(1999);
            });
    assertThat(detail.variants())
        .filteredOn(
            variant ->
                variant.variantId().equals(UUID.fromString("77d72233-88a4-5c7d-bbf4-1a45f2ab0f38")))
        .singleElement()
        .satisfies(
            variant -> {
              assertThat(variant.certifications())
                  .singleElement()
                  .satisfies(
                      certification -> {
                        assertThat(certification.combinedEfficiency())
                            .isEqualByComparingTo("12.600");
                        assertThat(certification.co2GPerKm()).isEqualByComparingTo("132.00");
                      });
              assertThat(variant.configurations())
                  .filteredOn(
                      configuration ->
                          configuration
                              .configurationId()
                              .equals(UUID.fromString("7d836d76-88a1-5d9f-9da8-f7eb22c8c179")))
                  .singleElement()
                  .satisfies(
                      configuration -> {
                        assertThat(configuration.trimName()).isEqualTo("스마트 셀렉션");
                        assertThat(configuration.price().displayPrice()).isEqualTo(27_630_000L);
                        assertThat(configuration.standardFeatures()).hasSize(69);
                        assertThat(configuration.standardFeatures())
                            .extracting(CatalogModelDetail.StandardFeature::name)
                            .contains(
                                "스마트스트림 G2.0 엔진",
                                "9에어백(운전석/동승석 어드밴스드, 운전석 무릎, 전복감지 커튼, 앞좌석/뒷좌석 사이드)",
                                "C타입 USB 단자(데이터/충전 전환형)");
                        assertThat(configuration.options())
                            .extracting(CatalogModelDetail.Option::code)
                            .containsExactly("CLUSTER-PACK-12-3", "PANORAMA-SUNROOF");
                      });
            });
  }

  /** 선택 연식의 동력 장치·인증·판매 구성·현재 옵션을 고정된 묶음 조회로 조립합니다. */
  @Test
  void loadsCompleteModelDetailForSelectedModelYear() {
    CatalogFixture bev = createPublishedBevModel("KIA-TEST", "기아 테스트", "EV6", "EV6", 55_000_000L);
    long variantId =
        jdbcTemplate.queryForObject(
            "SELECT vehicle_variant_id FROM car_configuration WHERE id = ?",
            Long.class,
            bev.configurationId());
    jdbcTemplate.update(
        "UPDATE car_configuration SET standard_features = '[{\"category\":\"안전\",\"name\":\"충돌방지\",\"description\":null}]', warranties = '[{\"name\":\"배터리\",\"years\":10,\"distanceKm\":200000,\"note\":null}]', safety_ratings = '[{\"organization\":\"KNCAP\",\"rating\":\"1등급\",\"assessedOn\":\"2026-01-02\",\"note\":null}]', media = '[{\"type\":\"IMAGE\",\"url\":\"https://example.com/ev6.jpg\",\"alt\":\"EV6\",\"sortOrder\":0}]' WHERE id = ?",
        bev.configurationId());
    jdbcTemplate.update(
        "INSERT INTO car_certification (vehicle_variant_id, condition_code, condition_name, certification_type, certification_standard, temperature_condition, wheel_size_inch, combined_efficiency, efficiency_unit, combined_range_km) VALUES (?, 'NORMAL-20', '20인치 상온', 'ELECTRIC', 'KOREA', 'NORMAL', 20, 4.8, 'KM_PER_KWH', 470)",
        variantId);
    jdbcTemplate.update(
        "INSERT INTO car_configuration_option (configuration_id, code, name, option_type, is_selectable, price_before_tax_benefit, price_after_tax_benefit, included_items, effective_from, display_order) VALUES (?, 'ADAS', '주행 보조', 'PACKAGE', TRUE, 1200000, 1000000, '[\"후측방 충돌방지\"]', '2026-01-01', 1)",
        bev.configurationId());
    jdbcTemplate.update(
        "INSERT INTO car_configuration_option (configuration_id, code, name, option_type, is_selectable, included_items, effective_from, display_order) VALUES (?, 'OLD', '중단 옵션', 'SINGLE', FALSE, '[]', '2026-01-01', 2)",
        bev.configurationId());

    catalogQueryProbe.clear();
    CatalogModelDetail detail =
        catalogModelDetailReadPort
            .find(bev.modelPublicId(), bev.modelYearPublicId(), AS_OF)
            .orElseThrow();

    assertThat(detail.model().modelId()).isEqualTo(bev.modelPublicId());
    assertThat(detail.selectedModelYear().modelYearId()).isEqualTo(bev.modelYearPublicId());
    assertThat(detail.powertrains())
        .singleElement()
        .satisfies(
            powertrain -> {
              assertThat(powertrain.propulsionType()).isEqualTo(PropulsionType.BEV);
              assertThat(powertrain.acChargeConnector()).isEqualTo("TYPE_2");
              assertThat(powertrain.acChargeMinutes()).isEqualTo(455);
              assertThat(powertrain.acChargeFromPercent()).isEqualTo(10);
              assertThat(powertrain.acChargeToPercent()).isEqualTo(100);
              assertThat(powertrain.acChargePowerKw()).isEqualByComparingTo("11");
              assertThat(powertrain.dcChargeConnector()).isEqualTo("CCS_COMBO_2");
              assertThat(powertrain.fastChargePowerKw()).isEqualByComparingTo("350");
              assertThat(powertrain.components())
                  .extracting(CatalogModelDetail.PowertrainComponent::componentType)
                  .containsExactly("MOTOR", "BATTERY");
              assertThat(powertrain.components())
                  .filteredOn(component -> component.componentType().equals("BATTERY"))
                  .singleElement()
                  .extracting(CatalogModelDetail.PowertrainComponent::usableCapacityKwh)
                  .isEqualTo(new java.math.BigDecimal("77.40"));
            });
    assertThat(detail.variants())
        .singleElement()
        .satisfies(
            variant -> {
              assertThat(variant.maxBrakedTowingCapacityKg()).isEqualTo(1800);
              assertThat(variant.maxUnbrakedTowingCapacityKg()).isEqualTo(750);
              assertThat(variant.maxRoofLoadKg()).isEqualTo(80);
              assertThat(variant.minimumTurningRadiusM()).isEqualByComparingTo("5.75");
              assertThat(variant.certifications())
                  .singleElement()
                  .satisfies(
                      certification -> {
                        assertThat(certification.conditionCode()).isEqualTo("NORMAL-20");
                        assertThat(certification.certificationStandard()).isEqualTo("KOREA");
                      });
              assertThat(variant.configurations())
                  .singleElement()
                  .satisfies(
                      configuration -> {
                        assertThat(configuration.standardFeatures())
                            .singleElement()
                            .extracting(CatalogModelDetail.StandardFeature::name)
                            .isEqualTo("충돌방지");
                        assertThat(configuration.price().displayPrice()).isEqualTo(55_000_000L);
                        assertThat(configuration.options())
                            .singleElement()
                            .satisfies(
                                option -> {
                                  assertThat(option.code()).isEqualTo("ADAS");
                                  assertThat(option.displayPrice()).isEqualTo(1_000_000L);
                                  assertThat(option.includedItems()).containsExactly("후측방 충돌방지");
                                });
                      });
            });
    assertThat(executedCatalogQueryCount()).isEqualTo(5);
  }

  /** 상세 configuration 상태도 자체 기간뿐 아니라 선택 연식의 종료 경계를 함께 적용합니다. */
  @Test
  void appliesModelYearPeriodToDetailConfigurationStatus() {
    CatalogFixture fixture =
        createPublishedIceModel("KIA-TEST", "기아 테스트", "ENDED", "종료 모델", 30_000_000L);
    jdbcTemplate.update(
        "UPDATE car_model_year SET sales_ended_on = ? WHERE public_id = ?",
        AS_OF,
        fixture.modelYearPublicId());

    CatalogModelDetail detail =
        catalogModelDetailReadPort
            .find(fixture.modelPublicId(), fixture.modelYearPublicId(), AS_OF)
            .orElseThrow();

    assertThat(detail.variants())
        .flatExtracting(CatalogModelDetail.Variant::configurations)
        .singleElement()
        .extracting(CatalogModelDetail.SalesConfiguration::salesStatus)
        .isEqualTo(SalesStatus.DISCONTINUED);
  }

  private long executedCatalogQueryCount() {
    return catalogQueryProbe.executions().size();
  }

  private List<String> modelPageExecutionPlan() {
    CatalogExecution pageExecution =
        catalogQueryProbe.executions().stream()
            .filter(execution -> execution.sql().contains("ROW_NUMBER()"))
            .findFirst()
            .orElseThrow();
    return jdbcTemplate.queryForList(
        "EXPLAIN " + pageExecution.sql(), String.class, pageExecution.bindings().toArray());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class QueryObservationConfig {

    @Bean
    CatalogQueryProbe catalogQueryProbe() {
      return new CatalogQueryProbe();
    }

    @Bean
    DefaultConfigurationCustomizer catalogQueryObservation(CatalogQueryProbe probe) {
      return configuration -> {
        List<org.jooq.ExecuteListenerProvider> providers =
            new ArrayList<>(Arrays.asList(configuration.executeListenerProviders()));
        providers.add(new DefaultExecuteListenerProvider(probe));
        configuration.setExecuteListenerProvider(
            providers.toArray(org.jooq.ExecuteListenerProvider[]::new));
      };
    }
  }

  static final class CatalogQueryProbe implements ExecuteListener {
    private final List<CatalogExecution> executions = new ArrayList<>();

    @Override
    public void executeStart(ExecuteContext context) {
      executions.add(
          new CatalogExecution(context.sql(), new ArrayList<>(context.query().getBindValues())));
    }

    List<CatalogExecution> executions() {
      return List.copyOf(executions);
    }

    void clear() {
      executions.clear();
    }
  }

  private record CatalogExecution(String sql, List<Object> bindings) {}

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
    return new CatalogFixture(
        brandPublicId,
        jdbcTemplate.queryForObject(
            "SELECT public_id FROM car_model WHERE id = ?", UUID.class, modelId),
        jdbcTemplate.queryForObject(
            "SELECT public_id FROM car_model_year WHERE id = ?", UUID.class, modelYearId),
        configurationId);
  }

  private CatalogFixture createPublishedBevModel(
      String brandCode, String brandName, String modelCode, String modelName, long price) {
    long brandId = findOrCreateBrand(brandCode, brandName);
    UUID brandPublicId = findBrandPublicId(brandCode);
    long modelId = createModel(brandId, modelCode, modelName, "ev6 100%_");
    long modelYearId = createModelYear(modelId, modelCode);
    long powertrainId =
        jdbcTemplate.queryForObject(
            "INSERT INTO car_powertrain (public_id, model_year_id, code, name, propulsion_type, primary_fuel_type, drivetrain_type, max_ac_charge_kw, ac_charge_connector, ac_charge_minutes, ac_charge_from_percent, ac_charge_to_percent, ac_charge_power_kw, max_dc_charge_kw, dc_charge_connector, fast_charge_minutes, fast_charge_from_percent, fast_charge_to_percent, fast_charge_power_kw) VALUES (?, ?, ?, '전기', 'BEV', 'ELECTRICITY', 'RWD', 11, 'TYPE_2', 455, 10, 100, 11, 240, 'CCS_COMBO_2', 18, 10, 80, 350) RETURNING id",
            Long.class,
            UUID.randomUUID(),
            modelYearId,
            modelCode + "-BEV");
    jdbcTemplate.update(
        "INSERT INTO car_powertrain_component (powertrain_id, component_type, component_role, name, max_power_kw) VALUES (?, 'MOTOR', 'TRACTION', '모터', 168)",
        powertrainId);
    jdbcTemplate.update(
        "INSERT INTO car_powertrain_component (powertrain_id, component_type, component_role, name, capacity_kwh, usable_capacity_kwh) VALUES (?, 'BATTERY', 'ENERGY_STORAGE', '배터리', 84, 77.4)",
        powertrainId);
    long variantId =
        jdbcTemplate.queryForObject(
            "INSERT INTO car_vehicle_variant (public_id, model_year_id, powertrain_id, code, name, body_type, seating_capacity, length_mm, width_mm, height_mm, wheelbase_mm, max_braked_towing_capacity_kg, max_unbraked_towing_capacity_kg, max_roof_load_kg, minimum_turning_radius_m) VALUES (?, ?, ?, ?, ?, 'SUV', 5, 4700, 1890, 1550, 2900, 1800, 750, 80, 5.75) RETURNING id",
            Long.class,
            UUID.randomUUID(),
            modelYearId,
            powertrainId,
            modelCode + "-VARIANT",
            modelCode + " 변형");
    long configurationId = createConfiguration(variantId, modelCode, true);
    jdbcTemplate.update(
        "INSERT INTO car_configuration_price (configuration_id, effective_from, price_before_tax_benefit) VALUES (?, '2026-01-01', ?)",
        configurationId,
        price);
    return new CatalogFixture(
        brandPublicId,
        jdbcTemplate.queryForObject(
            "SELECT public_id FROM car_model WHERE id = ?", UUID.class, modelId),
        jdbcTemplate.queryForObject(
            "SELECT public_id FROM car_model_year WHERE id = ?", UUID.class, modelYearId),
        configurationId);
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

  private record CatalogFixture(
      UUID brandPublicId, UUID modelPublicId, UUID modelYearPublicId, long configurationId) {}
}
