package com.ronams.carmunity.catalog.internal.application.service;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import java.util.Optional;

/** Cache miss에서 authoritative 모델 상세를 읽는 application 내부 경계입니다. */
@FunctionalInterface
public interface CatalogModelDetailSourceLoader {

  Optional<CatalogModelDetail> load(GetCatalogModelDetailQuery query);
}
