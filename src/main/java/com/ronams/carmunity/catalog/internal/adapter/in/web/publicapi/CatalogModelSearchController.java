package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.catalog.internal.application.port.in.SearchCatalogModelsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 첫 진입 기본 목록과 사용자 filter 결과를 제공하는 public 모델 조회 Controller입니다. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/car/models")
public class CatalogModelSearchController {

  private final SearchCatalogModelsUseCase searchCatalogModelsUseCase;

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
  public CatalogModelPageResponse searchModels(
      @Parameter(description = "카탈로그 모델 검색 조건") @ParameterObject @ModelAttribute
          CatalogModelSearchRequest request) {
    return CatalogModelPageResponse.from(searchCatalogModelsUseCase.execute(request.toQuery()));
  }
}
