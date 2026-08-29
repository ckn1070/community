package com.ronams.carmunity.catalog.internal.application.model;

import java.util.List;

/** 안정 정렬된 카탈로그 모델 page와 전체 모델 수를 함께 전달합니다. */
public record CatalogModelPage(
    List<CatalogModelSummary> items, int page, int size, long totalElements, int totalPages) {

  public CatalogModelPage {
    items = List.copyOf(items);
  }
}
