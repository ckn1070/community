package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelSummary;
import com.ronams.carmunity.catalog.internal.application.model.CatalogPriceBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** 모델 목록 카드가 사용하는 대표 연식·가격 기준의 공개 응답입니다. */
@Schema(description = "카탈로그 모델 목록 항목")
public record CatalogModelSummaryResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID modelId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID brandId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String brandName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(types = {"string", "null"}) String nameEn,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID representativeModelYearId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int modelYear,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String editionName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<BodyType> bodyTypes,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<PropulsionType> propulsionTypes,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<FuelType> fuelTypes,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesStatus salesStatus,
    @Schema(types = {"string", "null"}) LocalDate releasedOn,
    @Schema(types = {"integer", "null"}) Long startPrice,
    @Schema(types = {"string", "null"}) CatalogPriceBasis priceBasis,
    @Schema(types = {"string", "null"}) String currencyCode) {

  static CatalogModelSummaryResponse from(CatalogModelSummary summary) {
    return new CatalogModelSummaryResponse(
        summary.modelId(),
        summary.brandId(),
        summary.brandName(),
        summary.name(),
        summary.nameEn(),
        summary.representativeModelYearId(),
        summary.modelYear(),
        summary.editionName(),
        summary.bodyTypes(),
        summary.propulsionTypes(),
        summary.fuelTypes(),
        summary.salesStatus(),
        summary.releasedOn(),
        summary.startPrice(),
        summary.priceBasis(),
        summary.currencyCode());
  }
}
