package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelPage;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelSummary;
import com.ronams.carmunity.catalog.internal.application.model.CatalogPriceBasis;
import com.ronams.carmunity.catalog.internal.application.port.in.SearchCatalogModelsUseCase;
import com.ronams.carmunity.catalog.internal.application.query.CatalogModelSort;
import com.ronams.carmunity.catalog.internal.application.query.SearchCatalogModelsQuery;
import com.ronams.carmunity.config.ApiErrorResponseFactory;
import com.ronams.carmunity.config.ApiExceptionHandler;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** 카탈로그 모델 목록 public API의 기본 조회·filter·validation·응답 계약을 검증합니다. */
@WebMvcTest(controllers = CatalogModelSearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  CatalogModelSearchControllerTest.TestUseCaseConfig.class
})
class CatalogModelSearchControllerTest {

  private static final UUID MODEL_ID = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final UUID BRAND_ID = UUID.fromString("68dce8d8-34ed-49ec-a593-7a3c0afb2670");
  private static final UUID MODEL_YEAR_ID = UUID.fromString("9a799fe5-3fab-466d-9fe8-ecc364f8413d");

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeSearchCatalogModelsUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase.result = new CatalogModelPage(List.of(), 0, 20, 0, 0);
    useCase.lastQuery = null;
  }

  /** 첫 진입의 무필터 GET은 판매 중 모델 첫 page를 만드는 기본 query로 변환됩니다. */
  @Test
  void searchesDefaultOnSaleModelPageWithoutUserFilters() throws Exception {
    useCase.result = new CatalogModelPage(List.of(modelSummary()), 0, 20, 1, 1);
    LocalDate beforeRequest = LocalDate.now(ZoneId.of("Asia/Seoul"));

    mockMvc
        .perform(get("/api/v1/car/models"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].modelId").value(MODEL_ID.toString()))
        .andExpect(jsonPath("$.items[0].brandId").value(BRAND_ID.toString()))
        .andExpect(jsonPath("$.items[0].name").value("EV6"))
        .andExpect(jsonPath("$.items[0].representativeModelYearId").value(MODEL_YEAR_ID.toString()))
        .andExpect(jsonPath("$.items[0].bodyTypes[0]").value("SUV"))
        .andExpect(jsonPath("$.items[0].propulsionTypes[0]").value("BEV"))
        .andExpect(jsonPath("$.items[0].fuelTypes[0]").value("ELECTRICITY"))
        .andExpect(jsonPath("$.items[0].startPrice").value(55_400_000L))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));

    assertThat(useCase.lastQuery.normalizedSearchText()).isNull();
    assertThat(useCase.lastQuery.brandId()).isNull();
    assertThat(useCase.lastQuery.salesStatus()).isEqualTo(SalesStatus.ON_SALE);
    assertThat(useCase.lastQuery.asOf())
        .isBetween(beforeRequest, LocalDate.now(ZoneId.of("Asia/Seoul")));
    assertThat(useCase.lastQuery.sort()).isEqualTo(CatalogModelSort.NAME_ASC);
    assertThat(useCase.lastQuery.page()).isZero();
    assertThat(useCase.lastQuery.size()).isEqualTo(20);
  }

  /** 선택 입력은 공개 enum·가격·기준일·page 값으로 application query에 전달됩니다. */
  @Test
  void mapsSearchFiltersToApplicationQuery() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/car/models")
                .queryParam("q", "  EV6  ")
                .queryParam("brandId", BRAND_ID.toString())
                .queryParam("bodyType", "SUV")
                .queryParam("propulsionType", "BEV")
                .queryParam("fuelType", "ELECTRICITY")
                .queryParam("minPrice", "40000000")
                .queryParam("maxPrice", "60000000")
                .queryParam("salesStatus", "ALL")
                .queryParam("asOf", "2026-08-29")
                .queryParam("sort", "PRICE_ASC")
                .queryParam("page", "2")
                .queryParam("size", "30"))
        .andExpect(status().isOk());

    assertThat(useCase.lastQuery.normalizedSearchText()).isEqualTo("ev6");
    assertThat(useCase.lastQuery.brandId()).isEqualTo(BRAND_ID);
    assertThat(useCase.lastQuery.bodyType()).isEqualTo(BodyType.SUV);
    assertThat(useCase.lastQuery.propulsionType()).isEqualTo(PropulsionType.BEV);
    assertThat(useCase.lastQuery.fuelType()).isEqualTo(FuelType.ELECTRICITY);
    assertThat(useCase.lastQuery.minPrice()).isEqualTo(40_000_000L);
    assertThat(useCase.lastQuery.maxPrice()).isEqualTo(60_000_000L);
    assertThat(useCase.lastQuery.salesStatus()).isEqualTo(SalesStatus.ALL);
    assertThat(useCase.lastQuery.asOf()).isEqualTo(LocalDate.of(2026, 8, 29));
    assertThat(useCase.lastQuery.sort()).isEqualTo(CatalogModelSort.PRICE_ASC);
    assertThat(useCase.lastQuery.page()).isEqualTo(2);
    assertThat(useCase.lastQuery.size()).isEqualTo(30);
  }

  /** 가격 역전과 size 상한 위반은 use case 호출 전 공통 400 응답으로 닫습니다. */
  @Test
  void rejectsInvalidSearchRangesBeforeUseCaseInvocation() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/car/models")
                .queryParam("minPrice", "60000000")
                .queryParam("maxPrice", "40000000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    assertThat(useCase.lastQuery).isNull();

    mockMvc
        .perform(get("/api/v1/car/models").queryParam("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  /** UUID·enum·날짜를 해석할 수 없는 query parameter는 모두 동일한 400 계약으로 응답합니다. */
  @Test
  void rejectsMalformedSearchFilterTypes() throws Exception {
    mockMvc
        .perform(get("/api/v1/car/models").queryParam("brandId", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mockMvc
        .perform(get("/api/v1/car/models").queryParam("bodyType", "TRUCK"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mockMvc
        .perform(get("/api/v1/car/models").queryParam("asOf", "2026-02-30"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    assertThat(useCase.lastQuery).isNull();
  }

  private CatalogModelSummary modelSummary() {
    return new CatalogModelSummary(
        MODEL_ID,
        BRAND_ID,
        "기아",
        "EV6",
        null,
        MODEL_YEAR_ID,
        2026,
        "2026 EV6",
        Set.of(BodyType.SUV),
        Set.of(PropulsionType.BEV),
        Set.of(FuelType.ELECTRICITY),
        SalesStatus.ON_SALE,
        LocalDate.of(2025, 9, 1),
        55_400_000L,
        CatalogPriceBasis.AFTER_TAX_BENEFIT,
        "KRW");
  }

  @TestConfiguration
  static class TestUseCaseConfig {

    @Bean
    FakeSearchCatalogModelsUseCase fakeSearchCatalogModelsUseCase() {
      return new FakeSearchCatalogModelsUseCase();
    }
  }

  /** Web adapter의 query 변환과 page mapping을 관찰하는 use case 대역입니다. */
  static class FakeSearchCatalogModelsUseCase implements SearchCatalogModelsUseCase {

    private CatalogModelPage result;
    private SearchCatalogModelsQuery lastQuery;

    @Override
    public CatalogModelPage execute(SearchCatalogModelsQuery query) {
      lastQuery = query;
      return result;
    }
  }
}
