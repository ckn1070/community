package com.ronams.carmunity.catalog.internal.application.model;

import java.time.Duration;

/** 모델 상세 cache admission과 source 보호에 사용하는 검증된 application 정책입니다. */
public record CatalogModelDetailCachePolicy(
    boolean enabled,
    Duration ttl,
    Duration ttlJitter,
    int maxEntryBytes,
    int maxInFlightKeys,
    int maxSourceLoads,
    Duration sourceWaitTimeout) {

  public CatalogModelDetailCachePolicy {
    requirePositive(ttl, "TTL");
    if (ttl.compareTo(Duration.ofMinutes(5)) > 0) {
      throw new IllegalArgumentException("TTL은 5분을 넘을 수 없습니다.");
    }
    if (ttlJitter.isNegative() || ttlJitter.compareTo(ttl) >= 0) {
      throw new IllegalArgumentException("TTL jitter는 0 이상 TTL 미만이어야 합니다.");
    }
    if (maxEntryBytes <= 0 || maxInFlightKeys <= 0 || maxSourceLoads <= 0) {
      throw new IllegalArgumentException("Cache resource 상한은 양수여야 합니다.");
    }
    requirePositive(sourceWaitTimeout, "Source 대기 시간");
  }

  private static void requirePositive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(name + "은 양수여야 합니다.");
    }
  }
}
