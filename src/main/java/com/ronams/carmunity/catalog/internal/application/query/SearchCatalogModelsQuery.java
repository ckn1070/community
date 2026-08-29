package com.ronams.carmunity.catalog.internal.application.query;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * 모델 목록 검색의 application query입니다.
 *
 * <p>Web 표현과 무관하게 검색 literal, 가격 범위와 안정적인 page 입력을 한 경계에서 검증합니다.
 */
public record SearchCatalogModelsQuery(
    String normalizedSearchText,
    UUID brandId,
    BodyType bodyType,
    PropulsionType propulsionType,
    FuelType fuelType,
    Long minPrice,
    Long maxPrice,
    SalesStatus salesStatus,
    LocalDate asOf,
    CatalogModelSort sort,
    int page,
    int size) {

  public SearchCatalogModelsQuery {
    normalizedSearchText = normalizeSearchText(normalizedSearchText);
    requirePriceRange(minPrice, maxPrice);
    if (salesStatus == null) {
      throw new IllegalArgumentException("판매 상태는 필수입니다.");
    }
    if (asOf == null) {
      throw new IllegalArgumentException("조회 기준일은 필수입니다.");
    }
    if (sort == null) {
      throw new IllegalArgumentException("정렬 기준은 필수입니다.");
    }
    if (page < 0) {
      throw new IllegalArgumentException("page는 0 이상이어야 합니다.");
    }
    if (size < 1 || size > 100) {
      throw new IllegalArgumentException("size는 1 이상 100 이하여야 합니다.");
    }
  }

  /** PostgreSQL LIKE가 사용자 입력의 wildcard를 해석하지 않도록 escape한 부분 일치 pattern입니다. */
  public String likePattern() {
    if (normalizedSearchText == null) {
      return null;
    }
    String escaped =
        normalizedSearchText.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  public long offset() {
    return (long) page * size;
  }

  private static String normalizeSearchText(String rawSearchText) {
    if (rawSearchText == null) {
      return null;
    }
    String normalized =
        Normalizer.normalize(rawSearchText, Normalizer.Form.NFKC)
            .strip()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    int codePointCount = normalized.codePointCount(0, normalized.length());
    if (codePointCount < 1 || codePointCount > 100) {
      throw new IllegalArgumentException("검색어는 1~100 code point여야 합니다.");
    }
    return normalized;
  }

  private static void requirePriceRange(Long minPrice, Long maxPrice) {
    if (minPrice != null && minPrice < 0) {
      throw new IllegalArgumentException("최소 가격은 0 이상이어야 합니다.");
    }
    if (maxPrice != null && maxPrice < 0) {
      throw new IllegalArgumentException("최대 가격은 0 이상이어야 합니다.");
    }
    if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
      throw new IllegalArgumentException("최소 가격은 최대 가격보다 클 수 없습니다.");
    }
  }
}
