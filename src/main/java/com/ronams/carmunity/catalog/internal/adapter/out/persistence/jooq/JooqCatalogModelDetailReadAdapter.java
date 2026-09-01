package com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq;

import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarBrand.CAR_BRAND;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarConfiguration.CAR_CONFIGURATION;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarGeneration.CAR_GENERATION;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModel.CAR_MODEL;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModelYear.CAR_MODEL_YEAR;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarVehicleVariant.CAR_VEHICLE_VARIANT;

import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarBrand;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarConfiguration;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarGeneration;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModel;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModelYear;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarVehicleVariant;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Generation;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Model;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.ModelYear;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Powertrain;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.SalesConfiguration;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Variant;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelDetailReadPort;
import com.ronams.carmunity.catalog.internal.application.service.CatalogModelYearSelector;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.stereotype.Component;

/** 모델 상세를 다섯 개의 고정 묶음 query로 읽고 application read model graph로 조립합니다. */
@Component
final class JooqCatalogModelDetailReadAdapter implements CatalogModelDetailReadPort {

  private final DSLContext dsl;
  private final CatalogModelYearSelector modelYearSelector;
  private final CatalogModelDetailAssembler detailAssembler;

  JooqCatalogModelDetailReadAdapter(
      DSLContext dsl,
      CatalogModelYearSelector modelYearSelector,
      CatalogModelDetailAssembler detailAssembler) {
    this.dsl = dsl;
    this.modelYearSelector = modelYearSelector;
    this.detailAssembler = detailAssembler;
  }

  @Override
  public Optional<CatalogModelDetail> find(
      UUID modelId, UUID requestedModelYearId, LocalDate asOf) {
    Optional<Model> model = findModel(modelId);
    if (model.isEmpty()) {
      return Optional.empty();
    }

    List<CatalogModelYearSnapshot> yearSnapshots = findYears(modelId, asOf);
    List<ModelYear> modelYears =
        yearSnapshots.stream().map(CatalogModelYearSnapshot::modelYear).toList();
    Optional<ModelYear> selected = modelYearSelector.select(modelYears, requestedModelYearId);
    if (selected.isEmpty()) {
      return Optional.empty();
    }
    long selectedModelYearInternalId =
        yearSnapshots.stream()
            .filter(
                snapshot ->
                    snapshot.modelYear().modelYearId().equals(selected.orElseThrow().modelYearId()))
            .mapToLong(CatalogModelYearSnapshot::internalId)
            .findFirst()
            .orElseThrow();
    Map<String, Object> yearParameters =
        Map.of("modelYearInternalId", selectedModelYearInternalId, "asOf", asOf);

    List<Powertrain> powertrains = findPowertrains(yearParameters);
    Map<Long, List<SalesConfiguration>> configurations = findConfigurations(yearParameters);
    List<Variant> variants = findVariants(yearParameters, configurations);
    return Optional.of(
        detailAssembler.assemble(
            model.orElseThrow(), yearSnapshots, selected.orElseThrow(), powertrains, variants));
  }

  private Optional<Model> findModel(UUID modelId) {
    CarModel model = CAR_MODEL.as("model");
    CarBrand brand = CAR_BRAND.as("brand");
    CarGeneration generation = CAR_GENERATION.as("generation");
    CarModelYear modelYear = CAR_MODEL_YEAR.as("model_year");
    CarVehicleVariant variant = CAR_VEHICLE_VARIANT.as("variant");
    CarConfiguration configuration = CAR_CONFIGURATION.as("configuration");

    return dsl.select(
            model.PUBLIC_ID,
            brand.PUBLIC_ID,
            brand.NAME_KO,
            brand.NAME_EN,
            model.NAME_KO,
            model.NAME_EN,
            model.SUMMARY)
        .from(model)
        .join(brand)
        .on(brand.ID.eq(model.BRAND_ID))
        .where(model.PUBLIC_ID.eq(modelId))
        .andExists(
            DSL.selectOne()
                .from(generation)
                .join(modelYear)
                .on(modelYear.GENERATION_ID.eq(generation.ID))
                .join(variant)
                .on(variant.MODEL_YEAR_ID.eq(modelYear.ID))
                .join(configuration)
                .on(configuration.VEHICLE_VARIANT_ID.eq(variant.ID))
                .where(generation.MODEL_ID.eq(model.ID))
                .and(configuration.IS_PUBLISHED.isTrue()))
        .fetchOptional(
            record ->
                new Model(
                    record.get(model.PUBLIC_ID),
                    record.get(brand.PUBLIC_ID),
                    record.get(brand.NAME_KO),
                    record.get(brand.NAME_EN),
                    record.get(model.NAME_KO),
                    record.get(model.NAME_EN),
                    record.get(model.SUMMARY)));
  }

