package com.ronams.carmunity.community.internal.adapter.out.persistence;

import com.ronams.carmunity.community.internal.application.port.out.ConversationEventOutboxPort;
import com.ronams.carmunity.shared.integration.CommunityConversationCreatedV1;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Broker 호출 없이 같은 DB transaction에 immutable JSON 원본을 저장합니다. */
@Repository
@RequiredArgsConstructor
class JdbcConversationEventOutboxAdapter implements ConversationEventOutboxPort {
  private final JdbcClient jdbc;
  private final JsonMapper json;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void append(CommunityConversationCreatedV1 event) {
    String payload = json.writeValueAsString(event);
    if (payload.getBytes(StandardCharsets.UTF_8).length > 16 * 1024) {
      throw new IllegalArgumentException("대화 event가 최대 전달 크기를 초과했습니다.");
    }
    jdbc.sql(
            """
        INSERT INTO community_event_outbox
          (event_id, event_type, event_version, post_id, conversation_id, payload, occurred_at)
        VALUES (:event, :type, :version, :post, :conversation, CAST(:payload AS JSONB), :occurred)
        """)
        .param("event", event.eventId())
        .param("type", event.eventType())
        .param("version", event.eventVersion())
        .param("post", event.postId())
        .param("conversation", event.conversationId())
        .param("payload", payload)
        .param("occurred", Timestamp.from(event.occurredAt()))
        .update();
  }
}
