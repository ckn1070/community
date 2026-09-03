package com.ronams.carmunity.notification.internal.adapter.out.persistence;

import com.ronams.carmunity.notification.api.ConversationEventCompletion;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcConversationEventCompletion implements ConversationEventCompletion {
  private final JdbcClient jdbc;

  @Override
  public Set<UUID> completed(Set<UUID> eventIds) {
    if (eventIds.isEmpty()) return Set.of();
    if (eventIds.size() > 100) throw new IllegalArgumentException("완료 조회는 최대 100건입니다");
    return Set.copyOf(
        jdbc.sql("SELECT event_id FROM notification_event_receipt WHERE event_id IN (:ids)")
            .param("ids", eventIds)
            .query(UUID.class)
            .list());
  }
}
