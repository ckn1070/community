package com.ronams.carmunity.catalog.internal.adapter.out.cache;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Catalog 모델 상세 Redis cache의 bounded runtime 설정입니다. */
@Validated
@ConfigurationProperties(prefix = "carmunity.catalog.model-detail-cache")
record CatalogModelDetailRedisProperties(
    boolean enabled,
    @NotNull Duration ttl,
    @NotNull Duration ttlJitter,
    @Positive int maxEntryBytes,
    @Positive int maxInFlightKeys,
    @Positive int maxSourceLoads,
    @NotNull Duration sourceWaitTimeout,
    @NotNull @Valid CircuitProperties circuit) {

  record CircuitProperties(
      @Positive int slidingWindowSize,
      @Positive int minimumNumberOfCalls,
      @DecimalMin("1") @DecimalMax("100") float failureRateThreshold,
      @DecimalMin("1") @DecimalMax("100") float slowCallRateThreshold,
      @NotNull Duration slowCallDurationThreshold,
      @NotNull Duration openStateDuration,
      @Positive int halfOpenPermittedCalls) {}
}
