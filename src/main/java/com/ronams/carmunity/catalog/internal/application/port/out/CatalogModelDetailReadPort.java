package com.ronams.carmunity.catalog.internal.application.port.out;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Application이 공개 모델과 선택 연식의 전체 조회 graph를 읽을 때 기대하는 능력입니다. */
public interface CatalogModelDetailReadPort {

  /** 요청 연식 또는 기준일 대표 연식의 전체 조회 graph를 반환합니다. */
  Optional<CatalogModelDetail> find(UUID modelId, UUID requestedModelYearId, LocalDate asOf);
}
