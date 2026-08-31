package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.catalog.internal.application.port.in.ListCatalogBrandsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 첫 카탈로그 화면과 브랜드 filter가 사용하는 public 브랜드 조회 Controller입니다. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/car/brands")
public class CatalogBrandController {

  private final ListCatalogBrandsUseCase listCatalogBrandsUseCase;

  /** 서울 현재 날짜에 공개 판매 모델이 있는 브랜드를 표시 순서대로 반환합니다. */
  @Operation(summary = "카탈로그 브랜드 목록", description = "현재 판매 중인 공개 모델이 있는 브랜드를 조회합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "브랜드 목록 조회 성공",
      content =
          @Content(
              array = @ArraySchema(schema = @Schema(implementation = CatalogBrandResponse.class))))
  @ApiResponse(
      responseCode = "405",
      description = "지원하지 않는 HTTP method",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CatalogBrandResponse> list() {
    return listCatalogBrandsUseCase.execute().stream().map(CatalogBrandResponse::from).toList();
  }
}
