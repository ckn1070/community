package com.ronams.carmunity.catalog.internal.application.port.in;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelPage;
import com.ronams.carmunity.catalog.internal.application.query.SearchCatalogModelsQuery;

/** 공개 모델 목록을 filter와 안정적인 page로 조회하는 유스케이스입니다. */
public interface SearchCatalogModelsUseCase {

  CatalogModelPage execute(SearchCatalogModelsQuery query);
}
