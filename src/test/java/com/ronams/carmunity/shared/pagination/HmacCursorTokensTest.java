package com.ronams.carmunity.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class HmacCursorTokensTest {
  private final HmacCursorTokens tokens =
      new HmacCursorTokens("cursor-test-secret-that-is-at-least-32-bytes");

  @Test
  void roundTripsOpaquePosition() {
    Instant createdAt = Instant.parse("2026-09-01T10:00:00Z");
    String token = tokens.encode("COMMENT_DESC", createdAt, 42L);
    assertThat(tokens.decode(token, "COMMENT_DESC"))
        .isEqualTo(new CursorTokens.Position(createdAt, 42L));
  }

  @Test
  void rejectsTamperingAndDifferentSort() {
    String token = tokens.encode("COMMENT_DESC", Instant.now(), 42L);
    assertThatThrownBy(() -> tokens.decode(token + "x", "COMMENT_DESC"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> tokens.decode(token, "REPLY_ASC"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
