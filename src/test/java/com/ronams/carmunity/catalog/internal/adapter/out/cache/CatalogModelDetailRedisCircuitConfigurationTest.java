package com.ronams.carmunity.catalog.internal.adapter.out.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CatalogModelDetailRedisCircuitConfigurationTest {

  @Test
  void production_설정의_read_write_circuit이_open_halfOpen과_복구_재차단을_자동_전이한다() throws Exception {
    CatalogModelDetailRedisConfiguration configuration = new CatalogModelDetailRedisConfiguration();
    CatalogModelDetailRedisProperties properties = properties();
    CircuitBreaker read =
        configuration.catalogModelDetailRedisReadCircuit(properties, new SimpleMeterRegistry());
    CircuitBreaker write =
        configuration.catalogModelDetailRedisWriteCircuit(properties, new SimpleMeterRegistry());

    CountDownLatch readHalfOpen = transitionLatch(read, CircuitBreaker.State.HALF_OPEN);
    open(read);
    assertThat(readHalfOpen.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(read.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState())
        .isEqualTo(1);
    assertThat(read.executeSupplier(() -> "ok")).isEqualTo("ok");
    assertThat(read.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

    CountDownLatch writeHalfOpen = transitionLatch(write, CircuitBreaker.State.HALF_OPEN);
    open(write);
    assertThat(writeHalfOpen.await(1, TimeUnit.SECONDS)).isTrue();
    assertThatThrownBy(
            () ->
                write.executeRunnable(
                    () -> {
                      throw new IllegalStateException("write failure");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(write.getState()).isEqualTo(CircuitBreaker.State.OPEN);
  }

  private static void open(CircuitBreaker circuit) {
    for (int attempt = 0; attempt < 2; attempt++) {
      assertThatThrownBy(
              () ->
                  circuit.executeRunnable(
                      () -> {
                        throw new IllegalStateException("redis failure");
                      }))
          .isInstanceOf(IllegalStateException.class);
    }
    assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThatThrownBy(() -> circuit.executeRunnable(() -> {}))
        .isInstanceOf(CallNotPermittedException.class);
  }

  private static CountDownLatch transitionLatch(
      CircuitBreaker circuit, CircuitBreaker.State target) {
    CountDownLatch latch = new CountDownLatch(1);
    circuit
        .getEventPublisher()
        .onStateTransition(
            event -> {
              if (event.getStateTransition().getToState() == target) {
                latch.countDown();
              }
            });
    return latch;
  }

  private static CatalogModelDetailRedisProperties properties() {
    return new CatalogModelDetailRedisProperties(
        true,
        Duration.ofMinutes(5),
        Duration.ofSeconds(30),
        1_048_576,
        64,
        8,
        Duration.ofMillis(100),
        new CatalogModelDetailRedisProperties.CircuitProperties(
            2, 2, 50, 50, Duration.ofMillis(100), Duration.ofMillis(100), 1));
  }
}
