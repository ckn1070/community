package com.ronams.carmunity.catalog.internal.adapter.out.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelDetailCachePort;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import com.ronams.carmunity.support.RedisIntegrationTestSupport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RedisCatalogModelDetailCacheIntegrationTest extends RedisIntegrationTestSupport {

  @Autowired private CatalogModelDetailCachePort cache;
  @Autowired private Clock clock;

  @Test
  void actualRedisRoundTripTtlCorruptDeletionAndRepopulation() throws Exception {
    UUID modelId = UUID.randomUUID();
    GetCatalogModelDetailQuery query =
        new GetCatalogModelDetailQuery(
            modelId, null, LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))));
    CatalogModelDetail detail = detail(modelId);
    String key = "carmunity:catalog:model-detail:v1:" + modelId + ":representative:" + query.asOf();

    assertThat(cache.find(query)).isEmpty();
    cache.put(query, detail);
    assertThat(cache.find(query)).contains(detail);
    assertThat(Long.parseLong(observeRedis("TTL", key))).isPositive().isLessThanOrEqualTo(300);

    setRedisValueAsApplication(key, "{corrupt");
    assertThat(cache.find(query)).isEmpty();
    assertThat(observeRedis("EXISTS", key)).isEqualTo("0");

    cache.put(query, detail);
    assertThat(cache.find(query)).contains(detail);
  }

  @Test
  void operationAclAllowsOnlyReadOnlySmokeCommands() throws Exception {
    assertThat(executeAsOperation("PING")).contains("PONG");
    assertThat(executeAsOperation("INFO", "commandstats")).contains("# Commandstats");
    assertThat(executeAsOperation("INFO", "stats")).contains("keyspace_hits:");
    assertThat(executeAsOperation("INFO", "server")).contains("NOPERM");
    assertThat(executeAsOperation("MEMORY", "PURGE")).contains("NOPERM");
    assertThat(executeAsOperation("LATENCY", "RESET")).contains("NOPERM");
  }

  private CatalogModelDetail detail(UUID modelId) {
    UUID modelYearId = UUID.randomUUID();
    CatalogModelDetail.ModelYear modelYear =
        new CatalogModelDetail.ModelYear(
            modelYearId,
            2026,
            "2026 기본형",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 1),
            null,
            SalesStatus.ON_SALE);
    return new CatalogModelDetail(
        new CatalogModelDetail.Model(modelId, UUID.randomUUID(), "기아", "Kia", "EV6", null, null),
        List.of(),
        List.of(modelYear),
        modelYear,
        List.of(),
        List.of());
  }
}
