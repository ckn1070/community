package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogPriceBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 모델·연식·동력계통·판매 구성을 application 내부 type과 분리해 제공하는 상세 응답입니다. */
@Schema(
    description = "카탈로그 모델 상세",
    requiredProperties = {
      "model",
      "generations",
      "modelYears",
      "selectedModelYear",
      "powertrains",
      "variants"
    })
public record CatalogModelDetailResponse(
    ModelResponse model,
    List<GenerationResponse> generations,
    List<ModelYearResponse> modelYears,
    ModelYearResponse selectedModelYear,
    List<PowertrainResponse> powertrains,
    List<VariantResponse> variants) {

  static CatalogModelDetailResponse from(CatalogModelDetail detail) {
    return new CatalogModelDetailResponse(
        ModelResponse.from(detail.model()),
        detail.generations().stream().map(GenerationResponse::from).toList(),
        detail.modelYears().stream().map(ModelYearResponse::from).toList(),
        ModelYearResponse.from(detail.selectedModelYear()),
        detail.powertrains().stream().map(PowertrainResponse::from).toList(),
        detail.variants().stream().map(VariantResponse::from).toList());
  }

  @Schema(requiredProperties = {"modelId", "brandId", "brandName", "name"})
  public record ModelResponse(
      UUID modelId,
      UUID brandId,
      String brandName,
      @Schema(types = {"string", "null"}) String brandNameEn,
      String name,
      @Schema(types = {"string", "null"}) String nameEn,
      @Schema(types = {"string", "null"}) String summary) {

    static ModelResponse from(CatalogModelDetail.Model model) {
      return new ModelResponse(
          model.modelId(),
          model.brandId(),
          model.brandName(),
          model.brandNameEn(),
          model.name(),
          model.nameEn(),
          model.summary());
    }
  }

  @Schema(requiredProperties = {"generationId", "name", "changeType"})
  public record GenerationResponse(UUID generationId, String name, String changeType) {

    static GenerationResponse from(CatalogModelDetail.Generation generation) {
      return new GenerationResponse(
          generation.generationId(), generation.name(), generation.changeType());
    }
  }

  @Schema(
      requiredProperties = {
        "modelYearId",
        "modelYear",
        "editionName",
        "salesStartedOn",
        "salesStatus"
      })
  public record ModelYearResponse(
      UUID modelYearId,
      int modelYear,
      String editionName,
      @Schema(types = {"string", "null"}) LocalDate releasedOn,
      LocalDate salesStartedOn,
      @Schema(types = {"string", "null"}) LocalDate salesEndedOn,
      SalesStatus salesStatus) {

    static ModelYearResponse from(CatalogModelDetail.ModelYear modelYear) {
      return new ModelYearResponse(
          modelYear.modelYearId(),
          modelYear.modelYear(),
          modelYear.editionName(),
          modelYear.releasedOn(),
          modelYear.salesStartedOn(),
          modelYear.salesEndedOn(),
          modelYear.salesStatus());
    }
  }

  @Schema(
      requiredProperties = {
        "powertrainId",
        "name",
        "propulsionType",
        "primaryFuelType",
        "drivetrainType",
        "components"
      })
  public record PowertrainResponse(
      UUID powertrainId,
      String name,
      PropulsionType propulsionType,
      FuelType primaryFuelType,
      @Schema(types = {"string", "null"}) FuelType secondaryFuelType,
      String drivetrainType,
      @Schema(types = {"string", "null"}) String transmissionType,
      @Schema(types = {"integer", "null"}) Integer transmissionGears,
      @Schema(types = {"number", "null"}) BigDecimal systemMaxPowerKw,
      @Schema(types = {"number", "null"}) BigDecimal systemMaxPowerPs,
      @Schema(types = {"number", "null"}) BigDecimal systemMaxTorqueNm,
      @Schema(types = {"number", "null"}) BigDecimal zeroToHundredSeconds,
      @Schema(types = {"integer", "null"}) Integer topSpeedKmh,
      @Schema(types = {"number", "null"}) BigDecimal maxAcChargeKw,
      @Schema(types = {"string", "null"}) String acChargeConnector,
      @Schema(types = {"integer", "null"}) Integer acChargeMinutes,
      @Schema(types = {"integer", "null"}) Integer acChargeFromPercent,
      @Schema(types = {"integer", "null"}) Integer acChargeToPercent,
      @Schema(types = {"number", "null"}) BigDecimal acChargePowerKw,
      @Schema(types = {"number", "null"}) BigDecimal maxDcChargeKw,
      @Schema(types = {"string", "null"}) String dcChargeConnector,
      @Schema(types = {"integer", "null"}) Integer fastChargeMinutes,
      @Schema(types = {"integer", "null"}) Integer fastChargeFromPercent,
      @Schema(types = {"integer", "null"}) Integer fastChargeToPercent,
      @Schema(types = {"number", "null"}) BigDecimal fastChargePowerKw,
      List<ComponentResponse> components) {

    static PowertrainResponse from(CatalogModelDetail.Powertrain powertrain) {
      return new PowertrainResponse(
          powertrain.powertrainId(),
          powertrain.name(),
          powertrain.propulsionType(),
          powertrain.primaryFuelType(),
          powertrain.secondaryFuelType(),
          powertrain.drivetrainType(),
          powertrain.transmissionType(),
          powertrain.transmissionGears(),
          powertrain.systemMaxPowerKw(),
          powertrain.systemMaxPowerPs(),
          powertrain.systemMaxTorqueNm(),
          powertrain.zeroToHundredSeconds(),
          powertrain.topSpeedKmh(),
          powertrain.maxAcChargeKw(),
          powertrain.acChargeConnector(),
          powertrain.acChargeMinutes(),
          powertrain.acChargeFromPercent(),
          powertrain.acChargeToPercent(),
          powertrain.acChargePowerKw(),
          powertrain.maxDcChargeKw(),
          powertrain.dcChargeConnector(),
          powertrain.fastChargeMinutes(),
          powertrain.fastChargeFromPercent(),
          powertrain.fastChargeToPercent(),
          powertrain.fastChargePowerKw(),
          powertrain.components().stream().map(ComponentResponse::from).toList());
    }
  }

  @Schema(requiredProperties = {"componentType", "componentRole", "position", "name"})
  public record ComponentResponse(
      String componentType,
      String componentRole,
      String position,
      String name,
      @Schema(types = {"string", "null"}) FuelType fuelType,
      @Schema(types = {"integer", "null"}) Integer displacementCc,
      @Schema(types = {"integer", "null"}) Integer cylinderCount,
      @Schema(types = {"string", "null"}) String aspirationType,
      @Schema(types = {"number", "null"}) BigDecimal maxPowerKw,
      @Schema(types = {"number", "null"}) BigDecimal maxPowerPs,
      @Schema(types = {"integer", "null"}) Integer maxPowerRpm,
      @Schema(types = {"number", "null"}) BigDecimal maxTorqueNm,
      @Schema(types = {"number", "null"}) BigDecimal maxTorqueKgfM,
      @Schema(types = {"integer", "null"}) Integer maxTorqueRpmFrom,
      @Schema(types = {"integer", "null"}) Integer maxTorqueRpmTo,
      @Schema(types = {"number", "null"}) BigDecimal capacityKwh,
      @Schema(types = {"number", "null"}) BigDecimal usableCapacityKwh,
      @Schema(types = {"number", "null"}) BigDecimal capacityAh,
      @Schema(types = {"number", "null"}) BigDecimal voltageV,
      @Schema(types = {"number", "null"}) BigDecimal capacityL,
      @Schema(types = {"number", "null"}) BigDecimal capacityKg,
      @Schema(types = {"number", "null"}) BigDecimal pressureBar,
      @Schema(types = {"string", "null"}) String manufacturerName,
      @Schema(types = {"string", "null"}) String cellManufacturerName,
      @Schema(types = {"string", "null"}) String productionCountryCode,
      @Schema(types = {"string", "null"}) String cellType,
      @Schema(types = {"string", "null"}) String chemistry) {

    static ComponentResponse from(CatalogModelDetail.Component component) {
      return new ComponentResponse(
          component.componentType(),
          component.componentRole(),
          component.position(),
          component.name(),
          component.fuelType(),
          component.displacementCc(),
          component.cylinderCount(),
          component.aspirationType(),
          component.maxPowerKw(),
          component.maxPowerPs(),
          component.maxPowerRpm(),
          component.maxTorqueNm(),
          component.maxTorqueKgfM(),
          component.maxTorqueRpmFrom(),
          component.maxTorqueRpmTo(),
          component.capacityKwh(),
          component.usableCapacityKwh(),
          component.capacityAh(),
          component.voltageV(),
          component.capacityL(),
          component.capacityKg(),
          component.pressureBar(),
          component.manufacturerName(),
          component.cellManufacturerName(),
          component.productionCountryCode(),
          component.cellType(),
          component.chemistry());
    }
  }

  @Schema(
      requiredProperties = {
        "variantId",
        "powertrainId",
        "name",
        "bodyType",
        "roofType",
        "seatingCapacity",
        "lengthMm",
        "widthMm",
        "heightMm",
        "wheelbaseMm",
        "certifications",
        "configurations"
      })
  public record VariantResponse(
      UUID variantId,
      UUID powertrainId,
      String name,
      BodyType bodyType,
      String roofType,
      int seatingCapacity,
      @Schema(types = {"integer", "null"}) Integer doorCount,
      int lengthMm,
      int widthMm,
      int heightMm,
      int wheelbaseMm,
      @Schema(types = {"integer", "null"}) Integer frontTrackMm,
      @Schema(types = {"integer", "null"}) Integer rearTrackMm,
      @Schema(types = {"integer", "null"}) Integer groundClearanceMm,
      @Schema(types = {"number", "null"}) BigDecimal cargoCapacityL,
      @Schema(types = {"number", "null"}) BigDecimal maxCargoCapacityL,
      @Schema(types = {"integer", "null"}) Integer curbWeightKg,
      @Schema(types = {"integer", "null"}) Integer grossVehicleWeightKg,
      @Schema(types = {"integer", "null"}) Integer maxBrakedTowingCapacityKg,
      @Schema(types = {"integer", "null"}) Integer maxUnbrakedTowingCapacityKg,
      @Schema(types = {"integer", "null"}) Integer maxRoofLoadKg,
      @Schema(types = {"number", "null"}) BigDecimal minimumTurningRadiusM,
      @Schema(types = {"number", "null"}) BigDecimal wheelSizeInch,
      @Schema(types = {"string", "null"}) String frontTire,
      @Schema(types = {"string", "null"}) String rearTire,
      @Schema(types = {"string", "null"}) String frontSuspension,
      @Schema(types = {"string", "null"}) String rearSuspension,
      @Schema(types = {"string", "null"}) String steeringType,
      @Schema(types = {"string", "null"}) String frontBrake,
      @Schema(types = {"string", "null"}) String rearBrake,
      List<CertificationResponse> certifications,
      List<ConfigurationResponse> configurations) {

    static VariantResponse from(CatalogModelDetail.Variant variant) {
      return new VariantResponse(
          variant.variantId(),
          variant.powertrainId(),
          variant.name(),
          variant.bodyType(),
          variant.roofType(),
          variant.seatingCapacity(),
          variant.doorCount(),
          variant.lengthMm(),
          variant.widthMm(),
          variant.heightMm(),
          variant.wheelbaseMm(),
          variant.frontTrackMm(),
          variant.rearTrackMm(),
          variant.groundClearanceMm(),
          variant.cargoCapacityL(),
          variant.maxCargoCapacityL(),
          variant.curbWeightKg(),
          variant.grossVehicleWeightKg(),
          variant.maxBrakedTowingCapacityKg(),
          variant.maxUnbrakedTowingCapacityKg(),
          variant.maxRoofLoadKg(),
          variant.minimumTurningRadiusM(),
          variant.wheelSizeInch(),
          variant.frontTire(),
          variant.rearTire(),
          variant.frontSuspension(),
          variant.rearSuspension(),
          variant.steeringType(),
          variant.frontBrake(),
          variant.rearBrake(),
          variant.certifications().stream().map(CertificationResponse::from).toList(),
          variant.configurations().stream().map(ConfigurationResponse::from).toList());
    }
  }

  @Schema(
      requiredProperties = {
        "conditionCode",
        "conditionName",
        "certificationType",
        "certificationStandard",
        "builtInCamera",
        "efficiencyUnit"
      })
  public record CertificationResponse(
      String conditionCode,
      String conditionName,
      String certificationType,
      String certificationStandard,
      @Schema(types = {"string", "null"}) String temperatureCondition,
      @Schema(types = {"number", "null"}) BigDecimal wheelSizeInch,
      @Schema(types = {"string", "null"}) String tireSpecification,
      boolean builtInCamera,
      @Schema(types = {"integer", "null"}) Integer curbWeightKg,
      @Schema(types = {"integer", "null"}) Integer efficiencyGrade,
      @Schema(types = {"number", "null"}) BigDecimal cityEfficiency,
      @Schema(types = {"number", "null"}) BigDecimal highwayEfficiency,
      @Schema(types = {"number", "null"}) BigDecimal combinedEfficiency,
      String efficiencyUnit,
      @Schema(types = {"number", "null"}) BigDecimal cityRangeKm,
      @Schema(types = {"number", "null"}) BigDecimal highwayRangeKm,
      @Schema(types = {"number", "null"}) BigDecimal combinedRangeKm,
      @Schema(types = {"number", "null"}) BigDecimal co2GPerKm) {

    static CertificationResponse from(CatalogModelDetail.Certification certification) {
      return new CertificationResponse(
          certification.conditionCode(),
          certification.conditionName(),
          certification.certificationType(),
          certification.certificationStandard(),
          certification.temperatureCondition(),
          certification.wheelSizeInch(),
          certification.tireSpecification(),
          certification.builtInCamera(),
          certification.curbWeightKg(),
          certification.efficiencyGrade(),
          certification.cityEfficiency(),
          certification.highwayEfficiency(),
          certification.combinedEfficiency(),
          certification.efficiencyUnit(),
          certification.cityRangeKm(),
          certification.highwayRangeKm(),
          certification.combinedRangeKm(),
          certification.co2GPerKm());
    }
  }

  @Schema(
      requiredProperties = {
        "configurationId",
        "trimName",
        "salesChannel",
        "salesStartedOn",
        "salesStatus",
        "displayOrder",
        "standardFeatures",
        "warranties",
        "safetyRatings",
        "media",
        "options"
      })
  public record ConfigurationResponse(
      UUID configurationId,
      String trimName,
      String salesChannel,
      LocalDate salesStartedOn,
      @Schema(types = {"string", "null"}) LocalDate salesEndedOn,
      SalesStatus salesStatus,
      int displayOrder,
      List<StandardFeatureResponse> standardFeatures,
      List<WarrantyResponse> warranties,
      List<SafetyRatingResponse> safetyRatings,
      List<MediaResponse> media,
      @Schema(types = {"object", "null"}) PriceResponse price,
      List<OptionResponse> options) {

    static ConfigurationResponse from(CatalogModelDetail.Configuration configuration) {
      return new ConfigurationResponse(
          configuration.configurationId(),
          configuration.trimName(),
          configuration.salesChannel(),
          configuration.salesStartedOn(),
          configuration.salesEndedOn(),
          configuration.salesStatus(),
          configuration.displayOrder(),
          configuration.standardFeatures().stream().map(StandardFeatureResponse::from).toList(),
          configuration.warranties().stream().map(WarrantyResponse::from).toList(),
          configuration.safetyRatings().stream().map(SafetyRatingResponse::from).toList(),
          configuration.media().stream().map(MediaResponse::from).toList(),
          configuration.price() == null ? null : PriceResponse.from(configuration.price()),
          configuration.options().stream().map(OptionResponse::from).toList());
    }
  }

  @Schema(
      requiredProperties = {
        "effectiveFrom",
        "priceBeforeTaxBenefit",
        "displayPrice",
        "priceBasis",
        "currencyCode"
      })
  public record PriceResponse(
      LocalDate effectiveFrom,
      long priceBeforeTaxBenefit,
      @Schema(types = {"integer", "null"}) Long priceAfterTaxBenefit,
      long displayPrice,
      CatalogPriceBasis priceBasis,
      String currencyCode) {

    static PriceResponse from(CatalogModelDetail.Price price) {
      return new PriceResponse(
          price.effectiveFrom(),
          price.priceBeforeTaxBenefit(),
          price.priceAfterTaxBenefit(),
          price.displayPrice(),
          price.priceBasis(),
          price.currencyCode());
    }
  }

  @Schema(
      requiredProperties = {
        "code",
        "name",
        "optionType",
        "priceBeforeTaxBenefit",
        "displayPrice",
        "priceBasis",
        "currencyCode",
        "includedItems",
        "effectiveFrom",
        "displayOrder"
      })
  public record OptionResponse(
      String code,
      String name,
      String optionType,
      @Schema(types = {"string", "null"}) String category,
      long priceBeforeTaxBenefit,
      @Schema(types = {"integer", "null"}) Long priceAfterTaxBenefit,
      long displayPrice,
      CatalogPriceBasis priceBasis,
      String currencyCode,
      List<String> includedItems,
      LocalDate effectiveFrom,
      int displayOrder) {

    static OptionResponse from(CatalogModelDetail.Option option) {
      return new OptionResponse(
          option.code(),
          option.name(),
          option.optionType(),
          option.category(),
          option.priceBeforeTaxBenefit(),
          option.priceAfterTaxBenefit(),
          option.displayPrice(),
          option.priceBasis(),
          option.currencyCode(),
          option.includedItems(),
          option.effectiveFrom(),
          option.displayOrder());
    }
  }

  @Schema(requiredProperties = {"category", "name"})
  public record StandardFeatureResponse(
      String category, String name, @Schema(types = {"string", "null"}) String description) {
    static StandardFeatureResponse from(CatalogModelDetail.StandardFeature feature) {
      return new StandardFeatureResponse(feature.category(), feature.name(), feature.description());
    }
  }

  @Schema(requiredProperties = {"name"})
  public record WarrantyResponse(
      String name,
      @Schema(types = {"integer", "null"}) Integer years,
      @Schema(types = {"integer", "null"}) Integer distanceKm,
      @Schema(types = {"string", "null"}) String note) {
    static WarrantyResponse from(CatalogModelDetail.Warranty warranty) {
      return new WarrantyResponse(
          warranty.name(), warranty.years(), warranty.distanceKm(), warranty.note());
    }
  }

  @Schema(requiredProperties = {"organization", "rating"})
  public record SafetyRatingResponse(
      String organization,
      String rating,
      @Schema(types = {"string", "null"}) LocalDate assessedOn,
      @Schema(types = {"string", "null"}) String note) {
    static SafetyRatingResponse from(CatalogModelDetail.SafetyRating rating) {
      return new SafetyRatingResponse(
          rating.organization(), rating.rating(), rating.assessedOn(), rating.note());
    }
  }

  @Schema(requiredProperties = {"type", "url", "sortOrder"})
  public record MediaResponse(
      String type, String url, @Schema(types = {"string", "null"}) String alt, int sortOrder) {
    static MediaResponse from(CatalogModelDetail.Media media) {
      return new MediaResponse(media.type(), media.url(), media.alt(), media.sortOrder());
    }
  }
}
