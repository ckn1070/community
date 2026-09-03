package com.ronams.carmunity.community.internal.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Relay가 DB transaction을 끝낸 뒤 발행하고, 현재 lease 소유권으로만 결과를 기록하는 계약입니다. */
public interface ConversationOutboxDeliveryPort {
  Optional<Claim> claim();

  boolean published(Claim claim);

  boolean retry(Claim claim, String failureClass);

  record Claim(UUID eventId, UUID leaseToken, UUID postId, String payload) {}
}
