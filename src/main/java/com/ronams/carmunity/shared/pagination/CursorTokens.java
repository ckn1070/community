package com.ronams.carmunity.shared.pagination;

import java.time.Instant;

public interface CursorTokens {
  String encode(String sort, Instant createdAt, long internalId);

  Position decode(String encoded, String expectedSort);

  record Position(Instant createdAt, long internalId) {}
}
