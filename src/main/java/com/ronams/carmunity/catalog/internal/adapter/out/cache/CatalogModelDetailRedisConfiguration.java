package com.ronams.carmunity.catalog.internal.adapter.out.cache;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetailCachePolicy;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelDetailCachePort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/** Spring Data Redis client와 application cache port를 명시적으로 조립합니다. */
@Configuration
@EnableConfigurationProperties(CatalogModelDetailRedisProperties.class)
class CatalogModelDetailRedisConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(CatalogModelDetailRedisConfiguration.class);

  @Bean
  CatalogModelDetailCachePolicy catalogModelDetailCachePolicy(
      CatalogModelDetailRedisProperties properties) {
    return new CatalogModelDetailCachePolicy(
        properties.enabled(),
        properties.ttl(),
        properties.ttlJitter(),
        properties.maxEntryBytes(),
        properties.maxInFlightKeys(),
        properties.maxSourceLoads(),
        properties.sourceWaitTimeout());
  }

  @Bean("catalogModelDetailRedisReadCircuit")
  CircuitBreaker catalogModelDetailRedisReadCircuit(
      CatalogModelDetailRedisProperties properties, MeterRegistry meters) {
    return circuit("catalog-model-detail-redis-read", "read", properties.circuit(), meters);
  }

  @Bean("catalogModelDetailRedisWriteCircuit")
  CircuitBreaker catalogModelDetailRedisWriteCircuit(
      CatalogModelDetailRedisProperties properties, MeterRegistry meters) {
    return circuit("catalog-model-detail-redis-write", "write", properties.circuit(), meters);
  }

  @Bean
  CatalogModelDetailCachePort catalogModelDetailCachePort(
      StringRedisTemplate redis,
      ObjectMapper json,
      CatalogModelDetailCachePolicy policy,
      Clock clock,
      MeterRegistry meters,
      @Qualifier("catalogModelDetailRedisReadCircuit") CircuitBreaker readCircuit,
      @Qualifier("catalogModelDetailRedisWriteCircuit") CircuitBreaker writeCircuit) {
    return new RedisCatalogModelDetailCacheAdapter(
        redis,
        json,
        policy,
        clock,
        meters,
        readCircuit,
        writeCircuit,
        RedisCatalogModelDetailCacheAdapter.randomDownwardJitter());
  }

  private CircuitBreaker circuit(
      String name,
      String operation,
      CatalogModelDetailRedisProperties.CircuitProperties properties,
      MeterRegistry meters) {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(properties.slidingWindowSize())
            .minimumNumberOfCalls(properties.minimumNumberOfCalls())
            .failureRateThreshold(properties.failureRateThreshold())
            .slowCallRateThreshold(properties.slowCallRateThreshold())
            .slowCallDurationThreshold(properties.slowCallDurationThreshold())
            .waitDurationInOpenState(properties.openStateDuration())
            .permittedNumberOfCallsInHalfOpenState(properties.halfOpenPermittedCalls())
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    CircuitBreaker circuit = CircuitBreaker.of(name, config);
    circuit
        .getEventPublisher()
        .onStateTransition(
            event -> {
              String from =
                  event.getStateTransition().getFromState().name().toLowerCase(Locale.ROOT);
              String to = event.getStateTransition().getToState().name().toLowerCase(Locale.ROOT);
              meters
                  .counter(
                      "catalog.model.detail.cache.circuit.transitions",
                      "operation",
                      operation,
                      "from",
                      from,
                      "to",
                      to)
                  .increment();
              log.warn(
                  "Catalog 모델 상세 Redis circuit 상태 전이: operation={}, from={}, to={}",
                  operation,
                  from,
                  to);
            });
    return circuit;
  }
}
