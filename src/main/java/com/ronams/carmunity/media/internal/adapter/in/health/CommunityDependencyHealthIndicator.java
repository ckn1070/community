package com.ronams.carmunity.media.internal.adapter.in.health;

import com.ronams.carmunity.media.api.MediaAvailability;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** 기존 API readiness와 분리해 Community private storage 준비 상태만 관찰합니다. */
@Component("communityDependency")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class CommunityDependencyHealthIndicator implements HealthIndicator {
  private final MediaAvailability media;

  @Override
  public Health health() {
    return media.isReady()
        ? Health.up().withDetail("privateStorage", "ready").build()
        : Health.down().withDetail("privateStorage", "unavailable").build();
  }
}
