package com.ronams.carmunity.catalog.internal.adapter.out.persistence;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Certification;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Component;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Configuration;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Generation;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Media;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Model;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.ModelYear;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Option;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Powertrain;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Price;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.SafetyRating;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.StandardFeature;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Variant;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Warranty;
import com.ronams.carmunity.catalog.internal.application.model.CatalogPriceBasis;
import com.ronams.carmunity.catalog.internal.application.service.CatalogModelYearSelector;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 모델 상세를 다섯 개의 고정 묶음 query로 읽고 application read model graph로 조립합니다. */
@org.springframework.stereotype.Component
final class CatalogDetailQuery {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final CatalogModelYearSelector modelYearSelector;

  CatalogDetailQuery(
      NamedParameterJdbcTemplate jdbcTemplate, CatalogModelYearSelector modelYearSelector) {
    this.jdbcTemplate = jdbcTemplate;
    this.modelYearSelector = modelYearSelector;
  }

  Optional<CatalogModelDetail> find(UUID modelId, UUID requestedModelYearId, LocalDate asOf) {
    MapSqlParameterSource modelParameters =
        new MapSqlParameterSource().addValue("modelId", modelId).addValue("asOf", asOf);
    Optional<Model> model = findModel(modelParameters);
    if (model.isEmpty()) {
      return Optional.empty();
    }

    List<YearSnapshot> yearSnapshots = findYears(modelParameters);
    List<ModelYear> modelYears = yearSnapshots.stream().map(YearSnapshot::modelYear).toList();
    Optional<ModelYear> selected = modelYearSelector.select(modelYears, requestedModelYearId);
    if (selected.isEmpty()) {
      return Optional.empty();
    }
    long selectedModelYearInternalId =
        yearSnapshots.stream()
            .filter(
                snapshot ->
                    snapshot.modelYear().modelYearId().equals(selected.orElseThrow().modelYearId()))
            .mapToLong(YearSnapshot::internalId)
            .findFirst()
            .orElseThrow();
    MapSqlParameterSource yearParameters =
        new MapSqlParameterSource()
            .addValue("modelYearInternalId", selectedModelYearInternalId)
            .addValue("asOf", asOf);

    List<Powertrain> powertrains = findPowertrains(yearParameters);
    Map<Long, List<Configuration>> configurations = findConfigurations(yearParameters);
    List<Variant> variants = findVariants(yearParameters, configurations);
    List<Generation> generations =
        new ArrayList<>(
            yearSnapshots.stream()
                .collect(
                    LinkedHashMap<UUID, Generation>::new,
                    (items, snapshot) ->
                        items.putIfAbsent(
                            snapshot.generation().generationId(), snapshot.generation()),
                    LinkedHashMap::putAll)
                .values());

    return Optional.of(
        new CatalogModelDetail(
            model.orElseThrow(),
            generations,
            modelYears,
            selected.orElseThrow(),
            powertrains,
            variants));
  }

  private Optional<Model> findModel(MapSqlParameterSource parameters) {
    return jdbcTemplate.query(
        MODEL_SQL,
        parameters,
        resultSet -> {
          if (!resultSet.next()) {
            return Optional.empty();
          }
          return Optional.of(
              new Model(
                  resultSet.getObject("model_id", UUID.class),
                  resultSet.getObject("brand_id", UUID.class),
                  resultSet.getString("brand_name"),
                  resultSet.getString("brand_name_en"),
                  resultSet.getString("model_name"),
                  resultSet.getString("model_name_en"),
                  resultSet.getString("summary")));
        });
  }

  private List<YearSnapshot> findYears(MapSqlParameterSource parameters) {
    return jdbcTemplate.query(
        YEARS_SQL,
        parameters,
        (resultSet, rowNumber) ->
            new YearSnapshot(
                resultSet.getLong("model_year_internal_id"),
                new Generation(
                    resultSet.getObject("generation_id", UUID.class),
                    resultSet.getString("generation_name"),
                    resultSet.getString("change_type")),
                new ModelYear(
                    resultSet.getObject("model_year_id", UUID.class),
                    resultSet.getInt("model_year"),
                    resultSet.getString("edition_name"),
                    resultSet.getObject("released_on", LocalDate.class),
                    resultSet.getObject("sales_started_on", LocalDate.class),
                    resultSet.getObject("sales_ended_on", LocalDate.class),
                    SalesStatus.valueOf(resultSet.getString("sales_status")))));
  }

