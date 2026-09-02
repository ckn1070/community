package com.ronams.carmunity.catalog.internal.adapter.out.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class CatalogModelDetailRedisPropertiesBindingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(CatalogModelDetailRedisConfiguration.class)
          .withBean(
              StringRedisTemplate.class, () -> org.mockito.Mockito.mock(StringRedisTemplate.class))
          .withBean(tools.jackson.databind.ObjectMapper.class, () -> JsonMapper.builder().build())
          .withBean(Clock.class, Clock::systemUTC)
          .withBean(io.micrometer.core.instrument.MeterRegistry.class, SimpleMeterRegistry::new);

  @Test
  void 기본값과_중첩_circuit_설정을_typed_properties로_binding한다() {
    contextRunner
        .withPropertyValues(
            "carmunity.catalog.model-detail-cache.enabled=true",
            "carmunity.catalog.model-detail-cache.ttl=5m",
            "carmunity.catalog.model-detail-cache.ttl-jitter=30s",
            "carmunity.catalog.model-detail-cache.max-entry-bytes=1048576",
            "carmunity.catalog.model-detail-cache.max-in-flight-keys=64",
            "carmunity.catalog.model-detail-cache.max-source-loads=8",
            "carmunity.catalog.model-detail-cache.source-wait-timeout=100ms",
            "carmunity.catalog.model-detail-cache.circuit.sliding-window-size=20",
            "carmunity.catalog.model-detail-cache.circuit.minimum-number-of-calls=10",
            "carmunity.catalog.model-detail-cache.circuit.failure-rate-threshold=50",
            "carmunity.catalog.model-detail-cache.circuit.slow-call-rate-threshold=50",
            "carmunity.catalog.model-detail-cache.circuit.slow-call-duration-threshold=100ms",
            "carmunity.catalog.model-detail-cache.circuit.open-state-duration=5s",
            "carmunity.catalog.model-detail-cache.circuit.half-open-permitted-calls=2")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(CatalogModelDetailRedisProperties.class);
              assertThat(context)
                  .hasSingleBean(
                      com.ronams.carmunity.catalog.internal.application.model
                          .CatalogModelDetailCachePolicy.class);
            });
  }

  @Test
  void TTL_상한을_넘는_설정은_application_startup에서_거부한다() {
    contextRunner
        .withPropertyValues(
            "carmunity.catalog.model-detail-cache.ttl=301s",
            "carmunity.catalog.model-detail-cache.ttl-jitter=30s",
            "carmunity.catalog.model-detail-cache.max-entry-bytes=1048576",
            "carmunity.catalog.model-detail-cache.max-in-flight-keys=64",
            "carmunity.catalog.model-detail-cache.max-source-loads=8",
            "carmunity.catalog.model-detail-cache.source-wait-timeout=100ms",
            "carmunity.catalog.model-detail-cache.circuit.sliding-window-size=20",
            "carmunity.catalog.model-detail-cache.circuit.minimum-number-of-calls=10",
            "carmunity.catalog.model-detail-cache.circuit.failure-rate-threshold=50",
            "carmunity.catalog.model-detail-cache.circuit.slow-call-rate-threshold=50",
            "carmunity.catalog.model-detail-cache.circuit.slow-call-duration-threshold=100ms",
            "carmunity.catalog.model-detail-cache.circuit.open-state-duration=5s",
            "carmunity.catalog.model-detail-cache.circuit.half-open-permitted-calls=2")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void 양수가_아닌_circuit_설정은_binding_validation에서_거부한다() {
    contextRunner
        .withPropertyValues(
            "carmunity.catalog.model-detail-cache.ttl=5m",
            "carmunity.catalog.model-detail-cache.ttl-jitter=30s",
            "carmunity.catalog.model-detail-cache.max-entry-bytes=1048576",
            "carmunity.catalog.model-detail-cache.max-in-flight-keys=64",
            "carmunity.catalog.model-detail-cache.max-source-loads=8",
            "carmunity.catalog.model-detail-cache.source-wait-timeout=100ms",
            "carmunity.catalog.model-detail-cache.circuit.sliding-window-size=0",
            "carmunity.catalog.model-detail-cache.circuit.minimum-number-of-calls=10",
            "carmunity.catalog.model-detail-cache.circuit.failure-rate-threshold=50",
            "carmunity.catalog.model-detail-cache.circuit.slow-call-rate-threshold=50",
            "carmunity.catalog.model-detail-cache.circuit.slow-call-duration-threshold=100ms",
            "carmunity.catalog.model-detail-cache.circuit.open-state-duration=5s",
            "carmunity.catalog.model-detail-cache.circuit.half-open-permitted-calls=2")
        .run(context -> assertThat(context).hasFailed());
  }
}
