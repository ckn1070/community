package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.catalog.internal.application.CatalogErrorCode;
import com.ronams.carmunity.catalog.internal.application.port.in.GetCatalogModelDetailUseCase;
import com.ronams.carmunity.catalog.internal.application.port.in.SearchCatalogModelsUseCase;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import com.ronams.carmunity.shared.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 모델 resource의 public 검색과 상세 조회를 제공하는 Controller입니다. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/car/models")
public class CatalogModelController {

  private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final SearchCatalogModelsUseCase searchCatalogModelsUseCase;
  private final GetCatalogModelDetailUseCase getCatalogModelDetailUseCase;

  /** filter를 생략하면 판매 중 모델을 이름순 첫 page로 조회합니다. */
  @Operation(summary = "카탈로그 모델 목록", description = "선택 filter를 생략해도 판매 중 모델의 첫 page를 반환합니다.")
  @ApiResponse(responseCode = "200", description = "모델 목록 조회 성공")
  @ApiResponse(
      responseCode = "400",
      description = "잘못된 filter 또는 page 입력",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "405",
      description = "지원하지 않는 HTTP method",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public CatalogModelPageResponse search(
      @Parameter(description = "카탈로그 모델 검색 조건") @ParameterObject @ModelAttribute
          CatalogModelSearchRequest request) {
    return CatalogModelPageResponse.from(searchCatalogModelsUseCase.execute(request.toQuery()));
  }

  /** 연식을 생략하면 기준일에 공개된 대표 연식을 선택해 상세 snapshot을 반환합니다. */
  @Operation(summary = "카탈로그 모델 상세", description = "선택 연식 또는 기준일의 대표 연식 상세를 조회합니다.")
  @ApiResponse(responseCode = "200", description = "모델 상세 조회 성공")
  @ApiResponse(
      responseCode = "400",
      description = "잘못된 모델·연식 식별자 또는 기준일",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "공개 snapshot에서 조회할 수 없는 모델 또는 연식",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "405",
      description = "지원하지 않는 HTTP method",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(value = "/{carModelId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public CatalogModelDetailResponse getDetail(
      @PathVariable UUID carModelId,
      @RequestParam(required = false) UUID modelYearId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    GetCatalogModelDetailQuery query =
        new GetCatalogModelDetailQuery(
            carModelId, modelYearId, asOf == null ? LocalDate.now(SEOUL_ZONE_ID) : asOf);
    return getCatalogModelDetailUseCase
        .execute(query)
        .map(CatalogModelDetailResponse::from)
        .orElseThrow(() -> new ApiException(CatalogErrorCode.CATALOG_MODEL_NOT_FOUND));
  }
}
