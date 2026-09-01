package com.ronams.carmunity.catalog.internal.application.port.out;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelPage;
import com.ronams.carmunity.catalog.internal.application.query.SearchCatalogModelsQuery;

/** Application이 공개 모델을 조건과 안정된 page로 검색할 때 기대하는 능력입니다. */
public interface CatalogModelSearchPort {

  /** 같은 공개 configuration에서 filter를 모두 만족하는 모델을 반환합니다. */
  CatalogModelPage search(SearchCatalogModelsQuery query);
}