  private List<Powertrain> findPowertrains(MapSqlParameterSource parameters) {
    LinkedHashMap<UUID, PowertrainBuilder> builders =
        jdbcTemplate.query(
            POWERTRAINS_SQL,
            parameters,
            resultSet -> {
              LinkedHashMap<UUID, PowertrainBuilder> items = new LinkedHashMap<>();
              while (resultSet.next()) {
                UUID powertrainId = resultSet.getObject("powertrain_id", UUID.class);
                PowertrainBuilder builder =
                    items.computeIfAbsent(
                        powertrainId, ignored -> new PowertrainBuilder(resultSet));
                builder.addComponent(resultSet);
              }
              return items;
            });
    return builders.values().stream().map(PowertrainBuilder::build).toList();
  }

  private List<Variant> findVariants(
      MapSqlParameterSource parameters, Map<Long, List<Configuration>> configurations) {
    LinkedHashMap<Long, VariantBuilder> builders =
        jdbcTemplate.query(
            VARIANTS_SQL,
            parameters,
            resultSet -> {
              LinkedHashMap<Long, VariantBuilder> items = new LinkedHashMap<>();
              while (resultSet.next()) {
                long internalId = resultSet.getLong("variant_internal_id");
                VariantBuilder builder =
                    items.computeIfAbsent(internalId, ignored -> new VariantBuilder(resultSet));
                builder.addCertification(resultSet);
              }
              return items;
            });
    return builders.entrySet().stream()
        .map(
            entry -> entry.getValue().build(configurations.getOrDefault(entry.getKey(), List.of())))
        .toList();
  }

