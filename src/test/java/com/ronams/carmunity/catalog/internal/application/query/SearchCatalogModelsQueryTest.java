package com.ronams.carmunity.catalog.internal.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 모델 검색 query가 Web 기술과 무관하게 검색 literal과 page 경계를 고정하는지 검증합니다. */
class SearchCatalogModelsQueryTest {

  /** Unicode와 공백을 정규화하고 SQL wildcard 문자를 literal 검색값으로 escape합니다. */
  @Test
  void normalizesSearchTextAndEscapesSqlWildcards() {
    SearchCatalogModelsQuery query = query("  ＥＶ6   100%_\\  ", 0L, 50_000_000L, 0, 20);

    assertThat(query.normalizedSearchText()).isEqualTo("ev6 100%_\\");
    assertThat(query.likePattern()).isEqualTo("%ev6 100\\%\\_\\\\%");
  }

  /** 가격 역전, 음수 page와 허용 범위를 넘는 size를 application 경계에서 거부합니다. */
  @Test
  void rejectsInvalidPriceAndPageRanges() {
    assertThatThrownBy(() -> query("차량", 20_000_000L, 10_000_000L, 0, 20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("가격");
    assertThatThrownBy(() -> query("차량", 0L, null, -1, 20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("page");
    assertThatThrownBy(() -> query("차량", 0L, null, 0, 101))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("size");
  }

  /** 공백뿐인 검색어와 100 code point를 넘는 검색어를 유효한 filter로 만들지 않습니다. */
  @Test
  void rejectsBlankAndOverlongSearchText() {
    String overlongSearchText = "가".repeat(101);

    assertThatThrownBy(() -> query("   ", 0L, null, 0, 20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("검색어");
    assertThatThrownBy(() -> query(overlongSearchText, 0L, null, 0, 20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("검색어");
  }

  private SearchCatalogModelsQuery query(
      String searchText, Long minPrice, Long maxPrice, int page, int size) {
    return new SearchCatalogModelsQuery(
        searchText,
        null,
        BodyType.SUV,
        PropulsionType.BEV,
        FuelType.ELECTRICITY,
        minPrice,
        maxPrice,
        SalesStatus.ON_SALE,
        LocalDate.of(2026, 8, 29),
        CatalogModelSort.NAME_ASC,
        page,
        size);
  }
}
