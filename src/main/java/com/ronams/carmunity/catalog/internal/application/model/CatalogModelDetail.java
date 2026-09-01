package com.ronams.carmunity.catalog.internal.application.model;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 모델·연식·동력계통·판매 구성을 내부 DB id 없이 묶은 공개 상세 읽기 모델입니다. */
public record CatalogModelDetail(
    Model model,
    List<Generation> generations,
    List<ModelYear> modelYears,
    ModelYear selectedModelYear,
    List<Powertrain> powertrains,
    List<Variant> variants) {

  public CatalogModelDetail {
    generations = List.copyOf(generations);
    modelYears = List.copyOf(modelYears);
    powertrains = List.copyOf(powertrains);
    variants = List.copyOf(variants);
  }

  public record Model(
      UUID modelId,
      UUID brandId,
      String brandName,
      String brandNameEn,
      String name,
      String nameEn,
      String summary) {}

  public record Generation(UUID generationId, String name, String changeType) {}

  public record ModelYear(
      UUID modelYearId,
      int modelYear,
      String editionName,
      LocalDate releasedOn,
      LocalDate salesStartedOn,
      LocalDate salesEndedOn,
      SalesStatus salesStatus) {}

  public record Powertrain(
      UUID powertrainId,
      String name,
      PropulsionType propulsionType,
      FuelType primaryFuelType,
      FuelType secondaryFuelType,
      String drivetrainType,
      String transmissionType,
      Integer transmissionGears,
      BigDecimal systemMaxPowerKw,
      BigDecimal systemMaxPowerPs,
      BigDecimal systemMaxTorqueNm,
      BigDecimal zeroToHundredSeconds,
      Integer topSpeedKmh,
      BigDecimal maxAcChargeKw,
      String acChargeConnector,
      Integer acChargeMinutes,
      Integer acChargeFromPercent,
      Integer acChargeToPercent,
      BigDecimal acChargePowerKw,
      BigDecimal maxDcChargeKw,
      String dcChargeConnector,
      Integer fastChargeMinutes,
      Integer fastChargeFromPercent,
      Integer fastChargeToPercent,
      BigDecimal fastChargePowerKw,
      List<PowertrainComponent> components) {

    public Powertrain {
      components = List.copyOf(components);
    }
  }

  public record PowertrainComponent(
      String componentType,
      String componentRole,
      String position,
      String name,
      FuelType fuelType,
      Integer displacementCc,
      Integer cylinderCount,
      String aspirationType,
      BigDecimal maxPowerKw,
      BigDecimal maxPowerPs,
      Integer maxPowerRpm,
      BigDecimal maxTorqueNm,
      BigDecimal maxTorqueKgfM,
      Integer maxTorqueRpmFrom,
      Integer maxTorqueRpmTo,
      BigDecimal capacityKwh,
      BigDecimal usableCapacityKwh,
      BigDecimal capacityAh,
      BigDecimal voltageV,
      BigDecimal capacityL,
      BigDecimal capacityKg,
      BigDecimal pressureBar,
      String manufacturerName,
      String cellManufacturerName,
      String productionCountryCode,
      String cellType,
      String chemistry) {}

  public record Variant(
      UUID variantId,
      UUID powertrainId,
      String name,
      BodyType bodyType,
      String roofType,
      int seatingCapacity,
      Integer doorCount,
      int lengthMm,
      int widthMm,
      int heightMm,
      int wheelbaseMm,
      Integer frontTrackMm,
      Integer rearTrackMm,
      Integer groundClearanceMm,
      BigDecimal cargoCapacityL,
      BigDecimal maxCargoCapacityL,
      Integer curbWeightKg,
      Integer grossVehicleWeightKg,
      Integer maxBrakedTowingCapacityKg,
      Integer maxUnbrakedTowingCapacityKg,
      Integer maxRoofLoadKg,
      BigDecimal minimumTurningRadiusM,
      BigDecimal wheelSizeInch,
      String frontTire,
      String rearTire,
      String frontSuspension,
      String rearSuspension,
      String steeringType,
      String frontBrake,
      String rearBrake,
      List<Certification> certifications,
      List<SalesConfiguration> configurations) {

    public Variant {
      certifications = List.copyOf(certifications);
      configurations = List.copyOf(configurations);
    }
  }

  public record Certification(
      String conditionCode,
      String conditionName,
      String certificationType,
      String certificationStandard,
      String temperatureCondition,
      BigDecimal wheelSizeInch,
      String tireSpecification,
      boolean builtInCamera,
      Integer curbWeightKg,
      Integer efficiencyGrade,
      BigDecimal cityEfficiency,
      BigDecimal highwayEfficiency,
      BigDecimal combinedEfficiency,
      String efficiencyUnit,
      BigDecimal cityRangeKm,
      BigDecimal highwayRangeKm,
      BigDecimal combinedRangeKm,
      BigDecimal co2GPerKm) {}

  public record SalesConfiguration(
      UUID configurationId,
      String trimName,
      String salesChannel,
      LocalDate salesStartedOn,
      LocalDate salesEndedOn,
      SalesStatus salesStatus,
      int displayOrder,
      List<StandardFeature> standardFeatures,
      List<Warranty> warranties,
      List<SafetyRating> safetyRatings,
      List<Media> media,
      Price price,
      List<Option> options) {

    public SalesConfiguration {
      standardFeatures = List.copyOf(standardFeatures);
      warranties = List.copyOf(warranties);
      safetyRatings = List.copyOf(safetyRatings);
      media = List.copyOf(media);
      options = List.copyOf(options);
    }
  }

  public record Price(
      LocalDate effectiveFrom,
      long priceBeforeTaxBenefit,
      Long priceAfterTaxBenefit,
      long displayPrice,
      CatalogPriceBasis priceBasis,
      String currencyCode) {}

  public record Option(
      String code,
      String name,
      String optionType,
      String category,
      long priceBeforeTaxBenefit,
      Long priceAfterTaxBenefit,
      long displayPrice,
      CatalogPriceBasis priceBasis,
      String currencyCode,
      List<String> includedItems,
      LocalDate effectiveFrom,
      int displayOrder) {

    public Option {
      includedItems = List.copyOf(includedItems);
    }
  }

  public record StandardFeature(String category, String name, String description) {}

  public record Warranty(String name, Integer years, Integer distanceKm, String note) {}

  public record SafetyRating(
      String organization, String rating, LocalDate assessedOn, String note) {}

  public record Media(String type, String url, String alt, int sortOrder) {}
}
