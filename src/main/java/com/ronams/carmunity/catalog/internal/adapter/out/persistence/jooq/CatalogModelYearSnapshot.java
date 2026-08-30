package com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Generation;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.ModelYear;

/** 연식 선택과 상세 graph 조립에 필요한 persistence projection입니다. */
record CatalogModelYearSnapshot(long internalId, Generation generation, ModelYear modelYear) {}
