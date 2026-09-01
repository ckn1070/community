package com.ronams.carmunity.catalog.internal.application.query;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** 모델과 선택 연식을 기준일 snapshot으로 조회하는 application query입니다. */
public record GetCatalogModelDetailQuery(UUID modelId, UUID requestedModelYearId, LocalDate asOf) {

  public GetCatalogModelDetailQuery {
    Objects.requireNonNull(modelId, "모델 식별자는 필수입니다.");
    Objects.requireNonNull(asOf, "조회 기준일은 필수입니다.");
  }
}
