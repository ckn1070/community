package com.ronams.carmunity.notification.internal.adapter.out.persistence;

import com.ronams.carmunity.notification.api.CommunityConversationNotification;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CommunityConversationNotificationStore implements CommunityConversationNotification {

  private final JdbcClient jdbc;

  @Override
  public void store(
      UUID actorId, Set<UUID> recipientIds, UUID sourceCommentId, UUID postId, boolean reply) {
    recipientIds.stream()
        .filter(recipient -> !recipient.equals(actorId))
        .forEach(
            recipient ->
                jdbc.sql(
                        """
                        INSERT INTO notification_inbox
                          (public_id, recipient_account_public_id, actor_account_public_id, type,
                           source_content_public_id, post_public_id)
                        VALUES (:id, :recipient, :actor, :type, :source, :post)
                        ON CONFLICT (recipient_account_public_id, source_content_public_id) DO NOTHING
                        """)
                    .param("id", UUID.randomUUID())
                    .param("recipient", recipient)
                    .param("actor", actorId)
                    .param("type", reply ? "COMMUNITY_REPLY" : "COMMUNITY_COMMENT")
                    .param("source", sourceCommentId)
                    .param("post", postId)
                    .update());
  }
}
