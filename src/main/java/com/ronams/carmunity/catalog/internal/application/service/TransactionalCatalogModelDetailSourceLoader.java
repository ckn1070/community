package com.ronams.carmunity.catalog.internal.application.service;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelDetailReadPort;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 모델 상세 query graph를 하나의 읽기 전용 transaction snapshot에서 조립합니다. */
@RequiredArgsConstructor
@Service
class TransactionalCatalogModelDetailSourceLoader implements CatalogModelDetailSourceLoader {

  private final CatalogModelDetailReadPort catalogModelDetailReadPort;

  @Override
  @Transactional(readOnly = true)
  public Optional<CatalogModelDetail> load(GetCatalogModelDetailQuery query) {
    return catalogModelDetailReadPort.find(
        query.modelId(), query.requestedModelYearId(), query.asOf());
  }
}
