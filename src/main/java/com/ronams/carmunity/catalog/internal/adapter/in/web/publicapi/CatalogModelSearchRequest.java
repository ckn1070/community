package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.query.CatalogModelSort;
import com.ronams.carmunity.catalog.internal.application.query.SearchCatalogModelsQuery;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** 카탈로그 모델 목록의 선택 filter와 첫 진입 기본값을 application query로 변환합니다. */
@Getter
@Setter
public class CatalogModelSearchRequest {

  private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

  @Schema(description = "모델·브랜드 이름 검색어", maxLength = 100)
  private String q;

  @Schema(description = "브랜드 public id")
  private UUID brandId;

  @Schema(description = "차체 유형")
  private BodyType bodyType;

  @Schema(description = "구동 유형")
  private PropulsionType propulsionType;

  @Schema(description = "연료 유형")
  private FuelType fuelType;

  @Schema(description = "최소 시작 가격", minimum = "0")
  private Long minPrice;

  @Schema(description = "최대 시작 가격", minimum = "0")
  private Long maxPrice;

  @Schema(description = "판매 상태", defaultValue = "ON_SALE")
  private SalesStatus salesStatus = SalesStatus.ON_SALE;

  @Schema(description = "조회 기준일. 생략하면 서울 현재 날짜")
  private LocalDate asOf;

  @Schema(description = "정렬 기준", defaultValue = "NAME_ASC")
  private CatalogModelSort sort = CatalogModelSort.NAME_ASC;

  @Schema(description = "0부터 시작하는 page 번호", defaultValue = "0", minimum = "0")
  private int page;

  @Schema(description = "page 크기", defaultValue = "20", minimum = "1", maximum = "100")
  private int size = 20;

  /** Web 기본값을 채운 뒤 application 경계의 정규화와 범위 검증을 재사용합니다. */
  SearchCatalogModelsQuery toQuery() {
    try {
      return new SearchCatalogModelsQuery(
          q,
          brandId,
          bodyType,
          propulsionType,
          fuelType,
          minPrice,
          maxPrice,
          salesStatus,
          asOf == null ? LocalDate.now(SEOUL_ZONE_ID) : asOf,
          sort,
          page,
          size);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST, exception);
    }
  }
}
