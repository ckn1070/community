package com.ronams.carmunity.catalog.internal.adapter.out.cache;

import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;

/** Redis document namespace와 전체 query identity를 결합한 versioned key입니다. */
record CatalogModelDetailCacheKey(String value) {

  private static final String PREFIX = "carmunity:catalog:model-detail:v1:";

  static CatalogModelDetailCacheKey from(GetCatalogModelDetailQuery query) {
    String modelYear =
        query.requestedModelYearId() == null
            ? "representative"
            : query.requestedModelYearId().toString();
    return new CatalogModelDetailCacheKey(
        PREFIX + query.modelId() + ":" + modelYear + ":" + query.asOf());
  }
}
