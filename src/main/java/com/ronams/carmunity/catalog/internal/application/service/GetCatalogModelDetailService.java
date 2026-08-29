package com.ronams.carmunity.catalog.internal.application.service;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.port.in.GetCatalogModelDetailUseCase;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogReadPort;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 모델 상세의 여러 projection을 같은 읽기 전용 transaction snapshot에서 조립합니다. */
@RequiredArgsConstructor
@Service
public class GetCatalogModelDetailService implements GetCatalogModelDetailUseCase {

  private final CatalogReadPort catalogReadPort;

  @Override
  @Transactional(readOnly = true)
  public Optional<CatalogModelDetail> execute(GetCatalogModelDetailQuery query) {
    return catalogReadPort.findModelDetail(
        query.modelId(), query.requestedModelYearId(), query.asOf());
  }
}
