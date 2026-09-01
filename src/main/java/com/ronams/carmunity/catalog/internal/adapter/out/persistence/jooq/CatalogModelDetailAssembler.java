package com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Certification;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Generation;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Media;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Model;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.ModelYear;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Option;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Powertrain;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.PowertrainComponent;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Price;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.SafetyRating;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.SalesConfiguration;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.StandardFeature;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Variant;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Warranty;
import com.ronams.carmunity.catalog.internal.application.model.CatalogPriceBasis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 다섯 query 결과의 중복을 제거하고 application 상세 graph를 완성합니다. */
@Component
final class CatalogModelDetailAssembler {

  private static final String POWERTRAIN_ID = "powertrain_id";

  private final CatalogJsonConverter jsonConverter;

  CatalogModelDetailAssembler(CatalogJsonConverter jsonConverter) {
    this.jsonConverter = jsonConverter;
  }

  List<Powertrain> assemblePowertrains(List<CatalogProjectionRecord> rows) {
    LinkedHashMap<UUID, PowertrainBuilder> builders = new LinkedHashMap<>();
    rows.forEach(
        row -> {
          UUID powertrainId = row.getObject(POWERTRAIN_ID, UUID.class);
          PowertrainBuilder builder =
              builders.computeIfAbsent(powertrainId, ignored -> new PowertrainBuilder(row));
          builder.addComponent(row);
        });
    return builders.values().stream().map(PowertrainBuilder::build).toList();
  }

  List<Variant> assembleVariants(
      List<CatalogProjectionRecord> rows, Map<Long, List<SalesConfiguration>> configurations) {
    LinkedHashMap<Long, VariantBuilder> builders = new LinkedHashMap<>();
    rows.forEach(
        row -> {
          long internalId = row.getLong("variant_internal_id");
          VariantBuilder builder =
              builders.computeIfAbsent(internalId, ignored -> new VariantBuilder(row));
          builder.addCertification(row);
        });
    return builders.entrySet().stream()
        .map(
            entry -> entry.getValue().build(configurations.getOrDefault(entry.getKey(), List.of())))
        .toList();
  }

  Map<Long, List<SalesConfiguration>> assembleConfigurations(List<CatalogProjectionRecord> rows) {
    LinkedHashMap<Long, LinkedHashMap<UUID, ConfigurationBuilder>> byVariant =
        new LinkedHashMap<>();
    rows.forEach(
        row -> {
          long variantId = row.getLong("variant_internal_id");
          UUID configurationId = row.getObject("configuration_id", UUID.class);
          ConfigurationBuilder builder =
              byVariant
                  .computeIfAbsent(variantId, ignored -> new LinkedHashMap<>())
                  .computeIfAbsent(
                      configurationId, ignored -> new ConfigurationBuilder(row, jsonConverter));
          builder.addOption(row);
        });
    LinkedHashMap<Long, List<SalesConfiguration>> result = new LinkedHashMap<>();
    byVariant.forEach(
        (variantId, builders) ->
            result.put(
                variantId, builders.values().stream().map(ConfigurationBuilder::build).toList()));
    return result;
  }

  CatalogModelDetail assemble(
      Model model,
      List<CatalogModelYearSnapshot> yearSnapshots,
      ModelYear selectedModelYear,
      List<Powertrain> powertrains,
      List<Variant> variants) {
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
    List<ModelYear> modelYears =
        yearSnapshots.stream().map(CatalogModelYearSnapshot::modelYear).toList();

    return new CatalogModelDetail(
        model, generations, modelYears, selectedModelYear, powertrains, variants);
  }

  private static Integer integer(CatalogProjectionRecord resultSet, String column) {
    return resultSet.getObject(column, Integer.class);
  }

  private static BigDecimal decimal(CatalogProjectionRecord resultSet, String column) {
    return resultSet.getBigDecimal(column);
  }

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
    private final String acChargeConnector;
    private final Integer acChargeMinutes;
    private final Integer acChargeFromPercent;
    private final Integer acChargeToPercent;
    private final BigDecimal acChargePowerKw;
    private final BigDecimal maxDcChargeKw;
    private final String dcChargeConnector;
    private final Integer fastChargeMinutes;
    private final Integer fastChargeFromPercent;
    private final Integer fastChargeToPercent;
    private final BigDecimal fastChargePowerKw;
    private final List<PowertrainComponent> components = new ArrayList<>();

