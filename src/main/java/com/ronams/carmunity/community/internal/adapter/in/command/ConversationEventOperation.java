package com.ronams.carmunity.community.internal.adapter.in.command;

import com.ronams.carmunity.community.internal.application.service.ConversationOutboxLifecycleService;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** 같은 artifact의 일회 실행 경계입니다. 기본값은 dry-run이며 승인한 정확한 ID와 예상 건수만 처리합니다. */
@Component
@Slf4j
@ConditionalOnProperty(name = "carmunity.kafka.operation-enabled", havingValue = "true")
public class ConversationEventOperation implements ApplicationRunner {
  private final ConversationOutboxLifecycleService lifecycle;
  private final ObjectProvider<KafkaTemplate<String, String>> replay;

  public ConversationEventOperation(
      ConversationOutboxLifecycleService lifecycle,
      @Qualifier("conversationReplayKafkaTemplate")
          ObjectProvider<KafkaTemplate<String, String>> replay) {
    this.lifecycle = lifecycle;
    this.replay = replay;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (!"kafka-events".equals(option(args, "command")))
      throw new IllegalArgumentException("일회 실행 command가 필요합니다");
    String mode = option(args, "mode");
    if (!Set.of("replay", "terminal").contains(mode))
      throw new IllegalArgumentException("mode는 replay 또는 terminal입니다");
    List<String> raw = Arrays.asList(option(args, "event-ids").split(",", -1));
    Set<UUID> ids = new LinkedHashSet<>();
    for (String value : raw)
      if (!ids.add(UUID.fromString(value.trim())))
        throw new IllegalArgumentException("중복 event ID입니다");
    String reason = option(args, "reason");
    ConversationOutboxLifecycleService.validateEvidence(reason);
    var candidates = lifecycle.preview(ids);
    // 본문·계정·원인 자유 입력은 로그에 넣지 않고 event identity와 실행 결과만 출력합니다.
    log.info(
        "Kafka operation dry-run mode={} requested={} pending={} eventIds={}",
        mode,
        ids.size(),
        candidates.size(),
        candidates.stream().map(e -> e.eventId().toString()).toList());
    if (!args.containsOption("approved")) return;
    if (!"true".equals(option(args, "approved")))
      throw new IllegalArgumentException("approved=true가 필요합니다");
    int expected = Integer.parseInt(option(args, "expected-count"));
    if (expected != candidates.size() || expected < 1)
      throw new IllegalStateException("승인한 건수가 달라졌습니다");
    String operator = option(args, "operator");
    ConversationOutboxLifecycleService.validateEvidence(operator);
    if (mode.equals("terminal")) {
      lifecycle.terminate(
          ids,
          expected,
          operator,
          reason,
          option(args, "impact"),
          option(args, "unrecoverable-evidence"));
      log.info("Kafka operation terminal count={}", expected);
      return;
    }
    KafkaTemplate<String, String> kafka = replay.getObject();
    for (var original : candidates) {
      // Kafka를 기다리는 동안 DB transaction이나 connection을 점유하지 않습니다. 중단 후 같은 ID로 재개할 수 있습니다.
      kafka
          .send(
              "community.conversation-created.v1", original.postId().toString(), original.payload())
          .get(10, TimeUnit.SECONDS);
      log.info("Kafka operation broker-ack eventId={}", original.eventId());
    }
    log.info("Kafka operation broker-ack count={}; downstream 완료는 receipt로 별도 확인합니다", expected);
  }

  private static String option(ApplicationArguments args, String name) {
    List<String> values = args.getOptionValues(name);
    if (values == null || values.size() != 1 || values.getFirst().isBlank())
      throw new IllegalArgumentException("필수 option이 없거나 중복됐습니다: " + name);
    return values.getFirst();
  }
}
