package com.ronams.carmunity.catalog.internal.application.service;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelPage;
import com.ronams.carmunity.catalog.internal.application.port.in.SearchCatalogModelsUseCase;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelSearchPort;
import com.ronams.carmunity.catalog.internal.application.query.SearchCatalogModelsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 모델 목록 projection을 읽기 전용 transaction으로 조회하는 application service입니다. */
@RequiredArgsConstructor
@Service
public class SearchCatalogModelsService implements SearchCatalogModelsUseCase {

  private final CatalogModelSearchPort catalogModelSearchPort;

  @Override
  @Transactional(readOnly = true)
  public CatalogModelPage execute(SearchCatalogModelsQuery query) {
    return catalogModelSearchPort.search(query);
  }
}
