package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelPage;
import com.ronams.carmunity.catalog.internal.application.model.CatalogPriceBasis;
import com.ronams.carmunity.catalog.internal.application.port.in.GetCatalogModelDetailUseCase;
import com.ronams.carmunity.catalog.internal.application.port.in.SearchCatalogModelsUseCase;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import com.ronams.carmunity.config.ApiErrorResponseFactory;
import com.ronams.carmunity.config.ApiExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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

/** 카탈로그 모델 상세 public API의 연식 선택·기본 기준일·404 계약을 검증합니다. */
@WebMvcTest(controllers = CatalogModelController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  CatalogModelControllerDetailTest.TestUseCaseConfig.class
})
class CatalogModelControllerDetailTest {

  private static final UUID MODEL_ID = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final UUID BRAND_ID = UUID.fromString("68dce8d8-34ed-49ec-a593-7a3c0afb2670");
  private static final UUID MODEL_YEAR_ID = UUID.fromString("9a799fe5-3fab-466d-9fe8-ecc364f8413d");
  private static final UUID GENERATION_ID = UUID.fromString("5a3ac4ae-cf71-4a68-9916-64925df17ad5");
  private static final UUID POWERTRAIN_ID = UUID.fromString("6be58e75-a169-48be-b972-269eed730d02");
  private static final UUID VARIANT_ID = UUID.fromString("fe657f80-f583-4cc4-933f-16f835bebea4");
  private static final UUID CONFIGURATION_ID =
      UUID.fromString("b4655a36-d7fb-49fe-b25f-91be87dbdffd");

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeGetCatalogModelDetailUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase.result = Optional.of(modelDetail());
    useCase.lastQuery = null;
  }

  /** modelYearId와 asOf를 생략하면 현재 대표 연식을 서울 현재 날짜 기준으로 조회합니다. */
  @Test
  void getsDefaultModelDetailWithoutExplicitModelYear() throws Exception {
    LocalDate beforeRequest = LocalDate.now(ZoneId.of("Asia/Seoul"));

    mockMvc
        .perform(get("/api/v1/car/models/{carModelId}", MODEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model.modelId").value(MODEL_ID.toString()))
        .andExpect(jsonPath("$.model.brandId").value(BRAND_ID.toString()))
        .andExpect(jsonPath("$.model.name").value("EV6"))
        .andExpect(jsonPath("$.selectedModelYear.modelYearId").value(MODEL_YEAR_ID.toString()))
        .andExpect(jsonPath("$.selectedModelYear.modelYear").value(2026));

    assertThat(useCase.lastQuery.modelId()).isEqualTo(MODEL_ID);
    assertThat(useCase.lastQuery.requestedModelYearId()).isNull();
    assertThat(useCase.lastQuery.asOf())
        .isBetween(beforeRequest, LocalDate.now(ZoneId.of("Asia/Seoul")));
  }

  /** 상세 graph의 모든 중첩 묶음과 nullable·빈 배열 표현을 실제 JSON으로 변환합니다. */
  @Test
  void mapsCompleteModelDetailGraphToJson() throws Exception {
    useCase.result = Optional.of(completeModelDetail());

    mockMvc
        .perform(get("/api/v1/car/models/{carModelId}", MODEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model.nameEn").value(nullValue()))
        .andExpect(jsonPath("$.generations[0].generationId").value(GENERATION_ID.toString()))
        .andExpect(jsonPath("$.generations[0].changeType").value("FULL_CHANGE"))
        .andExpect(jsonPath("$.powertrains[0].powertrainId").value(POWERTRAIN_ID.toString()))
        .andExpect(jsonPath("$.powertrains[0].drivetrainType").value("RWD"))
        .andExpect(jsonPath("$.powertrains[0].components[0].name").value("전륜 모터"))
        .andExpect(jsonPath("$.powertrains[0].components[0].position").value("FRONT"))
        .andExpect(jsonPath("$.variants[0].variantId").value(VARIANT_ID.toString()))
        .andExpect(jsonPath("$.variants[0].roofType").value("STANDARD"))
        .andExpect(jsonPath("$.variants[0].certifications[0].combinedRangeKm").value(475))
        .andExpect(
            jsonPath("$.variants[0].configurations[0].configurationId")
                .value(CONFIGURATION_ID.toString()))
        .andExpect(
            jsonPath("$.variants[0].configurations[0].standardFeatures[0].name")
                .value("전방 충돌방지 보조"))
        .andExpect(
            jsonPath("$.variants[0].configurations[0].warranties[0].note").value(nullValue()))
        .andExpect(jsonPath("$.variants[0].configurations[0].safetyRatings[0].rating").value("5"))
        .andExpect(jsonPath("$.variants[0].configurations[0].media[0].alt").value(nullValue()))
        .andExpect(
            jsonPath("$.variants[0].configurations[0].price.priceAfterTaxBenefit")
                .value(nullValue()))
        .andExpect(jsonPath("$.variants[0].configurations[0].options[0].includedItems").isArray())
        .andExpect(jsonPath("$.variants[0].configurations[0].options[0].includedItems").isEmpty())
        .andExpect(
            jsonPath("$.variants[0].configurations[0].options[0].category").value(nullValue()));
  }

  /** 목록 snapshot을 유지하려는 consumer는 대표 연식 id와 기준일을 명시해 전달할 수 있습니다. */
  @Test
  void getsRequestedModelYearAtExplicitSnapshotDate() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/car/models/{carModelId}", MODEL_ID)
                .queryParam("modelYearId", MODEL_YEAR_ID.toString())
                .queryParam("asOf", "2026-08-29"))
        .andExpect(status().isOk());

    assertThat(useCase.lastQuery)
        .isEqualTo(
            new GetCatalogModelDetailQuery(MODEL_ID, MODEL_YEAR_ID, LocalDate.of(2026, 8, 29)));
  }

  /** 모델 또는 요청 연식이 공개 snapshot에 없으면 내부 존재 여부를 드러내지 않는 404를 반환합니다. */
  @Test
  void returnsNotFoundWhenModelSnapshotIsUnavailable() throws Exception {
    useCase.result = Optional.empty();

    mockMvc
        .perform(get("/api/v1/car/models/{carModelId}", MODEL_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CATALOG_MODEL_NOT_FOUND"));
  }

  /** path UUID와 snapshot 날짜를 해석할 수 없으면 use case 호출 전 400으로 분류합니다. */
  @Test
  void rejectsMalformedDetailIdentifiersAndDate() throws Exception {
    mockMvc
        .perform(get("/api/v1/car/models/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mockMvc
        .perform(
            get("/api/v1/car/models/{carModelId}", MODEL_ID)
                .queryParam("modelYearId", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mockMvc
        .perform(get("/api/v1/car/models/{carModelId}", MODEL_ID).queryParam("asOf", "2026-02-30"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    assertThat(useCase.lastQuery).isNull();
  }

  private CatalogModelDetail modelDetail() {
    CatalogModelDetail.Model model =
        new CatalogModelDetail.Model(MODEL_ID, BRAND_ID, "기아", "Kia", "EV6", null, null);
    CatalogModelDetail.ModelYear modelYear =
        new CatalogModelDetail.ModelYear(
            MODEL_YEAR_ID,
            2026,
            "2026 EV6",
            LocalDate.of(2025, 9, 1),
            LocalDate.of(2025, 9, 1),
            null,
            SalesStatus.ON_SALE);
    return new CatalogModelDetail(
        model, List.of(), List.of(modelYear), modelYear, List.of(), List.of());
  }

  private CatalogModelDetail completeModelDetail() {
    CatalogModelDetail basic = modelDetail();
    CatalogModelDetail.Generation generation =
        new CatalogModelDetail.Generation(GENERATION_ID, "2세대", "FULL_CHANGE");
    CatalogModelDetail.Component component =
        new CatalogModelDetail.Component(
            "MOTOR",
            "TRACTION",
            "FRONT",
            "전륜 모터",
            FuelType.ELECTRICITY,
            null,
            null,
            null,
            new BigDecimal("125.0"),
            null,
            null,
            new BigDecimal("350.0"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "현대모비스",
            null,
            "KR",
            null,
            null);
    CatalogModelDetail.Powertrain powertrain =
        new CatalogModelDetail.Powertrain(
            POWERTRAIN_ID,
            "롱레인지 2WD",
            PropulsionType.BEV,
            FuelType.ELECTRICITY,
            null,
            "RWD",
            "SINGLE_SPEED",
            1,
            new BigDecimal("168.0"),
            null,
            new BigDecimal("350.0"),
            new BigDecimal("7.3"),
            185,
            new BigDecimal("11.0"),
            "TYPE_2",
            null,
            null,
            null,
            null,
            new BigDecimal("240.0"),
            "CCS1",
            18,
            10,
            80,
            new BigDecimal("350.0"),
            List.of(component));
    CatalogModelDetail.Certification certification =
        new CatalogModelDetail.Certification(
            "COMBINED",
            "복합",
            "ELECTRIC",
            "환경부",
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            new BigDecimal("5.2"),
            "KM_PER_KWH",
            null,
            null,
            new BigDecimal("475"),
            null);
    CatalogModelDetail.Variant variant =
        new CatalogModelDetail.Variant(
            VARIANT_ID,
            POWERTRAIN_ID,
            "롱레인지 2WD",
            BodyType.SUV,
            "STANDARD",
            5,
            null,
            4695,
            1890,
            1550,
            2900,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(certification),
            List.of(completeConfiguration()));
    return new CatalogModelDetail(
        basic.model(),
        List.of(generation),
        basic.modelYears(),
        basic.selectedModelYear(),
        List.of(powertrain),
        List.of(variant));
  }

  private CatalogModelDetail.Configuration completeConfiguration() {
    CatalogModelDetail.Price price =
        new CatalogModelDetail.Price(
            LocalDate.of(2025, 9, 1),
            55_400_000L,
            null,
            55_400_000L,
            CatalogPriceBasis.BEFORE_TAX_BENEFIT,
            "KRW");
    CatalogModelDetail.Option option =
        new CatalogModelDetail.Option(
            "DRIVE_WISE",
            "드라이브 와이즈",
            "PACKAGE",
            null,
            1_000_000L,
            null,
            1_000_000L,
            CatalogPriceBasis.BEFORE_TAX_BENEFIT,
            "KRW",
            List.of(),
            LocalDate.of(2025, 9, 1),
            1);
    return new CatalogModelDetail.Configuration(
        CONFIGURATION_ID,
        "에어",
        "PRIVATE",
        LocalDate.of(2025, 9, 1),
        null,
        SalesStatus.ON_SALE,
        1,
        List.of(new CatalogModelDetail.StandardFeature("SAFETY", "전방 충돌방지 보조", null)),
        List.of(new CatalogModelDetail.Warranty("고전압 배터리", 10, 200_000, null)),
        List.of(new CatalogModelDetail.SafetyRating("KNCAP", "5", null, null)),
        List.of(new CatalogModelDetail.Media("EXTERIOR", "https://example.com/ev6.jpg", null, 1)),
        price,
        List.of(option));
  }

  @TestConfiguration
  static class TestUseCaseConfig {

    @Bean
    FakeGetCatalogModelDetailUseCase fakeGetCatalogModelDetailUseCase() {
      return new FakeGetCatalogModelDetailUseCase();
    }

    @Bean
    SearchCatalogModelsUseCase unusedSearchCatalogModelsUseCase() {
      return query -> new CatalogModelPage(List.of(), 0, 20, 0, 0);
    }
  }

  /** Controller가 상세 query를 조립하고 not-found를 분류하는지 관찰하는 use case 대역입니다. */
  static class FakeGetCatalogModelDetailUseCase implements GetCatalogModelDetailUseCase {

    private Optional<CatalogModelDetail> result = Optional.empty();
    private GetCatalogModelDetailQuery lastQuery;

    @Override
    public Optional<CatalogModelDetail> execute(GetCatalogModelDetailQuery query) {
      lastQuery = query;
      return result;
    }
  }
}
