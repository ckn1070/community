package com.ronams.carmunity.catalog.internal.adapter.out.cache;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetailCachePolicy;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelDetailCachePort;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Versioned JSON document를 Redis String으로 읽고 쓰는 catalog outbound adapter입니다. */
final class RedisCatalogModelDetailCacheAdapter implements CatalogModelDetailCachePort {

  private static final Logger log =
      LoggerFactory.getLogger(RedisCatalogModelDetailCacheAdapter.class);
  private static final Duration FAILURE_LOG_INTERVAL = Duration.ofMinutes(1);

  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final CatalogModelDetailCachePolicy policy;
  private final Clock clock;
  private final MeterRegistry meters;
  private final CircuitBreaker readCircuit;
  private final CircuitBreaker writeCircuit;
  private final LongUnaryOperator jitterSeconds;
  private final AtomicLong nextFailureLogAtMillis = new AtomicLong();

  RedisCatalogModelDetailCacheAdapter(
      StringRedisTemplate redis,
      ObjectMapper json,
      CatalogModelDetailCachePolicy policy,
      Clock clock,
      MeterRegistry meters,
      CircuitBreaker readCircuit,
      CircuitBreaker writeCircuit,
      LongUnaryOperator jitterSeconds) {
    this.redis = redis;
    this.json = json;
    this.policy = policy;
    this.clock = clock;
    this.meters = meters;
    this.readCircuit = readCircuit;
    this.writeCircuit = writeCircuit;
    this.jitterSeconds = jitterSeconds;
  }

  @Override
  public Optional<CatalogModelDetail> find(GetCatalogModelDetailQuery query) {
    String key = CatalogModelDetailCacheKey.from(query).value();
    Timer.Sample sample = Timer.start(meters);
    final String document;
    try {
      document = readCircuit.executeSupplier(() -> redis.opsForValue().get(key));
    } catch (CallNotPermittedException exception) {
      recordOperation(sample, "read", "circuit-open");
      logFailureSummary("read-circuit-open");
      return Optional.empty();
    } catch (DataAccessException exception) {
      recordOperation(sample, "read", "error");
      logFailureSummary("read-error");
      return Optional.empty();
    }
    if (document == null) {
      recordOperation(sample, "read", "miss");
      return Optional.empty();
    }

    try {
      CatalogModelDetailCacheDocument decoded =
          json.readValue(document, CatalogModelDetailCacheDocument.class);
      if (!decoded.matches(query, clock.instant())) {
        deleteBestEffort(key);
        recordOperation(sample, "read", "corrupt");
        logFailureSummary("read-corrupt");
        return Optional.empty();
      }
      CatalogModelDetail detail = decoded.toModel();
      recordOperation(sample, "read", "hit");
      return Optional.of(detail);
    } catch (JacksonException | IllegalArgumentException | NullPointerException exception) {
      deleteBestEffort(key);
      recordOperation(sample, "read", "corrupt");
      logFailureSummary("read-corrupt");
      return Optional.empty();
    }
  }

  @Override
  public void put(GetCatalogModelDetailQuery query, CatalogModelDetail detail) {
    Duration ttl = ttl();
    Instant cachedAt = clock.instant();
    CatalogModelDetailCacheDocument document =
        CatalogModelDetailCacheDocument.from(query, cachedAt, cachedAt.plus(ttl), detail);
    Timer.Sample sample = Timer.start(meters);
    try {
      String encoded = json.writeValueAsString(document);
      int encodedBytes = encoded.getBytes(StandardCharsets.UTF_8).length;
      DistributionSummary.builder("catalog.model.detail.cache.entry.bytes")
          .register(meters)
          .record(encodedBytes);
      if (encodedBytes > policy.maxEntryBytes()) {
        recordOperation(sample, "write", "oversized");
        return;
      }
      writeCircuit.executeRunnable(
          () ->
              redis
                  .opsForValue()
                  .set(CatalogModelDetailCacheKey.from(query).value(), encoded, ttl));
      recordOperation(sample, "write", "stored");
    } catch (CallNotPermittedException exception) {
      recordOperation(sample, "write", "circuit-open");
      logFailureSummary("write-circuit-open");
    } catch (DataAccessException | JacksonException exception) {
      recordOperation(sample, "write", "error");
      logFailureSummary("write-error");
    }
  }

  private Duration ttl() {
    long maximumJitter = policy.ttlJitter().toSeconds();
    long jitter = maximumJitter == 0 ? 0 : jitterSeconds.applyAsLong(maximumJitter);
    if (jitter < 0 || jitter > maximumJitter) {
      throw new IllegalStateException("TTL jitter가 설정 범위를 벗어났습니다.");
    }
    return policy.ttl().minusSeconds(jitter);
  }

  private void deleteBestEffort(String key) {
    Timer.Sample sample = Timer.start(meters);
    try {
      writeCircuit.executeRunnable(() -> redis.delete(key));
      recordOperation(sample, "delete", "deleted");
    } catch (CallNotPermittedException | DataAccessException ignored) {
      recordOperation(sample, "delete", "error");
      logFailureSummary("delete-error");
    }
  }

  private void recordOperation(Timer.Sample sample, String operation, String outcome) {
    meters
        .counter(
            "catalog.model.detail.cache.operations", "operation", operation, "outcome", outcome)
        .increment();
    sample.stop(
        Timer.builder("catalog.model.detail.cache.operation.duration")
            .tags("operation", operation, "outcome", outcome)
            .register(meters));
  }

  private void logFailureSummary(String outcome) {
    long now = clock.millis();
    long next = nextFailureLogAtMillis.get();
    if (now >= next
        && nextFailureLogAtMillis.compareAndSet(
            next, Math.addExact(now, FAILURE_LOG_INTERVAL.toMillis()))) {
      log.warn("Catalog 모델 상세 Redis cache 실패 요약: outcome={}", outcome);
    }
  }

  static LongUnaryOperator randomDownwardJitter() {
    return maximum -> ThreadLocalRandom.current().nextLong(maximum + 1);
  }
}
