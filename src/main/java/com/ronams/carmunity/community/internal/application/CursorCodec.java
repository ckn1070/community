package com.ronams.carmunity.community.internal.application;

import java.time.Instant;

public interface CursorCodec {
  String encode(String sort, Instant createdAt, long internalId);

  Position decode(String encoded, String expectedSort);

  record Position(Instant createdAt, long internalId) {}
}
