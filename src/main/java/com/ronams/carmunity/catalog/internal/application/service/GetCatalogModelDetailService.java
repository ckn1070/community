package com.ronams.carmunity.catalog.internal.application.service;

import com.ronams.carmunity.catalog.internal.application.CatalogErrorCode;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetailCachePolicy;
import com.ronams.carmunity.catalog.internal.application.port.in.GetCatalogModelDetailUseCase;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelDetailCachePort;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import com.ronams.carmunity.shared.error.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

/** 모델 상세의 cache-aside와 bounded source load를 조정합니다. */
@Service
public class GetCatalogModelDetailService implements GetCatalogModelDetailUseCase {

  private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final CatalogModelDetailCachePort cachePort;
  private final CatalogModelDetailSourceLoader sourceLoader;
  private final CatalogModelDetailCachePolicy policy;
  private final Clock clock;
  private final MeterRegistry meters;
  private final Object admissionMonitor = new Object();
  private final Map<GetCatalogModelDetailQuery, CompletableFuture<Optional<CatalogModelDetail>>>
      inFlight = new HashMap<>();
  private final Semaphore sourceLoadPermits;

  public GetCatalogModelDetailService(
      CatalogModelDetailCachePort cachePort,
      CatalogModelDetailSourceLoader sourceLoader,
      CatalogModelDetailCachePolicy policy,
      Clock clock,
      MeterRegistry meters) {
    this.cachePort = cachePort;
    this.sourceLoader = sourceLoader;
    this.policy = policy;
    this.clock = clock;
    this.meters = meters;
    this.sourceLoadPermits = new Semaphore(policy.maxSourceLoads());
  }

  @Override
  public Optional<CatalogModelDetail> execute(GetCatalogModelDetailQuery query) {
    if (!policy.enabled()) {
      record("disabled");
      return sourceLoader.load(query);
    }
    if (!query.asOf().equals(LocalDate.now(clock.withZone(SEOUL_ZONE_ID)))) {
      record("bypass-non-current-as-of");
      return sourceLoader.load(query);
    }

    Optional<CatalogModelDetail> cached = cachePort.find(query);
    if (cached.isPresent()) {
      record("hit");
      return cached;
    }
    record("miss");
    return loadOnce(query);
  }

  private Optional<CatalogModelDetail> loadOnce(GetCatalogModelDetailQuery query) {
    Admission admission = admit(query);
    if (!admission.leader()) {
      record("single-flight-shared-waiter");
      return await(admission.future());
    }

    CompletableFuture<Optional<CatalogModelDetail>> leader = admission.future();
    try {
      record("single-flight-leader");
      Optional<CatalogModelDetail> loaded = protectedSourceLoad(query);
      loaded.ifPresent(detail -> cachePort.put(query, detail));
      leader.complete(loaded);
      return loaded;
    } catch (RuntimeException | Error exception) {
      leader.completeExceptionally(exception);
      throw exception;
    } finally {
      synchronized (admissionMonitor) {
        inFlight.remove(query, leader);
        admissionMonitor.notifyAll();
      }
    }
  }

  private Admission admit(GetCatalogModelDetailQuery query) {
    long timeoutNanos = policy.sourceWaitTimeout().toNanos();
    long deadline = System.nanoTime() + timeoutNanos;
    synchronized (admissionMonitor) {
      while (true) {
        CompletableFuture<Optional<CatalogModelDetail>> existing = inFlight.get(query);
        if (existing != null) {
          return new Admission(existing, false);
        }
        if (inFlight.size() < policy.maxInFlightKeys()) {
          CompletableFuture<Optional<CatalogModelDetail>> leader = new CompletableFuture<>();
          inFlight.put(query, leader);
          return new Admission(leader, true);
        }

        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          throw capacityExhausted();
        }
        try {
          long millis = TimeUnit.NANOSECONDS.toMillis(remaining);
          int nanos = (int) (remaining - TimeUnit.MILLISECONDS.toNanos(millis));
          admissionMonitor.wait(millis, nanos);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw capacityExhausted();
        }
      }
    }
  }

  private Optional<CatalogModelDetail> protectedSourceLoad(GetCatalogModelDetailQuery query) {
    if (!tryAcquire(sourceLoadPermits)) {
      throw capacityExhausted();
    }
    try {
      record("source-load");
      return sourceLoader.load(query);
    } finally {
      sourceLoadPermits.release();
    }
  }

  private boolean tryAcquire(Semaphore semaphore) {
    try {
      return semaphore.tryAcquire(policy.sourceWaitTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw capacityExhausted();
    }
  }

  private Optional<CatalogModelDetail> await(
      CompletableFuture<Optional<CatalogModelDetail>> future) {
    try {
      return future.get(policy.sourceWaitTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw capacityExhausted();
    } catch (TimeoutException exception) {
      throw capacityExhausted();
    } catch (ExecutionException exception) {
      if (exception.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (exception.getCause() instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException(exception.getCause());
    }
  }

  private ApiException capacityExhausted() {
    record("capacity-exhausted");
    return new ApiException(CatalogErrorCode.CATALOG_MODEL_DETAIL_CAPACITY_EXHAUSTED);
  }

  private void record(String outcome) {
    meters.counter("catalog.model.detail.cache.requests", "outcome", outcome).increment();
  }

  private record Admission(
      CompletableFuture<Optional<CatalogModelDetail>> future, boolean leader) {}
}
