package com.ronams.carmunity.catalog.internal.adapter.in.web.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelDetailReadPort;
import com.ronams.carmunity.support.RedisIntegrationTestSupport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogModelDetailRedisApiIntegrationTest extends RedisIntegrationTestSupport {

  private static final UUID K5_MODEL_ID = UUID.fromString("68ad71d8-fae9-54fb-8835-9b202d5e5eed");

  @Autowired private MockMvc mockMvc;
  @Autowired private Clock clock;
  @MockitoSpyBean private CatalogModelDetailReadPort readPort;

  @Test
  void coldAndWarmApiResponsesAreIdenticalAndWarmRequestSkipsPostgreSql() throws Exception {
    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));

    String cold =
        mockMvc
            .perform(get("/api/v1/car/models/{carModelId}", K5_MODEL_ID))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String warm =
        mockMvc
            .perform(get("/api/v1/car/models/{carModelId}", K5_MODEL_ID))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(warm).isEqualTo(cold);
    verify(readPort, times(1)).find(eq(K5_MODEL_ID), eq(null), eq(today));
  }
}
