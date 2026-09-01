package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 공개 브랜드 선택지와 현재 판매 중 모델 수를 전달하는 응답입니다. */
@Schema(description = "카탈로그 브랜드 목록 항목")
public record CatalogBrandResponse(
    @Schema(description = "브랜드 public id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID brandId,
    @Schema(description = "국내 표시 이름", requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(
            description = "영문 이름",
            types = {"string", "null"})
        String nameEn,
    @Schema(description = "브랜드 국가 code", requiredMode = Schema.RequiredMode.REQUIRED)
        String countryCode,
    @Schema(description = "서울 현재 날짜에 판매 중인 공개 모델 수", requiredMode = Schema.RequiredMode.REQUIRED)
        long onSaleModelCount) {

  static CatalogBrandResponse from(CatalogBrandSummary summary) {
    return new CatalogBrandResponse(
        summary.brandId(),
        summary.name(),
        summary.nameEn(),
        summary.countryCode(),
        summary.onSaleModelCount());
  }
}
