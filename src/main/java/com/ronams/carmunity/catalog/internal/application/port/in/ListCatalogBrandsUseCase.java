package com.ronams.carmunity.catalog.internal.application.port.in;

import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import java.util.List;

/** 현재 공개 판매 모델이 있는 브랜드를 조회하는 유스케이스입니다. */
public interface ListCatalogBrandsUseCase {

  List<CatalogBrandSummary> execute();
}