    private PowertrainBuilder(CatalogProjectionRecord resultSet) {
      powertrainId = resultSet.getObject(POWERTRAIN_ID, UUID.class);
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
      acChargeConnector = resultSet.getString("ac_charge_connector");
      acChargeMinutes = integer(resultSet, "ac_charge_minutes");
      acChargeFromPercent = integer(resultSet, "ac_charge_from_percent");
      acChargeToPercent = integer(resultSet, "ac_charge_to_percent");
      acChargePowerKw = decimal(resultSet, "ac_charge_power_kw");
      maxDcChargeKw = decimal(resultSet, "max_dc_charge_kw");
      dcChargeConnector = resultSet.getString("dc_charge_connector");
      fastChargeMinutes = integer(resultSet, "fast_charge_minutes");
      fastChargeFromPercent = integer(resultSet, "fast_charge_from_percent");
      fastChargeToPercent = integer(resultSet, "fast_charge_to_percent");
      fastChargePowerKw = decimal(resultSet, "fast_charge_power_kw");
    }

    private void addComponent(CatalogProjectionRecord resultSet) {
      if (resultSet.getString("component_type") == null) {
        return;
      }
      components.add(
          new PowertrainComponent(
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
              decimal(resultSet, "usable_capacity_kwh"),
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
          acChargeConnector,
          acChargeMinutes,
          acChargeFromPercent,
          acChargeToPercent,
          acChargePowerKw,
          maxDcChargeKw,
          dcChargeConnector,
          fastChargeMinutes,
          fastChargeFromPercent,
          fastChargeToPercent,
          fastChargePowerKw,
          components);
    }

    private static FuelType fuelType(String value) {
      return value == null ? null : FuelType.valueOf(value);
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
    private final Integer maxBrakedTowingCapacityKg;
    private final Integer maxUnbrakedTowingCapacityKg;
    private final Integer maxRoofLoadKg;
    private final BigDecimal minimumTurningRadiusM;
    private final BigDecimal wheelSizeInch;
    private final String frontTire;
    private final String rearTire;
    private final String frontSuspension;
    private final String rearSuspension;
    private final String steeringType;
    private final String frontBrake;
    private final String rearBrake;
    private final List<Certification> certifications = new ArrayList<>();

    private VariantBuilder(CatalogProjectionRecord resultSet) {
      variantId = resultSet.getObject("variant_id", UUID.class);
      powertrainId = resultSet.getObject(POWERTRAIN_ID, UUID.class);
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
      maxBrakedTowingCapacityKg = integer(resultSet, "max_braked_towing_capacity_kg");
      maxUnbrakedTowingCapacityKg = integer(resultSet, "max_unbraked_towing_capacity_kg");
      maxRoofLoadKg = integer(resultSet, "max_roof_load_kg");
      minimumTurningRadiusM = decimal(resultSet, "minimum_turning_radius_m");
      wheelSizeInch = decimal(resultSet, "wheel_size_inch");
      frontTire = resultSet.getString("front_tire");
      rearTire = resultSet.getString("rear_tire");
      frontSuspension = resultSet.getString("front_suspension");
      rearSuspension = resultSet.getString("rear_suspension");
      steeringType = resultSet.getString("steering_type");
      frontBrake = resultSet.getString("front_brake");
      rearBrake = resultSet.getString("rear_brake");
    }

    private void addCertification(CatalogProjectionRecord resultSet) {
      if (resultSet.getString("condition_code") == null) {
        return;
      }
      certifications.add(
          new Certification(
              resultSet.getString("condition_code"),
              resultSet.getString("condition_name"),
              resultSet.getString("certification_type"),
              resultSet.getString("certification_standard"),
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

    private Variant build(List<SalesConfiguration> configurations) {
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
          maxBrakedTowingCapacityKg,
          maxUnbrakedTowingCapacityKg,
          maxRoofLoadKg,
          minimumTurningRadiusM,
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
    private final CatalogJsonConverter jsonConverter;

    private ConfigurationBuilder(
        CatalogProjectionRecord resultSet, CatalogJsonConverter jsonConverter) {
      this.jsonConverter = jsonConverter;
      configurationId = resultSet.getObject("configuration_id", UUID.class);
      trimName = resultSet.getString("trim_name");
      salesChannel = resultSet.getString("sales_channel");
      salesStartedOn = resultSet.getObject("sales_started_on", LocalDate.class);
      salesEndedOn = resultSet.getObject("sales_ended_on", LocalDate.class);
      salesStatus = SalesStatus.valueOf(resultSet.getString("sales_status"));
      displayOrder = resultSet.getInt("display_order");
      standardFeatures = jsonConverter.standardFeatures(resultSet.getString("standard_features"));
      warranties = jsonConverter.warranties(resultSet.getString("warranties"));
      safetyRatings = jsonConverter.safetyRatings(resultSet.getString("safety_ratings"));
      media = jsonConverter.media(resultSet.getString("media"));
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
    }

    private void addOption(CatalogProjectionRecord resultSet) {
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
              jsonConverter.stringArray(resultSet.getString("included_items")),
              resultSet.getObject("option_effective_from", LocalDate.class),
              resultSet.getInt("option_display_order")));
    }

    private SalesConfiguration build() {
      return new SalesConfiguration(
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
}
