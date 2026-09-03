package com.ronams.carmunity.notification.internal.adapter.in.kafka;

import com.ronams.carmunity.notification.internal.application.service.ConversationEventInboxService;
import com.ronams.carmunity.shared.integration.CommunityConversationCreatedV1;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 고정 V1 type으로만 읽고, DB transaction이 성공한 뒤 consumer offset을 확정합니다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "carmunity.kafka.consumer-enabled", havingValue = "true")
public class ConversationEventListener {
  private final ConversationEventInboxService inbox;
  private final JsonMapper json;

  @KafkaListener(
      id = "communityConversationInbox",
      topics = "community.conversation-created.v1",
      groupId = "notification.community-conversation.v1",
      containerFactory = "notificationKafkaListenerFactory")
  public void receive(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
    if (record.value() == null
        || record.value().getBytes(StandardCharsets.UTF_8).length > 16 * 1024)
      throw new IllegalArgumentException("허용되지 않는 event 크기입니다.");
    var event = json.readValue(record.value(), CommunityConversationCreatedV1.class);
    if (!"CommunityConversationCreated".equals(event.eventType()) || event.eventVersion() != 1)
      throw new IllegalArgumentException("지원하지 않는 event 계약입니다.");
    Objects.requireNonNull(event.eventId());
    Objects.requireNonNull(event.occurredAt());
    Objects.requireNonNull(event.postId());
    Objects.requireNonNull(event.conversationId());
    Objects.requireNonNull(event.conversationKind());
    Objects.requireNonNull(event.actorAccountId());
    Objects.requireNonNull(event.postAuthorAccountId());
    if ((event.conversationKind() == CommunityConversationCreatedV1.ConversationKind.REPLY)
        != (event.directParentAuthorAccountId() != null))
      throw new IllegalArgumentException("대화 종류와 부모 관계가 일치하지 않습니다.");
    if (!event.postId().toString().equals(record.key()))
      throw new IllegalArgumentException("게시글 partition key가 일치하지 않습니다.");
    inbox.receive(event);
    acknowledgment.acknowledge();
  }
}
