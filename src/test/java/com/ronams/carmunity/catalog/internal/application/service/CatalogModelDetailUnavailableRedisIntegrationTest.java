package com.ronams.carmunity.catalog.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.port.in.GetCatalogModelDetailUseCase;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelDetailReadPort;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "carmunity.catalog.model-detail-cache.enabled=true",
      "spring.data.redis.host=127.0.0.1",
      "spring.data.redis.port=1",
      "spring.data.redis.connect-timeout=50ms",
      "spring.data.redis.timeout=50ms"
    })
class CatalogModelDetailUnavailableRedisIntegrationTest extends PostgreSqlIntegrationTestSupport {

  @Autowired private GetCatalogModelDetailUseCase useCase;
  @Autowired private Clock clock;
  @MockitoBean private CatalogModelDetailReadPort readPort;

  @Test
  void unavailableRedisFallsBackToPostgreSqlSourceWithinBoundedTime() {
    UUID modelId = UUID.randomUUID();
    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
    CatalogModelDetail detail = detail(modelId);
    when(readPort.find(modelId, null, today)).thenReturn(Optional.of(detail));

    long started = System.nanoTime();
    Optional<CatalogModelDetail> result =
        useCase.execute(new GetCatalogModelDetailQuery(modelId, null, today));

    assertThat(result).contains(detail);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    verify(readPort).find(modelId, null, today);
  }

  private CatalogModelDetail detail(UUID modelId) {
    return new CatalogModelDetail(
        new CatalogModelDetail.Model(modelId, UUID.randomUUID(), "기아", "Kia", "EV6", null, null),
        List.of(),
        List.of(),
        null,
        List.of(),
        List.of());
  }
}