  private List<CatalogModelYearSnapshot> findYears(UUID modelId, LocalDate asOf) {
    CarModel model = CAR_MODEL.as("model");
    CarGeneration generation = CAR_GENERATION.as("generation");
    CarModelYear modelYear = CAR_MODEL_YEAR.as("model_year");
    CarVehicleVariant variant = CAR_VEHICLE_VARIANT.as("variant");
    CarConfiguration configuration = CAR_CONFIGURATION.as("configuration");
    Field<Integer> statusRank =
        DSL.when(modelYear.SALES_STARTED_ON.gt(asOf).or(configuration.SALES_STARTED_ON.gt(asOf)), 2)
            .when(modelYear.SALES_ENDED_ON.le(asOf).or(configuration.SALES_ENDED_ON.le(asOf)), 3)
            .otherwise(1);
    Field<String> salesStatus =
        DSL.choose(DSL.min(statusRank))
            .when(1, SalesStatus.ON_SALE.name())
            .when(2, SalesStatus.UPCOMING.name())
            .otherwise(SalesStatus.DISCONTINUED.name());

    return dsl.select(
            generation.PUBLIC_ID,
            generation.NAME,
            generation.CHANGE_TYPE,
            modelYear.ID,
            modelYear.PUBLIC_ID,
            modelYear.MODEL_YEAR,
            modelYear.DISPLAY_NAME,
            modelYear.RELEASED_ON,
            modelYear.SALES_STARTED_ON,
            modelYear.SALES_ENDED_ON,
            salesStatus)
        .from(model)
        .join(generation)
        .on(generation.MODEL_ID.eq(model.ID))
        .join(modelYear)
        .on(modelYear.GENERATION_ID.eq(generation.ID))
        .join(variant)
        .on(variant.MODEL_YEAR_ID.eq(modelYear.ID))
        .join(configuration)
        .on(configuration.VEHICLE_VARIANT_ID.eq(variant.ID))
        .where(model.PUBLIC_ID.eq(modelId))
        .and(configuration.IS_PUBLISHED.isTrue())
        .groupBy(generation.ID, modelYear.ID)
        .orderBy(
            generation.GENERATION_ORDER,
            modelYear.MODEL_YEAR,
            modelYear.SALES_STARTED_ON,
            modelYear.PUBLIC_ID)
        .fetch(
            record ->
                new CatalogModelYearSnapshot(
                    record.get(modelYear.ID),
                    new Generation(
                        record.get(generation.PUBLIC_ID),
                        record.get(generation.NAME),
                        record.get(generation.CHANGE_TYPE)),
                    new ModelYear(
                        record.get(modelYear.PUBLIC_ID),
                        record.get(modelYear.MODEL_YEAR).intValue(),
                        record.get(modelYear.DISPLAY_NAME),
                        record.get(modelYear.RELEASED_ON),
                        record.get(modelYear.SALES_STARTED_ON),
                        record.get(modelYear.SALES_ENDED_ON),
                        SalesStatus.valueOf(record.get(salesStatus)))));
  }

  private List<Powertrain> findPowertrains(Map<String, ?> parameters) {
    return detailAssembler.assemblePowertrains(fetch(POWERTRAINS_SQL, parameters));
  }

  private List<Variant> findVariants(
      Map<String, ?> parameters, Map<Long, List<SalesConfiguration>> configurations) {
    return detailAssembler.assembleVariants(fetch(VARIANTS_SQL, parameters), configurations);
  }

