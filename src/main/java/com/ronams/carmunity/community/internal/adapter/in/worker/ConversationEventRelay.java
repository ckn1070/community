package com.ronams.carmunity.community.internal.adapter.in.worker;

import com.ronams.carmunity.community.internal.application.port.out.ConversationOutboxDeliveryPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** 별도 단일 worker에서 발행합니다. Kafka 대기 중 DB connection이나 다른 업무 scheduler를 점유하지 않습니다. */
@Component
@ConditionalOnProperty(name = "carmunity.kafka.relay-enabled", havingValue = "true")
public class ConversationEventRelay implements SmartLifecycle {
  private static final Logger log = LoggerFactory.getLogger(ConversationEventRelay.class);
  private final ConversationOutboxDeliveryPort delivery;
  private final KafkaTemplate<String, String> kafka;
  private final MeterRegistry metrics;
  private final AtomicBoolean inFlight = new AtomicBoolean();
  private volatile boolean running;
  private ScheduledExecutorService worker;

  public ConversationEventRelay(
      ConversationOutboxDeliveryPort delivery,
      @Qualifier("communityKafkaTemplate") KafkaTemplate<String, String> kafka,
      MeterRegistry metrics) {
    this.delivery = delivery;
    this.kafka = kafka;
    this.metrics = metrics;
  }

  public void publishNext() {
    if (!inFlight.compareAndSet(false, true)) return;
    try {
      var next = delivery.claim();
      if (next.isEmpty()) return;
      var claim = next.get();
      try {
        kafka
            .send("community.conversation-created.v1", claim.postId().toString(), claim.payload())
            .get(10, TimeUnit.SECONDS);
        count(delivery.published(claim) ? "ack" : "fenced");
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        // 종료 중에는 새 DB 작업을 시작하지 않습니다. 남은 claim은 lease 만료 뒤 회수합니다.
      } catch (java.util.concurrent.ExecutionException
          | java.util.concurrent.TimeoutException
          | RuntimeException failed) {
        count("retry");
        delivery.retry(claim, failed.getClass().getSimpleName());
      }
    } finally {
      inFlight.set(false);
    }
  }

  private void count(String outcome) {
    metrics.counter("community.events.publish", "outcome", outcome).increment();
  }

  @Override
  public synchronized void start() {
    if (running) return;
    worker =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("community-event-relay").factory());
    running = true;
    worker.scheduleWithFixedDelay(
        () -> {
          try {
            publishNext();
          } catch (RuntimeException failure) {
            count("database-error");
            log.warn("Kafka relay 작업 실패: {}", failure.getClass().getSimpleName());
          }
        },
        0,
        500,
        TimeUnit.MILLISECONDS);
  }

  @Override
  public synchronized void stop() {
    running = false;
    if (worker == null) return;
    worker.shutdown();
    try {
      if (!worker.awaitTermination(15, TimeUnit.SECONDS)) worker.shutdownNow();
    } catch (InterruptedException interrupted) {
      worker.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void stop(Runnable callback) {
    stop();
    callback.run();
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
