package com.ronams.carmunity.catalog.internal.application.port.in;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import java.util.Optional;

/** 공개 모델과 선택 또는 대표 연식의 전체 조회 graph를 반환하는 유스케이스입니다. */
public interface GetCatalogModelDetailUseCase {

  Optional<CatalogModelDetail> execute(GetCatalogModelDetailQuery query);
}