  private Map<Long, List<Configuration>> findConfigurations(MapSqlParameterSource parameters) {
    LinkedHashMap<Long, LinkedHashMap<UUID, ConfigurationBuilder>> byVariant =
        jdbcTemplate.query(
            CONFIGURATIONS_SQL,
            parameters,
            resultSet -> {
              LinkedHashMap<Long, LinkedHashMap<UUID, ConfigurationBuilder>> variants =
                  new LinkedHashMap<>();
              while (resultSet.next()) {
                long variantId = resultSet.getLong("variant_internal_id");
                UUID configurationId = resultSet.getObject("configuration_id", UUID.class);
                ConfigurationBuilder builder =
                    variants
                        .computeIfAbsent(variantId, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(
                            configurationId, ignored -> new ConfigurationBuilder(resultSet));
                builder.addOption(resultSet);
              }
              return variants;
            });
    LinkedHashMap<Long, List<Configuration>> result = new LinkedHashMap<>();
    byVariant.forEach(
        (variantId, builders) ->
            result.put(
                variantId, builders.values().stream().map(ConfigurationBuilder::build).toList()));
    return result;
  }

  private static Integer integer(ResultSet resultSet, String column) throws SQLException {
    return resultSet.getObject(column, Integer.class);
  }

  private static BigDecimal decimal(ResultSet resultSet, String column) throws SQLException {
    return resultSet.getBigDecimal(column);
  }

  private static FuelType fuelType(String value) {
    return value == null ? null : FuelType.valueOf(value);
  }

  private static List<StandardFeature> standardFeatures(String json) {
    return jsonArray(json).stream()
        .map(
            node ->
                new StandardFeature(
                    text(node, "category"), text(node, "name"), optionalText(node, "description")))
        .toList();
  }

  private static List<Warranty> warranties(String json) {
    return jsonArray(json).stream()
        .map(
            node ->
                new Warranty(
                    text(node, "name"),
                    optionalInteger(node, "years"),
                    optionalInteger(node, "distanceKm"),
                    optionalText(node, "note")))
        .toList();
  }

  private static List<SafetyRating> safetyRatings(String json) {
    return jsonArray(json).stream()
        .map(
            node ->
                new SafetyRating(
                    text(node, "organization"),
                    text(node, "rating"),
                    optionalDate(node, "assessedOn"),
                    optionalText(node, "note")))
        .toList();
  }

  private static List<Media> media(String json) {
    return jsonArray(json).stream()
        .map(
            node ->
                new Media(
                    text(node, "type"),
                    text(node, "url"),
                    text(node, "alt"),
                    node.get("sortOrder").intValue()))
        .toList();
  }

  private static List<String> stringArray(String json) {
    return jsonArray(json).stream().map(JsonNode::stringValue).toList();
  }

  private static List<JsonNode> jsonArray(String json) {
    try {
      return JSON.readTree(json).valueStream().toList();
    } catch (Exception exception) {
      throw new IllegalStateException("검증을 통과한 catalog JSON을 읽을 수 없습니다.", exception);
    }
  }

  private static String text(JsonNode node, String field) {
    return node.get(field).stringValue();
  }

  private static String optionalText(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.stringValue();
  }

  private static Integer optionalInteger(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.intValue();
  }

  private static LocalDate optionalDate(JsonNode node, String field) {
    String value = optionalText(node, field);
    return value == null ? null : LocalDate.parse(value);
  }

  private record YearSnapshot(long internalId, Generation generation, ModelYear modelYear) {}

  private static final class PowertrainBuilder {
    private final UUID powertrainId;
    private final String name;
    private final PropulsionType propulsionType;
    private final FuelType primaryFuelType;
    private final FuelType secondaryFuelType;
    private final String drivetrainType;
    private final String transmissionType;
    private final Integer transmissionGears;
    private final BigDecimal systemMaxPowerKw;
    private final BigDecimal systemMaxPowerPs;
    private final BigDecimal systemMaxTorqueNm;
    private final BigDecimal zeroToHundredSeconds;
    private final Integer topSpeedKmh;
    private final BigDecimal maxAcChargeKw;
    private final BigDecimal maxDcChargeKw;
    private final Integer fastChargeMinutes;
    private final Integer fastChargeFromPercent;
    private final Integer fastChargeToPercent;
    private final List<Component> components = new ArrayList<>();

    private PowertrainBuilder(ResultSet resultSet) {
      try {
        powertrainId = resultSet.getObject("powertrain_id", UUID.class);
        name = resultSet.getString("powertrain_name");
        propulsionType = PropulsionType.valueOf(resultSet.getString("propulsion_type"));
        primaryFuelType = FuelType.valueOf(resultSet.getString("primary_fuel_type"));
        secondaryFuelType = fuelType(resultSet.getString("secondary_fuel_type"));
        drivetrainType = resultSet.getString("drivetrain_type");
        transmissionType = resultSet.getString("transmission_type");
        transmissionGears = integer(resultSet, "transmission_gears");
        systemMaxPowerKw = decimal(resultSet, "system_max_power_kw");
        systemMaxPowerPs = decimal(resultSet, "system_max_power_ps");
        systemMaxTorqueNm = decimal(resultSet, "system_max_torque_nm");
        zeroToHundredSeconds = decimal(resultSet, "zero_to_hundred_seconds");
        topSpeedKmh = integer(resultSet, "top_speed_kmh");
        maxAcChargeKw = decimal(resultSet, "max_ac_charge_kw");
        maxDcChargeKw = decimal(resultSet, "max_dc_charge_kw");
        fastChargeMinutes = integer(resultSet, "fast_charge_minutes");
        fastChargeFromPercent = integer(resultSet, "fast_charge_from_percent");
        fastChargeToPercent = integer(resultSet, "fast_charge_to_percent");
      } catch (SQLException exception) {
        throw new IllegalStateException("동력계통 projection을 읽을 수 없습니다.", exception);
      }
    }

    private void addComponent(ResultSet resultSet) throws SQLException {
      if (resultSet.getString("component_type") == null) {
        return;
      }
      components.add(
          new Component(
              resultSet.getString("component_type"),
              resultSet.getString("component_role"),
              resultSet.getString("component_position"),
              resultSet.getString("component_name"),
              fuelType(resultSet.getString("component_fuel_type")),
              integer(resultSet, "displacement_cc"),
              integer(resultSet, "cylinder_count"),
              resultSet.getString("aspiration_type"),
              decimal(resultSet, "component_max_power_kw"),
              decimal(resultSet, "component_max_power_ps"),
              integer(resultSet, "max_power_rpm"),
              decimal(resultSet, "component_max_torque_nm"),
              decimal(resultSet, "max_torque_kgf_m"),
              integer(resultSet, "max_torque_rpm_from"),
              integer(resultSet, "max_torque_rpm_to"),
              decimal(resultSet, "capacity_kwh"),
              decimal(resultSet, "capacity_ah"),
              decimal(resultSet, "voltage_v"),
              decimal(resultSet, "capacity_l"),
              decimal(resultSet, "capacity_kg"),
              decimal(resultSet, "pressure_bar"),
              resultSet.getString("manufacturer_name"),
              resultSet.getString("cell_manufacturer_name"),
              resultSet.getString("production_country_code"),
              resultSet.getString("cell_type"),
              resultSet.getString("chemistry")));
    }

    private Powertrain build() {
      return new Powertrain(
          powertrainId,
          name,
          propulsionType,
          primaryFuelType,
          secondaryFuelType,
          drivetrainType,
          transmissionType,
          transmissionGears,
          systemMaxPowerKw,
          systemMaxPowerPs,
          systemMaxTorqueNm,
          zeroToHundredSeconds,
          topSpeedKmh,
          maxAcChargeKw,
          maxDcChargeKw,
          fastChargeMinutes,
          fastChargeFromPercent,
          fastChargeToPercent,
          components);
    }
  }

  private static final class VariantBuilder {
    private final UUID variantId;
    private final UUID powertrainId;
    private final String name;
    private final BodyType bodyType;
    private final String roofType;
    private final int seatingCapacity;
    private final Integer doorCount;
    private final int lengthMm;
    private final int widthMm;
    private final int heightMm;
    private final int wheelbaseMm;
    private final Integer frontTrackMm;
    private final Integer rearTrackMm;
    private final Integer groundClearanceMm;
    private final BigDecimal cargoCapacityL;
    private final BigDecimal maxCargoCapacityL;
    private final Integer curbWeightKg;
    private final Integer grossVehicleWeightKg;
    private final BigDecimal wheelSizeInch;
    private final String frontTire;
    private final String rearTire;
    private final String frontSuspension;
    private final String rearSuspension;
    private final String steeringType;
    private final String frontBrake;
    private final String rearBrake;
    private final List<Certification> certifications = new ArrayList<>();

    private VariantBuilder(ResultSet resultSet) {
      try {
        variantId = resultSet.getObject("variant_id", UUID.class);
        powertrainId = resultSet.getObject("powertrain_id", UUID.class);
        name = resultSet.getString("variant_name");
        bodyType = BodyType.valueOf(resultSet.getString("body_type"));
        roofType = resultSet.getString("roof_type");
        seatingCapacity = resultSet.getInt("seating_capacity");
        doorCount = integer(resultSet, "door_count");
        lengthMm = resultSet.getInt("length_mm");
        widthMm = resultSet.getInt("width_mm");
        heightMm = resultSet.getInt("height_mm");
        wheelbaseMm = resultSet.getInt("wheelbase_mm");
        frontTrackMm = integer(resultSet, "front_track_mm");
        rearTrackMm = integer(resultSet, "rear_track_mm");
        groundClearanceMm = integer(resultSet, "ground_clearance_mm");
        cargoCapacityL = decimal(resultSet, "cargo_capacity_l");
        maxCargoCapacityL = decimal(resultSet, "max_cargo_capacity_l");
        curbWeightKg = integer(resultSet, "curb_weight_kg");
        grossVehicleWeightKg = integer(resultSet, "gross_vehicle_weight_kg");
        wheelSizeInch = decimal(resultSet, "wheel_size_inch");
        frontTire = resultSet.getString("front_tire");
        rearTire = resultSet.getString("rear_tire");
        frontSuspension = resultSet.getString("front_suspension");
        rearSuspension = resultSet.getString("rear_suspension");
        steeringType = resultSet.getString("steering_type");
        frontBrake = resultSet.getString("front_brake");
        rearBrake = resultSet.getString("rear_brake");
      } catch (SQLException exception) {
        throw new IllegalStateException("차량 변형 projection을 읽을 수 없습니다.", exception);
      }
    }

    private void addCertification(ResultSet resultSet) throws SQLException {
      if (resultSet.getString("condition_code") == null) {
        return;
      }
      certifications.add(
          new Certification(
              resultSet.getString("condition_code"),
              resultSet.getString("condition_name"),
              resultSet.getString("certification_type"),
              resultSet.getString("temperature_condition"),
              decimal(resultSet, "certification_wheel_size_inch"),
              resultSet.getString("tire_specification"),
              resultSet.getBoolean("built_in_camera"),
              integer(resultSet, "certification_curb_weight_kg"),
              integer(resultSet, "efficiency_grade"),
              decimal(resultSet, "city_efficiency"),
              decimal(resultSet, "highway_efficiency"),
              decimal(resultSet, "combined_efficiency"),
              resultSet.getString("efficiency_unit"),
              decimal(resultSet, "city_range_km"),
              decimal(resultSet, "highway_range_km"),
              decimal(resultSet, "combined_range_km"),
              decimal(resultSet, "co2_g_per_km")));
    }

    private Variant build(List<Configuration> configurations) {
      return new Variant(
          variantId,
          powertrainId,
          name,
          bodyType,
          roofType,
          seatingCapacity,
          doorCount,
          lengthMm,
          widthMm,
          heightMm,
          wheelbaseMm,
          frontTrackMm,
          rearTrackMm,
          groundClearanceMm,
          cargoCapacityL,
          maxCargoCapacityL,
          curbWeightKg,
          grossVehicleWeightKg,
          wheelSizeInch,
          frontTire,
          rearTire,
          frontSuspension,
          rearSuspension,
          steeringType,
          frontBrake,
          rearBrake,
          certifications,
          configurations);
    }
  }

  private static final class ConfigurationBuilder {
    private final UUID configurationId;
    private final String trimName;
    private final String salesChannel;
    private final LocalDate salesStartedOn;
    private final LocalDate salesEndedOn;
    private final SalesStatus salesStatus;
    private final int displayOrder;
    private final List<StandardFeature> standardFeatures;
    private final List<Warranty> warranties;
    private final List<SafetyRating> safetyRatings;
    private final List<Media> media;
    private final Price price;
    private final List<Option> options = new ArrayList<>();

    private ConfigurationBuilder(ResultSet resultSet) {
      try {
        configurationId = resultSet.getObject("configuration_id", UUID.class);
        trimName = resultSet.getString("trim_name");
        salesChannel = resultSet.getString("sales_channel");
        salesStartedOn = resultSet.getObject("sales_started_on", LocalDate.class);
        salesEndedOn = resultSet.getObject("sales_ended_on", LocalDate.class);
        salesStatus = SalesStatus.valueOf(resultSet.getString("sales_status"));
        displayOrder = resultSet.getInt("display_order");
        standardFeatures = standardFeatures(resultSet.getString("standard_features"));
        warranties = warranties(resultSet.getString("warranties"));
        safetyRatings = safetyRatings(resultSet.getString("safety_ratings"));
        media = media(resultSet.getString("media"));
        Long beforeTax = resultSet.getObject("price_before_tax_benefit", Long.class);
        if (beforeTax == null) {
          price = null;
        } else {
          Long afterTax = resultSet.getObject("price_after_tax_benefit", Long.class);
          price =
              new Price(
                  resultSet.getObject("price_effective_from", LocalDate.class),
                  beforeTax,
                  afterTax,
                  afterTax == null ? beforeTax : afterTax,
                  afterTax == null
                      ? CatalogPriceBasis.BEFORE_TAX_BENEFIT
                      : CatalogPriceBasis.AFTER_TAX_BENEFIT,
                  resultSet.getString("price_currency_code"));
        }
      } catch (SQLException exception) {
        throw new IllegalStateException("판매 구성 projection을 읽을 수 없습니다.", exception);
      }
    }

    private void addOption(ResultSet resultSet) throws SQLException {
      String code = resultSet.getString("option_code");
      if (code == null) {
        return;
      }
      long beforeTax = resultSet.getLong("option_price_before_tax_benefit");
      Long afterTax = resultSet.getObject("option_price_after_tax_benefit", Long.class);
      options.add(
          new Option(
              code,
              resultSet.getString("option_name"),
              resultSet.getString("option_type"),
              resultSet.getString("option_category"),
              beforeTax,
              afterTax,
              afterTax == null ? beforeTax : afterTax,
              afterTax == null
                  ? CatalogPriceBasis.BEFORE_TAX_BENEFIT
                  : CatalogPriceBasis.AFTER_TAX_BENEFIT,
              resultSet.getString("option_currency_code"),
              stringArray(resultSet.getString("included_items")),
              resultSet.getObject("option_effective_from", LocalDate.class),
              resultSet.getInt("option_display_order")));
    }

    private Configuration build() {
      return new Configuration(
          configurationId,
          trimName,
          salesChannel,
          salesStartedOn,
          salesEndedOn,
          salesStatus,
          displayOrder,
          standardFeatures,
          warranties,
          safetyRatings,
          media,
          price,
          options);
    }
  }

  private static final String MODEL_SQL =
      """
      SELECT
          model.public_id AS model_id,
          brand.public_id AS brand_id,
          brand.name_ko AS brand_name,
          brand.name_en AS brand_name_en,
          model.name_ko AS model_name,
          model.name_en AS model_name_en,
          model.summary
      FROM car_model model
      JOIN car_brand brand ON brand.id = model.brand_id
      WHERE model.public_id = :modelId
        AND EXISTS (
            SELECT 1
            FROM car_generation generation
            JOIN car_model_year model_year ON model_year.generation_id = generation.id
            JOIN car_vehicle_variant variant ON variant.model_year_id = model_year.id
            JOIN car_configuration configuration ON configuration.vehicle_variant_id = variant.id
            WHERE generation.model_id = model.id
              AND configuration.is_published
        )
      """;

  private static final String YEARS_SQL =
      """
      SELECT
          generation.public_id AS generation_id,
          generation.name AS generation_name,
          generation.change_type,
          generation.generation_order,
          model_year.id AS model_year_internal_id,
          model_year.public_id AS model_year_id,
          model_year.model_year,
          model_year.display_name AS edition_name,
          model_year.released_on,
          model_year.sales_started_on,
          model_year.sales_ended_on,
          CASE MIN(
              CASE
                  WHEN :asOf < model_year.sales_started_on
                    OR :asOf < configuration.sales_started_on THEN 2
                  WHEN (model_year.sales_ended_on IS NOT NULL AND :asOf >= model_year.sales_ended_on)
                    OR (configuration.sales_ended_on IS NOT NULL AND :asOf >= configuration.sales_ended_on)
                    THEN 3
                  ELSE 1
              END
          )
              WHEN 1 THEN 'ON_SALE'
              WHEN 2 THEN 'UPCOMING'
              ELSE 'DISCONTINUED'
          END AS sales_status
      FROM car_model model
      JOIN car_generation generation ON generation.model_id = model.id
      JOIN car_model_year model_year ON model_year.generation_id = generation.id
      JOIN car_vehicle_variant variant ON variant.model_year_id = model_year.id
      JOIN car_configuration configuration ON configuration.vehicle_variant_id = variant.id
      WHERE model.public_id = :modelId
        AND configuration.is_published
      GROUP BY generation.id, model_year.id
      ORDER BY generation.generation_order, model_year.model_year, model_year.sales_started_on,
               model_year.public_id
      """;

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
          powertrain.max_dc_charge_kw,
          powertrain.fast_charge_minutes,
          powertrain.fast_charge_from_percent,
          powertrain.fast_charge_to_percent,
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
