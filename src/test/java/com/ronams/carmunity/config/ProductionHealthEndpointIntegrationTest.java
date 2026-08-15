package com.ronams.carmunity.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * production profile의 Actuator, MVC, health group wiring을 실제 application context에서 검증합니다.
 *
 * <p>DB health만 통제 가능한 test contributor로 바꿔 DB 장애가 readiness에서만 트래픽 차단으로 나타나고 liveness 재시작 조건에는
 * 포함되지 않는지 확인합니다.
 */
@SpringBootTest(
    properties = {"spring.profiles.active=production", "management.health.db.enabled=false"})
@AutoConfigureMockMvc
@Import(ProductionHealthEndpointIntegrationTest.DatabaseHealthTestConfiguration.class)
class ProductionHealthEndpointIntegrationTest extends PostgreSqlIntegrationTestSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private AtomicBoolean databaseAvailable;

  /** 정상 기동에서는 liveness와 DB를 포함한 readiness가 모두 `UP`이어야 합니다. */
  @Test
  void productionHealthEndpointsAreUpWhenDatabaseIsAvailable() throws Exception {
    databaseAvailable.set(true);

    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  /** DB health가 `DOWN`이면 readiness만 실패하고 liveness는 애플리케이션 생존 상태를 유지해야 합니다. */
  @Test
  void databaseFailureRemovesReadinessWithoutFailingLiveness() throws Exception {
    databaseAvailable.set(false);

    try {
      mockMvc
          .perform(get("/actuator/health/readiness"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.status").value("DOWN"));
      mockMvc
          .perform(get("/actuator/health/liveness"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"));
    } finally {
      databaseAvailable.set(true);
    }
  }

  /** 실제 datasource indicator 대신 장애 상태만 안전하게 전환하는 `db` test contributor를 등록합니다. */
  @TestConfiguration(proxyBeanMethods = false)
  static class DatabaseHealthTestConfiguration {

    @Bean
    AtomicBoolean databaseAvailable() {
      return new AtomicBoolean(true);
    }

    @Bean(name = "db")
    HealthIndicator databaseHealthIndicator(AtomicBoolean databaseAvailable) {
      return () -> databaseAvailable.get() ? Health.up().build() : Health.down().build();
    }
  }
}
