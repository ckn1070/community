package com.ronams.carmunity.catalog.internal.application.port.out;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import java.util.Optional;

/** Application이 선택적 모델 상세 cache에 기대하는 최소 read/write 능력입니다. */
public interface CatalogModelDetailCachePort {

  Optional<CatalogModelDetail> find(GetCatalogModelDetailQuery query);

  void put(GetCatalogModelDetailQuery query, CatalogModelDetail detail);
}
