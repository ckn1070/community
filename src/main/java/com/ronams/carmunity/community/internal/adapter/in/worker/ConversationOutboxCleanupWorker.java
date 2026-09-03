package com.ronams.carmunity.community.internal.adapter.in.worker;

import com.ronams.carmunity.community.internal.application.service.ConversationOutboxLifecycleService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 명시적으로 활성화한 환경에서만 제한된 batch를 정리합니다. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "carmunity.kafka.cleanup-enabled", havingValue = "true")
class ConversationOutboxCleanupWorker {
  private final ConversationOutboxLifecycleService lifecycle;
  private final MeterRegistry meters;

  @Scheduled(fixedDelayString = "${carmunity.kafka.cleanup-delay:PT1M}")
  public void cleanup() {
    try {
      meters
          .counter("community.events.cleanup", "outcome", "deleted")
          .increment(lifecycle.cleanup());
    } catch (RuntimeException exception) {
      meters.counter("community.events.cleanup", "outcome", "preserved").increment();
      log.warn("Outbox cleanup 보존: {}", exception.getClass().getSimpleName());
    }
  }
}
