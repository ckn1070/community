package com.ronams.carmunity.catalog.internal.application.port.out;

import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import java.time.LocalDate;
import java.util.List;

/** Application이 기준일의 공개 브랜드 목록을 읽을 때 기대하는 능력입니다. */
public interface CatalogBrandReadPort {

  /** 기준일에 판매 중인 공개 모델이 있는 브랜드와 각 모델 수를 반환합니다. */
  List<CatalogBrandSummary> list(LocalDate asOf);
}
