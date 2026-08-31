package com.ronams.carmunity.auth.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** refresh session의 발급·rotation·logout·재사용 금지 전이를 framework 없이 검증합니다. */
class RefreshSessionTest {

  private static final Instant ISSUED_AT = Instant.parse("2099-07-01T00:00:00Z");
  private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(3600);

  @Test
  void 발급한_session을_새_token으로_rotation한다() {
    RefreshSession issued = issue();

    RefreshSession rotated = issued.rotateTo(2L, ISSUED_AT.plusSeconds(10));

    assertThat(rotated.revokedReason()).isEqualTo("ROTATED");
    assertThat(rotated.replacedByTokenId()).isEqualTo(2L);
    assertThat(rotated.isReuseCandidate()).isTrue();
  }

  @Test
  void rotation된_session의_재전이를_거부한다() {
    RefreshSession rotated = issue().rotateTo(2L, ISSUED_AT.plusSeconds(10));

    assertThatThrownBy(() -> rotated.rotateTo(3L, ISSUED_AT.plusSeconds(20)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> rotated.revokeAt("LOGOUT", ISSUED_AT.plusSeconds(20)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void logout은_활성_session을_폐기하고_중복_logout을_거부한다() {
    RefreshSession loggedOut = issue().revokeAt("LOGOUT", ISSUED_AT.plusSeconds(10));

    assertThat(loggedOut.revokedReason()).isEqualTo("LOGOUT");
    assertThatThrownBy(() -> loggedOut.revokeAt("LOGOUT", ISSUED_AT.plusSeconds(20)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 만료된_session은_rotation할_수_없다() {
    assertThatThrownBy(() -> issue().rotateTo(2L, EXPIRES_AT))
        .isInstanceOf(IllegalStateException.class);
  }

  private RefreshSession issue() {
    return RefreshSession.issue(UUID.randomUUID(), UUID.randomUUID(), ISSUED_AT, EXPIRES_AT);
  }
}
