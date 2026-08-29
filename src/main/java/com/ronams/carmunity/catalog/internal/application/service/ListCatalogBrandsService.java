package com.ronams.carmunity.catalog.internal.application.service;

import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import com.ronams.carmunity.catalog.internal.application.port.in.ListCatalogBrandsUseCase;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogReadPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서울 현재 날짜의 공개 브랜드 목록을 읽는 application service입니다. */
@Service
public class ListCatalogBrandsService implements ListCatalogBrandsUseCase {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final CatalogReadPort catalogReadPort;
  private final Clock clock;

  /** 운영에서는 UTC 기준 Clock을 사용하고 서울 날짜 변환은 application 경계에서 수행합니다. */
  @Autowired
  public ListCatalogBrandsService(CatalogReadPort catalogReadPort) {
    this(catalogReadPort, Clock.systemUTC());
  }

  /** 테스트가 날짜 경계를 통제할 수 있도록 Clock을 교체하는 생성자입니다. */
  ListCatalogBrandsService(CatalogReadPort catalogReadPort, Clock clock) {
    this.catalogReadPort = catalogReadPort;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CatalogBrandSummary> execute() {
    return catalogReadPort.listBrands(LocalDate.ofInstant(clock.instant(), SEOUL));
  }
}
