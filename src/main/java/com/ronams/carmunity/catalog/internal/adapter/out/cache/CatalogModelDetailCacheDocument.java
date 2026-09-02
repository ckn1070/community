package com.ronams.carmunity.catalog.internal.adapter.out.cache;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogPriceBasis;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Redis wire schema와 application read model 사이의 명시적 mapping 경계입니다. */
record CatalogModelDetailCacheDocument(
    int schemaVersion,
    UUID modelId,
    UUID requestedModelYearId,
    LocalDate asOf,
    Instant cachedAt,
    Instant expiresAt,
    Detail detail) {

  private static final int CURRENT_SCHEMA_VERSION = 1;

  static CatalogModelDetailCacheDocument from(
      GetCatalogModelDetailQuery query,
      Instant cachedAt,
      Instant expiresAt,
      CatalogModelDetail detail) {
    return new CatalogModelDetailCacheDocument(
        CURRENT_SCHEMA_VERSION,
        query.modelId(),
        query.requestedModelYearId(),
        query.asOf(),
        cachedAt,
        expiresAt,
        Detail.from(detail));
  }

  boolean matches(GetCatalogModelDetailQuery query, Instant now) {
    return schemaVersion == CURRENT_SCHEMA_VERSION
        && Objects.equals(modelId, query.modelId())
        && Objects.equals(requestedModelYearId, query.requestedModelYearId())
        && Objects.equals(asOf, query.asOf())
        && cachedAt != null
        && expiresAt != null
        && cachedAt.isBefore(expiresAt)
        && expiresAt.isAfter(now)
        && detail != null
        && detail.isValidFor(query);
  }

  CatalogModelDetail toModel() {
    return detail.toModel();
  }

  record Detail(
      Model model,
      List<Generation> generations,
      List<ModelYear> modelYears,
      ModelYear selectedModelYear,
      List<Powertrain> powertrains,
      List<Variant> variants) {

    static Detail from(CatalogModelDetail source) {
      return new Detail(
          Model.from(source.model()),
          source.generations().stream().map(Generation::from).toList(),
          source.modelYears().stream().map(ModelYear::from).toList(),
          source.selectedModelYear() == null ? null : ModelYear.from(source.selectedModelYear()),
          source.powertrains().stream().map(Powertrain::from).toList(),
          source.variants().stream().map(Variant::from).toList());
    }

    boolean isValidFor(GetCatalogModelDetailQuery query) {
      if (model == null
          || !Objects.equals(model.modelId(), query.modelId())
          || model.brandId() == null
          || isBlank(model.brandName())
          || isBlank(model.name())
          || !hasNoNulls(generations)
          || !hasNoNulls(modelYears)
          || !hasNoNulls(powertrains)
          || !hasNoNulls(variants)
          || selectedModelYear == null
          || selectedModelYear.modelYearId() == null
          || (query.requestedModelYearId() != null
              && !Objects.equals(selectedModelYear.modelYearId(), query.requestedModelYearId()))) {
        return false;
      }
      return validList(generations, Generation::isValid)
          && validList(modelYears, ModelYear::isValid)
          && modelYears.contains(selectedModelYear)
          && validList(powertrains, Powertrain::isValid)
          && validList(variants, Variant::isValid);
    }

    CatalogModelDetail toModel() {
      return new CatalogModelDetail(
          require(model, "model").toModel(),
          requireList(generations, "generations").stream().map(Generation::toModel).toList(),
          requireList(modelYears, "modelYears").stream().map(ModelYear::toModel).toList(),
          selectedModelYear == null ? null : selectedModelYear.toModel(),
          requireList(powertrains, "powertrains").stream().map(Powertrain::toModel).toList(),
          requireList(variants, "variants").stream().map(Variant::toModel).toList());
    }
  }

  record Model(
      UUID modelId,
      UUID brandId,
      String brandName,
      String brandNameEn,
      String name,
      String nameEn,
      String summary) {

    static Model from(CatalogModelDetail.Model source) {
      return new Model(
          source.modelId(),
          source.brandId(),
          source.brandName(),
          source.brandNameEn(),
          source.name(),
          source.nameEn(),
          source.summary());
    }

    CatalogModelDetail.Model toModel() {
      return new CatalogModelDetail.Model(
          modelId, brandId, brandName, brandNameEn, name, nameEn, summary);
    }
  }

  record Generation(UUID generationId, String name, String changeType) {
    boolean isValid() {
      return hasRequired(generationId, name, changeType);
    }

    static Generation from(CatalogModelDetail.Generation source) {
      return new Generation(source.generationId(), source.name(), source.changeType());
    }

    CatalogModelDetail.Generation toModel() {
      return new CatalogModelDetail.Generation(generationId, name, changeType);
    }
  }

  record ModelYear(
      UUID modelYearId,
      Integer modelYear,
      String editionName,
      LocalDate releasedOn,
      LocalDate salesStartedOn,
      LocalDate salesEndedOn,
      SalesStatus salesStatus) {

    boolean isValid() {
      return hasRequired(modelYearId, modelYear, editionName, salesStartedOn, salesStatus);
    }

    static ModelYear from(CatalogModelDetail.ModelYear source) {
      return new ModelYear(
          source.modelYearId(),
          source.modelYear(),
          source.editionName(),
          source.releasedOn(),
          source.salesStartedOn(),
          source.salesEndedOn(),
          source.salesStatus());
    }

    CatalogModelDetail.ModelYear toModel() {
      return new CatalogModelDetail.ModelYear(
          modelYearId,
          modelYear,
          editionName,
          releasedOn,
          salesStartedOn,
          salesEndedOn,
          salesStatus);
    }
  }

  record Powertrain(
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

    boolean isValid() {
      return hasRequired(powertrainId, name, propulsionType, primaryFuelType, drivetrainType)
          && validList(components, PowertrainComponent::isValid);
    }

    static Powertrain from(CatalogModelDetail.Powertrain source) {
      return new Powertrain(
          source.powertrainId(),
          source.name(),
          source.propulsionType(),
          source.primaryFuelType(),
          source.secondaryFuelType(),
          source.drivetrainType(),
          source.transmissionType(),
          source.transmissionGears(),
          source.systemMaxPowerKw(),
          source.systemMaxPowerPs(),
          source.systemMaxTorqueNm(),
          source.zeroToHundredSeconds(),
          source.topSpeedKmh(),
          source.maxAcChargeKw(),
          source.acChargeConnector(),
          source.acChargeMinutes(),
          source.acChargeFromPercent(),
          source.acChargeToPercent(),
          source.acChargePowerKw(),
          source.maxDcChargeKw(),
          source.dcChargeConnector(),
          source.fastChargeMinutes(),
          source.fastChargeFromPercent(),
          source.fastChargeToPercent(),
          source.fastChargePowerKw(),
          source.components().stream().map(PowertrainComponent::from).toList());
    }

    CatalogModelDetail.Powertrain toModel() {
      return new CatalogModelDetail.Powertrain(
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
          requireList(components, "powertrain.components").stream()
              .map(PowertrainComponent::toModel)
              .toList());
    }
  }

  record PowertrainComponent(
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
      String chemistry) {

    boolean isValid() {
      return hasRequired(componentType, componentRole, position, name);
    }

    static PowertrainComponent from(CatalogModelDetail.PowertrainComponent source) {
      return new PowertrainComponent(
          source.componentType(),
          source.componentRole(),
          source.position(),
          source.name(),
          source.fuelType(),
          source.displacementCc(),
          source.cylinderCount(),
          source.aspirationType(),
          source.maxPowerKw(),
          source.maxPowerPs(),
          source.maxPowerRpm(),
          source.maxTorqueNm(),
          source.maxTorqueKgfM(),
          source.maxTorqueRpmFrom(),
          source.maxTorqueRpmTo(),
          source.capacityKwh(),
          source.usableCapacityKwh(),
          source.capacityAh(),
          source.voltageV(),
          source.capacityL(),
          source.capacityKg(),
          source.pressureBar(),
          source.manufacturerName(),
          source.cellManufacturerName(),
          source.productionCountryCode(),
          source.cellType(),
          source.chemistry());
    }

    CatalogModelDetail.PowertrainComponent toModel() {
      return new CatalogModelDetail.PowertrainComponent(
          componentType,
          componentRole,
          position,
          name,
          fuelType,
          displacementCc,
          cylinderCount,
          aspirationType,
          maxPowerKw,
          maxPowerPs,
          maxPowerRpm,
          maxTorqueNm,
          maxTorqueKgfM,
          maxTorqueRpmFrom,
          maxTorqueRpmTo,
          capacityKwh,
          usableCapacityKwh,
          capacityAh,
          voltageV,
          capacityL,
          capacityKg,
          pressureBar,
          manufacturerName,
          cellManufacturerName,
          productionCountryCode,
          cellType,
          chemistry);
    }
  }

  record Variant(
      UUID variantId,
      UUID powertrainId,
      String name,
      BodyType bodyType,
      String roofType,
      Integer seatingCapacity,
      Integer doorCount,
      Integer lengthMm,
      Integer widthMm,
      Integer heightMm,
      Integer wheelbaseMm,
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

    boolean isValid() {
      return hasRequired(
              variantId,
              powertrainId,
              name,
              bodyType,
              roofType,
              seatingCapacity,
              lengthMm,
              widthMm,
              heightMm,
              wheelbaseMm)
          && validList(certifications, Certification::isValid)
          && validList(configurations, SalesConfiguration::isValid);
    }

    static Variant from(CatalogModelDetail.Variant source) {
      return new Variant(
          source.variantId(),
          source.powertrainId(),
          source.name(),
          source.bodyType(),
          source.roofType(),
          source.seatingCapacity(),
          source.doorCount(),
          source.lengthMm(),
          source.widthMm(),
          source.heightMm(),
          source.wheelbaseMm(),
          source.frontTrackMm(),
          source.rearTrackMm(),
          source.groundClearanceMm(),
          source.cargoCapacityL(),
          source.maxCargoCapacityL(),
          source.curbWeightKg(),
          source.grossVehicleWeightKg(),
          source.maxBrakedTowingCapacityKg(),
          source.maxUnbrakedTowingCapacityKg(),
          source.maxRoofLoadKg(),
          source.minimumTurningRadiusM(),
          source.wheelSizeInch(),
          source.frontTire(),
          source.rearTire(),
          source.frontSuspension(),
          source.rearSuspension(),
          source.steeringType(),
          source.frontBrake(),
          source.rearBrake(),
          source.certifications().stream().map(Certification::from).toList(),
          source.configurations().stream().map(SalesConfiguration::from).toList());
    }

    CatalogModelDetail.Variant toModel() {
      return new CatalogModelDetail.Variant(
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
          requireList(certifications, "variant.certifications").stream()
              .map(Certification::toModel)
              .toList(),
          requireList(configurations, "variant.configurations").stream()
              .map(SalesConfiguration::toModel)
              .toList());
    }
  }

  record Certification(
      String conditionCode,
      String conditionName,
      String certificationType,
      String certificationStandard,
      String temperatureCondition,
      BigDecimal wheelSizeInch,
      String tireSpecification,
      Boolean builtInCamera,
      Integer curbWeightKg,
      Integer efficiencyGrade,
      BigDecimal cityEfficiency,
      BigDecimal highwayEfficiency,
      BigDecimal combinedEfficiency,
      String efficiencyUnit,
      BigDecimal cityRangeKm,
      BigDecimal highwayRangeKm,
      BigDecimal combinedRangeKm,
      BigDecimal co2GPerKm) {

    boolean isValid() {
      return hasRequired(
          conditionCode,
          conditionName,
          certificationType,
          certificationStandard,
          builtInCamera,
          efficiencyUnit);
    }

    static Certification from(CatalogModelDetail.Certification source) {
      return new Certification(
          source.conditionCode(),
          source.conditionName(),
          source.certificationType(),
          source.certificationStandard(),
          source.temperatureCondition(),
          source.wheelSizeInch(),
          source.tireSpecification(),
          source.builtInCamera(),
          source.curbWeightKg(),
          source.efficiencyGrade(),
          source.cityEfficiency(),
          source.highwayEfficiency(),
          source.combinedEfficiency(),
          source.efficiencyUnit(),
          source.cityRangeKm(),
          source.highwayRangeKm(),
          source.combinedRangeKm(),
          source.co2GPerKm());
    }

    CatalogModelDetail.Certification toModel() {
      return new CatalogModelDetail.Certification(
          conditionCode,
          conditionName,
          certificationType,
          certificationStandard,
          temperatureCondition,
          wheelSizeInch,
          tireSpecification,
          builtInCamera,
          curbWeightKg,
          efficiencyGrade,
          cityEfficiency,
          highwayEfficiency,
          combinedEfficiency,
          efficiencyUnit,
          cityRangeKm,
          highwayRangeKm,
          combinedRangeKm,
          co2GPerKm);
    }
  }

  record SalesConfiguration(
      UUID configurationId,
      String trimName,
      String salesChannel,
      LocalDate salesStartedOn,
      LocalDate salesEndedOn,
      SalesStatus salesStatus,
      Integer displayOrder,
      List<StandardFeature> standardFeatures,
      List<Warranty> warranties,
      List<SafetyRating> safetyRatings,
      List<Media> media,
      Price price,
      List<Option> options) {

    boolean isValid() {
      return hasRequired(
              configurationId, trimName, salesChannel, salesStartedOn, salesStatus, displayOrder)
          && validList(standardFeatures, StandardFeature::isValid)
          && validList(warranties, Warranty::isValid)
          && validList(safetyRatings, SafetyRating::isValid)
          && validList(media, Media::isValid)
          && (price == null || price.isValid())
          && validList(options, Option::isValid);
    }

    static SalesConfiguration from(CatalogModelDetail.SalesConfiguration source) {
      return new SalesConfiguration(
          source.configurationId(),
          source.trimName(),
          source.salesChannel(),
          source.salesStartedOn(),
          source.salesEndedOn(),
          source.salesStatus(),
          source.displayOrder(),
          source.standardFeatures().stream().map(StandardFeature::from).toList(),
          source.warranties().stream().map(Warranty::from).toList(),
          source.safetyRatings().stream().map(SafetyRating::from).toList(),
          source.media().stream().map(Media::from).toList(),
          source.price() == null ? null : Price.from(source.price()),
          source.options().stream().map(Option::from).toList());
    }

    CatalogModelDetail.SalesConfiguration toModel() {
      return new CatalogModelDetail.SalesConfiguration(
          configurationId,
          trimName,
          salesChannel,
          salesStartedOn,
          salesEndedOn,
          salesStatus,
          displayOrder,
          requireList(standardFeatures, "configuration.standardFeatures").stream()
              .map(StandardFeature::toModel)
              .toList(),
          requireList(warranties, "configuration.warranties").stream()
              .map(Warranty::toModel)
              .toList(),
          requireList(safetyRatings, "configuration.safetyRatings").stream()
              .map(SafetyRating::toModel)
              .toList(),
          requireList(media, "configuration.media").stream().map(Media::toModel).toList(),
          price == null ? null : price.toModel(),
          requireList(options, "configuration.options").stream().map(Option::toModel).toList());
    }
  }

  record Price(
      LocalDate effectiveFrom,
      Long priceBeforeTaxBenefit,
      Long priceAfterTaxBenefit,
      Long displayPrice,
      CatalogPriceBasis priceBasis,
      String currencyCode) {

    boolean isValid() {
      return hasRequired(
          effectiveFrom, priceBeforeTaxBenefit, displayPrice, priceBasis, currencyCode);
    }

    static Price from(CatalogModelDetail.Price source) {
      return new Price(
          source.effectiveFrom(),
          source.priceBeforeTaxBenefit(),
          source.priceAfterTaxBenefit(),
          source.displayPrice(),
          source.priceBasis(),
          source.currencyCode());
    }

    CatalogModelDetail.Price toModel() {
      return new CatalogModelDetail.Price(
          effectiveFrom,
          priceBeforeTaxBenefit,
          priceAfterTaxBenefit,
          displayPrice,
          priceBasis,
          currencyCode);
    }
  }

  record Option(
      String code,
      String name,
      String optionType,
      String category,
      Long priceBeforeTaxBenefit,
      Long priceAfterTaxBenefit,
      Long displayPrice,
      CatalogPriceBasis priceBasis,
      String currencyCode,
      List<String> includedItems,
      LocalDate effectiveFrom,
      Integer displayOrder) {

    boolean isValid() {
      return hasRequired(
              code,
              name,
              optionType,
              priceBeforeTaxBenefit,
              displayPrice,
              priceBasis,
              currencyCode,
              effectiveFrom,
              displayOrder)
          && hasNoNulls(includedItems);
    }

    static Option from(CatalogModelDetail.Option source) {
      return new Option(
          source.code(),
          source.name(),
          source.optionType(),
          source.category(),
          source.priceBeforeTaxBenefit(),
          source.priceAfterTaxBenefit(),
          source.displayPrice(),
          source.priceBasis(),
          source.currencyCode(),
          source.includedItems(),
          source.effectiveFrom(),
          source.displayOrder());
    }

    CatalogModelDetail.Option toModel() {
      return new CatalogModelDetail.Option(
          code,
          name,
          optionType,
          category,
          priceBeforeTaxBenefit,
          priceAfterTaxBenefit,
          displayPrice,
          priceBasis,
          currencyCode,
          requireList(includedItems, "option.includedItems"),
          effectiveFrom,
          displayOrder);
    }
  }

  record StandardFeature(String category, String name, String description) {
    boolean isValid() {
      return hasRequired(category, name);
    }

    static StandardFeature from(CatalogModelDetail.StandardFeature source) {
      return new StandardFeature(source.category(), source.name(), source.description());
    }

    CatalogModelDetail.StandardFeature toModel() {
      return new CatalogModelDetail.StandardFeature(category, name, description);
    }
  }

  record Warranty(String name, Integer years, Integer distanceKm, String note) {
    boolean isValid() {
      return name != null;
    }

    static Warranty from(CatalogModelDetail.Warranty source) {
      return new Warranty(source.name(), source.years(), source.distanceKm(), source.note());
    }

    CatalogModelDetail.Warranty toModel() {
      return new CatalogModelDetail.Warranty(name, years, distanceKm, note);
    }
  }

  record SafetyRating(String organization, String rating, LocalDate assessedOn, String note) {
    boolean isValid() {
      return hasRequired(organization, rating);
    }

    static SafetyRating from(CatalogModelDetail.SafetyRating source) {
      return new SafetyRating(
          source.organization(), source.rating(), source.assessedOn(), source.note());
    }

    CatalogModelDetail.SafetyRating toModel() {
      return new CatalogModelDetail.SafetyRating(organization, rating, assessedOn, note);
    }
  }

  record Media(String type, String url, String alt, Integer sortOrder) {
    boolean isValid() {
      return hasRequired(type, url, sortOrder);
    }

    static Media from(CatalogModelDetail.Media source) {
      return new Media(source.type(), source.url(), source.alt(), source.sortOrder());
    }

    CatalogModelDetail.Media toModel() {
      return new CatalogModelDetail.Media(type, url, alt, sortOrder);
    }
  }

  private static <T> T require(T value, String field) {
    if (value == null) {
      throw new IllegalArgumentException("Cache document 필수 field가 없습니다: " + field);
    }
    return value;
  }

  private static <T> List<T> requireList(List<T> value, String field) {
    return List.copyOf(require(value, field));
  }

  private static boolean hasNoNulls(List<?> values) {
    return values != null && values.stream().noneMatch(Objects::isNull);
  }

  private static <T> boolean validList(List<T> values, Predicate<T> valid) {
    return hasNoNulls(values) && values.stream().allMatch(valid);
  }

  // Wire의 primitive도 boxed type으로 받아 누락을 0·false 정상값과 구분합니다.
  private static boolean hasRequired(Object... values) {
    for (Object value : values) {
      if (value == null) {
        return false;
      }
    }
    return true;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
