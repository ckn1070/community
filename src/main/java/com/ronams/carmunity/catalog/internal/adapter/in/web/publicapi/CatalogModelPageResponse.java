package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 카탈로그 모델 목록의 안정 정렬된 page 응답입니다. */
@Schema(description = "카탈로그 모델 목록 page")
public record CatalogModelPageResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CatalogModelSummaryResponse> items,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {

  static CatalogModelPageResponse from(CatalogModelPage page) {
    return new CatalogModelPageResponse(
        page.items().stream().map(CatalogModelSummaryResponse::from).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }
}
