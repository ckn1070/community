package com.ronams.carmunity.shared.integration;

import java.time.Instant;
import java.util.UUID;

/** 생성 시점의 대화 관계만 전달하는 V1 wire 계약입니다. 본문·내부 DB ID·최종 수신자는 포함하지 않으며, 수신자 선택은 Notification이 소유합니다. */
public record CommunityConversationCreatedV1(
    UUID eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    UUID postId,
    UUID conversationId,
    ConversationKind conversationKind,
    UUID actorAccountId,
    UUID postAuthorAccountId,
    UUID directParentAuthorAccountId) {

  public enum ConversationKind {
    COMMENT,
    REPLY
  }
}
