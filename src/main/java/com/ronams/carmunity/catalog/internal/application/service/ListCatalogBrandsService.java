package com.ronams.carmunity.catalog.internal.application.service;

import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import com.ronams.carmunity.catalog.internal.application.port.in.ListCatalogBrandsUseCase;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogBrandReadPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서울 현재 날짜의 공개 브랜드 목록을 읽는 application service입니다. */
@Service
public class ListCatalogBrandsService implements ListCatalogBrandsUseCase {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final CatalogBrandReadPort catalogBrandReadPort;
  private final Clock clock;

  /** 공용 UTC Clock을 주입받고 서울 날짜 변환은 application 경계에서 수행합니다. */
  public ListCatalogBrandsService(CatalogBrandReadPort catalogBrandReadPort, Clock clock) {
    this.catalogBrandReadPort = catalogBrandReadPort;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CatalogBrandSummary> execute() {
    return catalogBrandReadPort.list(LocalDate.ofInstant(clock.instant(), SEOUL));
  }
}
