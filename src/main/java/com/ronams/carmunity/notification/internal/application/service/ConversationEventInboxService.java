package com.ronams.carmunity.notification.internal.application.service;

import com.ronams.carmunity.notification.api.CommunityConversationNotification;
import com.ronams.carmunity.shared.integration.CommunityConversationCreatedV1;
import com.ronams.carmunity.shared.integration.CommunityConversationCreatedV1.ConversationKind;
import java.util.LinkedHashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Receipt와 inbox를 하나의 transaction으로 완료하며 수신자 0명도 완료 증거를 남깁니다. */
@Service
@RequiredArgsConstructor
public class ConversationEventInboxService {
  private final JdbcClient jdbc;
  private final CommunityConversationNotification notifications;

  @Transactional(timeout = 5)
  public void receive(CommunityConversationCreatedV1 event) {
    int inserted =
        jdbc.sql(
                "INSERT INTO notification_event_receipt(event_id) VALUES(:id) ON CONFLICT(event_id) DO NOTHING")
            .param("id", event.eventId())
            .update();
    if (inserted == 0) return;
    var recipients = new LinkedHashSet<UUID>();
    recipients.add(event.postAuthorAccountId());
    if (event.directParentAuthorAccountId() != null)
      recipients.add(event.directParentAuthorAccountId());
    // 동기 호출과 같은 공개 저장 계약이 자기 알림 제외·중복 inbox 방지를 담당합니다.
    notifications.store(
        event.actorAccountId(),
        recipients,
        event.conversationId(),
        event.postId(),
        event.conversationKind() == ConversationKind.REPLY);
  }
}
