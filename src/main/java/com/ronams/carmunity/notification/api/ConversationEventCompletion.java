package com.ronams.carmunity.notification.api;

import java.util.Set;
import java.util.UUID;

/** Receipt의 소유권을 Notification에 유지하는 batch 완료 조회 계약입니다. 미반환 ID는 미완료입니다. */
public interface ConversationEventCompletion {
  Set<UUID> completed(Set<UUID> eventIds);
}