  private Map<Long, List<SalesConfiguration>> findConfigurations(Map<String, ?> parameters) {
    return detailAssembler.assembleConfigurations(fetch(CONFIGURATIONS_SQL, parameters));
  }

  private List<CatalogProjectionRecord> fetch(String sql, Map<String, ?> parameters) {
    var parsedSql = NamedParameterUtils.parseSqlStatement(sql);
    var parameterSource = new MapSqlParameterSource(parameters);
    String preparedSql = NamedParameterUtils.substituteNamedParameters(parsedSql, parameterSource);
    Object[] bindings = NamedParameterUtils.buildValueArray(parsedSql, parameterSource, null);
    return dsl.fetch(preparedSql, bindings).stream().map(CatalogProjectionRecord::new).toList();
  }

  private static final String POWERTRAINS_SQL =
      """
      SELECT
          powertrain.public_id AS powertrain_id,
          powertrain.name AS powertrain_name,
          powertrain.propulsion_type,
          powertrain.primary_fuel_type,
          powertrain.secondary_fuel_type,
          powertrain.drivetrain_type,
          powertrain.transmission_type,
          powertrain.transmission_gears,
          powertrain.system_max_power_kw,
          powertrain.system_max_power_ps,
          powertrain.system_max_torque_nm,
          powertrain.zero_to_hundred_seconds,
          powertrain.top_speed_kmh,
          powertrain.max_ac_charge_kw,
          powertrain.ac_charge_connector,
          powertrain.ac_charge_minutes,
          powertrain.ac_charge_from_percent,
          powertrain.ac_charge_to_percent,
          powertrain.ac_charge_power_kw,
          powertrain.max_dc_charge_kw,
          powertrain.dc_charge_connector,
          powertrain.fast_charge_minutes,
          powertrain.fast_charge_from_percent,
          powertrain.fast_charge_to_percent,
          powertrain.fast_charge_power_kw,
          component.component_type,
          component.component_role,
          component.position AS component_position,
          component.name AS component_name,
          component.fuel_type AS component_fuel_type,
          component.displacement_cc,
          component.cylinder_count,
          component.aspiration_type,
          component.max_power_kw AS component_max_power_kw,
          component.max_power_ps AS component_max_power_ps,
          component.max_power_rpm,
          component.max_torque_nm AS component_max_torque_nm,
          component.max_torque_kgf_m,
          component.max_torque_rpm_from,
          component.max_torque_rpm_to,
          component.capacity_kwh,
          component.usable_capacity_kwh,
          component.capacity_ah,
          component.voltage_v,
          component.capacity_l,
          component.capacity_kg,
          component.pressure_bar,
          component.manufacturer_name,
          component.cell_manufacturer_name,
          component.production_country_code,
          component.cell_type,
          component.chemistry
      FROM car_powertrain powertrain
      LEFT JOIN car_powertrain_component component ON component.powertrain_id = powertrain.id
      WHERE powertrain.model_year_id = :modelYearInternalId
        AND EXISTS (
            SELECT 1
            FROM car_vehicle_variant variant
            JOIN car_configuration configuration ON configuration.vehicle_variant_id = variant.id
            WHERE variant.powertrain_id = powertrain.id
              AND configuration.is_published
        )
      ORDER BY powertrain.public_id, component.sort_order, component.id
      """;

