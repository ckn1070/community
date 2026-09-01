package com.ronams.carmunity.catalog.internal.application.service;

import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.ModelYear;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 상세 요청이 생략한 연식을 판매 상태·출시일·연식·판매 시작일·UUID 순으로 선택합니다. */
@Component
public final class CatalogModelYearSelector {

  /** 요청 연식이 있으면 그 연식만, 없으면 공개 연식 중 대표 한 개를 선택합니다. */
  public Optional<ModelYear> select(List<ModelYear> modelYears, UUID requestedModelYearId) {
    if (requestedModelYearId != null) {
      return modelYears.stream()
          .filter(modelYear -> modelYear.modelYearId().equals(requestedModelYearId))
          .findFirst();
    }
    return modelYears.stream().min(REPRESENTATIVE_ORDER);
  }

  private static final Comparator<ModelYear> REPRESENTATIVE_ORDER =
      Comparator.comparingInt((ModelYear modelYear) -> statusPriority(modelYear.salesStatus()))
          .thenComparing(ModelYear::releasedOn, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(ModelYear::modelYear, Comparator.reverseOrder())
          .thenComparing(ModelYear::salesStartedOn, Comparator.reverseOrder())
          .thenComparing(modelYear -> modelYear.modelYearId().toString());

  private static int statusPriority(SalesStatus status) {
    return switch (status) {
      case ON_SALE -> 1;
      case UPCOMING -> 2;
      case DISCONTINUED -> 3;
      case ALL -> throw new IllegalArgumentException("연식 snapshot은 ALL 상태를 가질 수 없습니다.");
    };
  }
}
