package com.ronams.carmunity.notification.api;

import java.util.Set;
import java.util.UUID;

/** Community comment transaction에서 inbox row를 원자적으로 저장하는 공개 계약입니다. */
public interface CommunityConversationNotification {
  void store(
      UUID actorId, Set<UUID> recipientIds, UUID sourceCommentId, UUID postId, boolean reply);
}
