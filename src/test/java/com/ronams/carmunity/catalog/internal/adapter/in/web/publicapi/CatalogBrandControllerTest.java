package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import com.ronams.carmunity.catalog.internal.application.port.in.ListCatalogBrandsUseCase;
import com.ronams.carmunity.config.ApiErrorResponseFactory;
import com.ronams.carmunity.config.ApiExceptionHandler;
import java.util.List;
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

/** 카탈로그 브랜드 public API의 응답 mapping과 use case 호출 계약을 검증합니다. */
@WebMvcTest(controllers = CatalogBrandController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  CatalogBrandControllerTest.TestUseCaseConfig.class
})
class CatalogBrandControllerTest {

  private static final UUID BRAND_ID = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeListCatalogBrandsUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase.result = List.of();
    useCase.callCount = 0;
  }

  /** 첫 진입용 브랜드 선택지는 인증 입력 없이 공개 식별자와 판매 중 모델 수를 반환합니다. */
  @Test
  void listsPublicBrandsWithOnSaleModelCounts() throws Exception {
    useCase.result = List.of(new CatalogBrandSummary(BRAND_ID, "기아", "Kia", "KR", 7));

    mockMvc
        .perform(get("/api/v1/car/brands"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].brandId").value(BRAND_ID.toString()))
        .andExpect(jsonPath("$[0].name").value("기아"))
        .andExpect(jsonPath("$[0].nameEn").value("Kia"))
        .andExpect(jsonPath("$[0].countryCode").value("KR"))
        .andExpect(jsonPath("$[0].onSaleModelCount").value(7));

    assertThat(useCase.callCount).isEqualTo(1);
  }

  @TestConfiguration
  static class TestUseCaseConfig {

    @Bean
    FakeListCatalogBrandsUseCase fakeListCatalogBrandsUseCase() {
      return new FakeListCatalogBrandsUseCase();
    }
  }

  /** Web adapter가 application 결과를 그대로 한 번 읽는지 관찰하는 use case 대역입니다. */
  static class FakeListCatalogBrandsUseCase implements ListCatalogBrandsUseCase {

    private List<CatalogBrandSummary> result = List.of();
    private int callCount;

    @Override
    public List<CatalogBrandSummary> execute() {
      callCount++;
      return result;
    }
  }
}
