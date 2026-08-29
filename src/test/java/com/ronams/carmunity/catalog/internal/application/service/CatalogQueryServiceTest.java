package com.ronams.carmunity.catalog.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelPage;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogReadPort;
import com.ronams.carmunity.catalog.internal.application.query.CatalogModelSort;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import com.ronams.carmunity.catalog.internal.application.query.SearchCatalogModelsQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogQueryServiceTest {

  @Mock private CatalogReadPort catalogReadPort;

  @Test
  void 브랜드_목록은_서울_현재_날짜를_기준으로_조회한다() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-28T15:30:00Z"), ZoneOffset.UTC);
    CatalogBrandSummary brand = new CatalogBrandSummary(UUID.randomUUID(), "기아", "Kia", "KR", 2);
    when(catalogReadPort.listBrands(LocalDate.of(2026, 8, 29))).thenReturn(List.of(brand));

    List<CatalogBrandSummary> result =
        new ListCatalogBrandsService(catalogReadPort, clock).execute();

    assertThat(result).containsExactly(brand);
  }

  @Test
  void 모델_목록은_검증된_query를_persistence에_전달한다() {
    SearchCatalogModelsQuery query =
        new SearchCatalogModelsQuery(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            SalesStatus.ON_SALE,
            LocalDate.of(2026, 8, 29),
            CatalogModelSort.NAME_ASC,
            0,
            20);
    CatalogModelPage page = new CatalogModelPage(List.of(), 0, 20, 0, 0);
    when(catalogReadPort.searchModels(query)).thenReturn(page);

    CatalogModelPage result = new SearchCatalogModelsService(catalogReadPort).execute(query);

    assertThat(result).isSameAs(page);
  }

  @Test
  void 모델_상세는_선택_연식과_기준일을_persistence에_전달한다() {
    UUID modelId = UUID.randomUUID();
    UUID modelYearId = UUID.randomUUID();
    GetCatalogModelDetailQuery query =
        new GetCatalogModelDetailQuery(modelId, modelYearId, LocalDate.of(2026, 8, 29));

    assertThat(new GetCatalogModelDetailService(catalogReadPort).execute(query)).isEmpty();
    verify(catalogReadPort).findModelDetail(modelId, modelYearId, LocalDate.of(2026, 8, 29));
  }
}
