package com.ronams.carmunity.catalog.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.catalog.internal.application.CatalogErrorCode;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetailCachePolicy;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelDetailCachePort;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import com.ronams.carmunity.shared.error.ApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GetCatalogModelDetailCacheServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate TODAY_IN_SEOUL = LocalDate.of(2026, 9, 3);

  @Test
  void flag가_꺼져_있으면_Redis를_호출하지_않고_source를_읽는다() {
    RecordingCache cache = new RecordingCache();
    AtomicInteger sourceCalls = new AtomicInteger();
    GetCatalogModelDetailService service = service(false, cache, query -> source(sourceCalls));

    assertThat(service.execute(query(UUID.randomUUID(), TODAY_IN_SEOUL))).isEmpty();
    assertThat(cache.reads).isZero();
    assertThat(cache.writes).isZero();
    assertThat(sourceCalls).hasValue(1);
  }

  @Test
  void 과거_기준일은_cache를_우회한다() {
    RecordingCache cache = new RecordingCache();
    AtomicInteger sourceCalls = new AtomicInteger();
    GetCatalogModelDetailService service = service(true, cache, query -> source(sourceCalls));

    service.execute(query(UUID.randomUUID(), TODAY_IN_SEOUL.minusDays(1)));

    assertThat(cache.reads).isZero();
    assertThat(cache.writes).isZero();
    assertThat(sourceCalls).hasValue(1);
  }

  @Test
  void cache_hit은_source를_읽지_않는다() {
    RecordingCache cache = new RecordingCache();
    cache.result = Optional.of(detail());
    AtomicInteger sourceCalls = new AtomicInteger();
    GetCatalogModelDetailService service = service(true, cache, query -> source(sourceCalls));

    assertThat(service.execute(query(UUID.randomUUID(), TODAY_IN_SEOUL))).contains(detail());
    assertThat(sourceCalls).hasValue(0);
    assertThat(cache.writes).isZero();
  }

  @Test
  void 같은_key의_동시_miss는_source_load_하나를_공유한다() throws Exception {
    RecordingCache cache = new RecordingCache();
    AtomicInteger sourceCalls = new AtomicInteger();
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    GetCatalogModelDetailService service =
        service(
            true,
            cache,
            query -> {
              sourceCalls.incrementAndGet();
              entered.countDown();
              await(release);
              return Optional.of(detail());
            });
    GetCatalogModelDetailQuery query = query(UUID.randomUUID(), TODAY_IN_SEOUL);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> service.execute(query));
      assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
      var second = executor.submit(() -> service.execute(query));
      release.countDown();

      assertThat(first.get()).contains(detail());
      assertThat(second.get()).contains(detail());
    }
    assertThat(sourceCalls).hasValue(1);
    assertThat(cache.writes).isEqualTo(1);
  }

  @Test
  void permit을_기다리는_같은_key_요청도_새_leader와_future를_공유한다() throws Exception {
    CountDownLatch occupiedSourceEntered = new CountDownLatch(1);
    CountDownLatch releaseOccupiedSource = new CountDownLatch(1);
    CountDownLatch contendedCacheReads = new CountDownLatch(2);
    CountDownLatch contendedSourceEntered = new CountDownLatch(1);
    CountDownLatch releaseContendedSource = new CountDownLatch(1);
    AtomicInteger contendedSourceCalls = new AtomicInteger();
    GetCatalogModelDetailQuery occupiedQuery = query(UUID.randomUUID(), TODAY_IN_SEOUL);
    GetCatalogModelDetailQuery contendedQuery = query(UUID.randomUUID(), TODAY_IN_SEOUL);
    CatalogModelDetailCachePort cache =
        new CatalogModelDetailCachePort() {
          @Override
          public Optional<CatalogModelDetail> find(GetCatalogModelDetailQuery query) {
            if (query.equals(contendedQuery)) {
              contendedCacheReads.countDown();
            }
            return Optional.empty();
          }

          @Override
          public void put(GetCatalogModelDetailQuery query, CatalogModelDetail detail) {}
        };
    CatalogModelDetailCachePolicy policy =
        new CatalogModelDetailCachePolicy(
            true,
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            1_048_576,
            1,
            1,
            Duration.ofMillis(300));
    GetCatalogModelDetailService service =
        new GetCatalogModelDetailService(
            cache,
            query -> {
              if (query.equals(occupiedQuery)) {
                occupiedSourceEntered.countDown();
                await(releaseOccupiedSource);
                return Optional.empty();
              }
              contendedSourceCalls.incrementAndGet();
              contendedSourceEntered.countDown();
              await(releaseContendedSource);
              return Optional.of(detail());
            },
            policy,
            CLOCK,
            new SimpleMeterRegistry());

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var occupied = executor.submit(() -> service.execute(occupiedQuery));
      assertThat(occupiedSourceEntered.await(1, TimeUnit.SECONDS)).isTrue();
      var first = executor.submit(() -> service.execute(contendedQuery));
      var second = executor.submit(() -> service.execute(contendedQuery));
      assertThat(contendedCacheReads.await(1, TimeUnit.SECONDS)).isTrue();

      releaseOccupiedSource.countDown();
      assertThat(contendedSourceEntered.await(1, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(100);
      releaseContendedSource.countDown();

      occupied.get();
      assertThat(first.get()).contains(detail());
      assertThat(second.get()).contains(detail());
    }
    assertThat(contendedSourceCalls).hasValue(1);
  }

  @Test
  void 서로_다른_key의_source_capacity가_소진되면_503_오류로_종료한다() throws Exception {
    RecordingCache cache = new RecordingCache();
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    GetCatalogModelDetailService service =
        service(
            true,
            cache,
            query -> {
              entered.countDown();
              await(release);
              return Optional.empty();
            });

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> service.execute(query(UUID.randomUUID(), TODAY_IN_SEOUL)));
      assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> service.execute(query(UUID.randomUUID(), TODAY_IN_SEOUL)))
          .isInstanceOfSatisfying(
              ApiException.class,
              exception ->
                  assertThat(exception.errorCode())
                      .isEqualTo(CatalogErrorCode.CATALOG_MODEL_DETAIL_CAPACITY_EXHAUSTED));
      release.countDown();
      first.get();
    }
  }

  @Test
  void 서로_다른_key의_inFlight_capacity가_소진되면_source를_우회하지_않고_503으로_종료한다() throws Exception {
    RecordingCache cache = new RecordingCache();
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger sourceCalls = new AtomicInteger();
    CatalogModelDetailCachePolicy policy =
        new CatalogModelDetailCachePolicy(
            true,
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            1_048_576,
            1,
            2,
            Duration.ofMillis(50));
    GetCatalogModelDetailService service =
        new GetCatalogModelDetailService(
            cache,
            ignored -> {
              sourceCalls.incrementAndGet();
              entered.countDown();
              await(release);
              return Optional.empty();
            },
            policy,
            CLOCK,
            new SimpleMeterRegistry());

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> service.execute(query(UUID.randomUUID(), TODAY_IN_SEOUL)));
      assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> service.execute(query(UUID.randomUUID(), TODAY_IN_SEOUL)))
          .isInstanceOfSatisfying(
              ApiException.class,
              exception ->
                  assertThat(exception.errorCode())
                      .isEqualTo(CatalogErrorCode.CATALOG_MODEL_DETAIL_CAPACITY_EXHAUSTED));
      assertThat(sourceCalls).hasValue(1);
      release.countDown();
      first.get();
    }
  }

  @Test
  void source_예외_뒤_inFlight_entry를_제거해_같은_key를_다시_조회할_수_있다() {
    RecordingCache cache = new RecordingCache();
    AtomicInteger calls = new AtomicInteger();
    GetCatalogModelDetailQuery query = query(UUID.randomUUID(), TODAY_IN_SEOUL);
    GetCatalogModelDetailService service =
        service(
            true,
            cache,
            ignored -> {
              if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("source failure");
              }
              return Optional.empty();
            });

    assertThatThrownBy(() -> service.execute(query)).isInstanceOf(IllegalStateException.class);
    assertThat(service.execute(query)).isEmpty();
    assertThat(calls).hasValue(2);
  }

  @Test
  void 같은_key_waiter는_source_예외를_공유하고_entry_cleanup_뒤_재시도한다() throws Exception {
    RecordingCache cache = new RecordingCache();
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    GetCatalogModelDetailService service =
        service(
            true,
            cache,
            query -> {
              if (calls.incrementAndGet() == 1) {
                entered.countDown();
                await(release);
                throw new IllegalStateException("shared source failure");
              }
              return Optional.empty();
            });
    GetCatalogModelDetailQuery query = query(UUID.randomUUID(), TODAY_IN_SEOUL);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var leader = executor.submit(() -> service.execute(query));
      assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
      var waiter = executor.submit(() -> service.execute(query));
      release.countDown();

      assertThatThrownBy(leader::get).hasCauseInstanceOf(IllegalStateException.class);
      assertThatThrownBy(waiter::get).hasCauseInstanceOf(IllegalStateException.class);
    }

    assertThat(service.execute(query)).isEmpty();
    assertThat(calls).hasValue(2);
  }

  @Test
  void notFound는_cache에_저장하지_않는다() {
    RecordingCache cache = new RecordingCache();
    GetCatalogModelDetailService service = service(true, cache, query -> Optional.empty());

    assertThat(service.execute(query(UUID.randomUUID(), TODAY_IN_SEOUL))).isEmpty();
    assertThat(cache.writes).isZero();
  }

  @Test
  void leader_shared_waiter와_source_load를_low_cardinality로_기록한다() throws Exception {
    RecordingCache cache = new RecordingCache();
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    CatalogModelDetailCachePolicy policy =
        new CatalogModelDetailCachePolicy(
            true,
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            1_048_576,
            8,
            1,
            Duration.ofSeconds(1));
    GetCatalogModelDetailService service =
        new GetCatalogModelDetailService(
            cache,
            query -> {
              entered.countDown();
              await(release);
              return Optional.empty();
            },
            policy,
            CLOCK,
            meters);
    GetCatalogModelDetailQuery query = query(UUID.randomUUID(), TODAY_IN_SEOUL);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var leader = executor.submit(() -> service.execute(query));
      assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
      var waiter = executor.submit(() -> service.execute(query));
      release.countDown();
      leader.get();
      waiter.get();
    }

    assertThat(requestCount(meters, "single-flight-leader")).isEqualTo(1);
    assertThat(requestCount(meters, "single-flight-shared-waiter")).isEqualTo(1);
    assertThat(requestCount(meters, "source-load")).isEqualTo(1);
  }

  private GetCatalogModelDetailService service(
      boolean enabled,
      CatalogModelDetailCachePort cache,
      CatalogModelDetailSourceLoader sourceLoader) {
    CatalogModelDetailCachePolicy policy =
        new CatalogModelDetailCachePolicy(
            enabled,
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            1_048_576,
            8,
            1,
            Duration.ofMillis(20));
    return new GetCatalogModelDetailService(
        cache, sourceLoader, policy, CLOCK, new SimpleMeterRegistry());
  }

  private static GetCatalogModelDetailQuery query(UUID modelId, LocalDate asOf) {
    return new GetCatalogModelDetailQuery(modelId, null, asOf);
  }

  private static double requestCount(SimpleMeterRegistry meters, String outcome) {
    return meters
        .get("catalog.model.detail.cache.requests")
        .tag("outcome", outcome)
        .counter()
        .count();
  }

  private static Optional<CatalogModelDetail> source(AtomicInteger calls) {
    calls.incrementAndGet();
    return Optional.empty();
  }

  private static CatalogModelDetail detail() {
    return new CatalogModelDetail(
        new CatalogModelDetail.Model(
            UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a"),
            UUID.fromString("68dce8d8-34ed-49ec-a593-7a3c0afb2670"),
            "기아",
            "Kia",
            "EV6",
            null,
            null),
        java.util.List.of(),
        java.util.List.of(),
        null,
        java.util.List.of(),
        java.util.List.of());
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(1, TimeUnit.SECONDS)) {
        throw new IllegalStateException("테스트 동기화 시간이 초과되었습니다.");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private static final class RecordingCache implements CatalogModelDetailCachePort {
    private Optional<CatalogModelDetail> result = Optional.empty();
    private int reads;
    private int writes;

    @Override
    public Optional<CatalogModelDetail> find(GetCatalogModelDetailQuery query) {
      reads++;
      return result;
    }

    @Override
    public void put(GetCatalogModelDetailQuery query, CatalogModelDetail detail) {
      writes++;
    }
  }
}