  private static final String VARIANTS_SQL =
      """
      SELECT
          variant.id AS variant_internal_id,
          variant.public_id AS variant_id,
          powertrain.public_id AS powertrain_id,
          variant.name AS variant_name,
          variant.body_type,
          variant.roof_type,
          variant.seating_capacity,
          variant.door_count,
          variant.length_mm,
          variant.width_mm,
          variant.height_mm,
          variant.wheelbase_mm,
          variant.front_track_mm,
          variant.rear_track_mm,
          variant.ground_clearance_mm,
          variant.cargo_capacity_l,
          variant.max_cargo_capacity_l,
          variant.curb_weight_kg,
          variant.gross_vehicle_weight_kg,
          variant.max_braked_towing_capacity_kg,
          variant.max_unbraked_towing_capacity_kg,
          variant.max_roof_load_kg,
          variant.minimum_turning_radius_m,
          variant.wheel_size_inch,
          variant.front_tire,
          variant.rear_tire,
          variant.front_suspension,
          variant.rear_suspension,
          variant.steering_type,
          variant.front_brake,
          variant.rear_brake,
          certification.condition_code,
          certification.condition_name,
          certification.certification_type,
          certification.certification_standard,
          certification.temperature_condition,
          certification.wheel_size_inch AS certification_wheel_size_inch,
          certification.tire_specification,
          certification.built_in_camera,
          certification.curb_weight_kg AS certification_curb_weight_kg,
          certification.efficiency_grade,
          certification.city_efficiency,
          certification.highway_efficiency,
          certification.combined_efficiency,
          certification.efficiency_unit,
          certification.city_range_km,
          certification.highway_range_km,
          certification.combined_range_km,
          certification.co2_g_per_km
      FROM car_vehicle_variant variant
      JOIN car_powertrain powertrain ON powertrain.id = variant.powertrain_id
      LEFT JOIN car_certification certification ON certification.vehicle_variant_id = variant.id
      WHERE variant.model_year_id = :modelYearInternalId
        AND EXISTS (
            SELECT 1
            FROM car_configuration configuration
            WHERE configuration.vehicle_variant_id = variant.id
              AND configuration.is_published
        )
      ORDER BY variant.public_id, certification.condition_code
      """;

  private static final String CONFIGURATIONS_SQL =
      """
      SELECT
          variant.id AS variant_internal_id,
          configuration.public_id AS configuration_id,
          configuration.trim_name,
          configuration.sales_channel,
          configuration.sales_started_on,
          configuration.sales_ended_on,
          CASE
              WHEN :asOf < model_year.sales_started_on
                OR :asOf < configuration.sales_started_on THEN 'UPCOMING'
              WHEN (model_year.sales_ended_on IS NOT NULL
                AND :asOf >= model_year.sales_ended_on)
                OR (configuration.sales_ended_on IS NOT NULL
                AND :asOf >= configuration.sales_ended_on) THEN 'DISCONTINUED'
              ELSE 'ON_SALE'
          END AS sales_status,
          configuration.display_order,
          configuration.standard_features::TEXT AS standard_features,
          configuration.warranties::TEXT AS warranties,
          configuration.safety_ratings::TEXT AS safety_ratings,
          configuration.media::TEXT AS media,
          price.effective_from AS price_effective_from,
          price.price_before_tax_benefit,
          price.price_after_tax_benefit,
          price.currency_code AS price_currency_code,
          catalog_option.code AS option_code,
          catalog_option.name AS option_name,
          catalog_option.option_type,
          catalog_option.category AS option_category,
          catalog_option.price_before_tax_benefit AS option_price_before_tax_benefit,
          catalog_option.price_after_tax_benefit AS option_price_after_tax_benefit,
          catalog_option.currency_code AS option_currency_code,
          catalog_option.included_items::TEXT AS included_items,
          catalog_option.effective_from AS option_effective_from,
          catalog_option.display_order AS option_display_order
      FROM car_vehicle_variant variant
      JOIN car_model_year model_year ON model_year.id = variant.model_year_id
      JOIN car_configuration configuration ON configuration.vehicle_variant_id = variant.id
      LEFT JOIN LATERAL (
          SELECT price.*
          FROM car_configuration_price price
          WHERE price.configuration_id = configuration.id
            AND price.effective_from <= :asOf
          ORDER BY price.effective_from DESC
          LIMIT 1
      ) price ON TRUE
      LEFT JOIN LATERAL (
          SELECT latest_option.*
          FROM (
              SELECT DISTINCT ON (catalog_option.code) catalog_option.*
              FROM car_configuration_option catalog_option
              WHERE catalog_option.configuration_id = configuration.id
                AND catalog_option.effective_from <= :asOf
              ORDER BY catalog_option.code, catalog_option.effective_from DESC
          ) latest_option
          WHERE latest_option.is_selectable
      ) catalog_option ON TRUE
      WHERE variant.model_year_id = :modelYearInternalId
        AND configuration.is_published
      ORDER BY variant.public_id, configuration.display_order, configuration.public_id,
               catalog_option.display_order, catalog_option.code
      """;
}
