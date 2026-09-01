package com.ronams.carmunity.account.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** account one-time challenge가 cooldown·terminal·만료 규칙을 직접 소유하는지 검증합니다. */
class AccountChallengeDomainTest {

  private static final Instant NOW = Instant.parse("2099-07-01T00:00:00Z");

  @Test
  void emailVerification은_terminal상태를_만료보다_우선한다() {
    EmailVerificationChallenge challenge =
        new EmailVerificationChallenge(
            1L, 10L, NOW.minusSeconds(1), NOW.minusSeconds(30), NOW.minusSeconds(5), null);

    assertThat(challenge.stateAt(NOW)).isEqualTo(ChallengeState.CONSUMED);
    assertThat(challenge.isInCooldownAt(NOW, Duration.ofSeconds(60))).isTrue();
  }

  @Test
  void passwordReset은_cooldown경계와_만료경계를_포함한다() {
    PasswordResetChallenge challenge =
        new PasswordResetChallenge(1L, 10L, NOW, NOW.minusSeconds(60), null, null);

    assertThat(challenge.stateAt(NOW)).isEqualTo(ChallengeState.EXPIRED);
    assertThat(challenge.isInCooldownAt(NOW, Duration.ofSeconds(60))).isFalse();
  }

  @Test
  void adminInvitation은_회수된_초대를_다시_열지_않는다() {
    AdminInvitation invitation =
        new AdminInvitation(UUID.randomUUID(), NOW.plusSeconds(3600), null, NOW.minusSeconds(1));

    assertThat(invitation.stateAt(NOW)).isEqualTo(ChallengeState.REVOKED);
  }

  @Test
  void emailVerification은_소비한_challenge의_재전이를_거부한다() {
    EmailVerificationChallenge open =
        new EmailVerificationChallenge(
            1L, 10L, NOW.plusSeconds(60), NOW.minusSeconds(30), null, null);

    EmailVerificationChallenge consumed = open.consumeAt(NOW);
    Instant retryAt = NOW.plusSeconds(1);

    assertThat(consumed.stateAt(NOW)).isEqualTo(ChallengeState.CONSUMED);
    assertThatThrownBy(() -> consumed.revokeAt(retryAt)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void passwordReset은_회수한_challenge의_소비를_거부한다() {
    PasswordResetChallenge open =
        new PasswordResetChallenge(1L, 10L, NOW.plusSeconds(60), NOW.minusSeconds(30), null, null);

    PasswordResetChallenge revoked = open.revokeAt(NOW);
    Instant retryAt = NOW.plusSeconds(1);

    assertThat(revoked.stateAt(NOW)).isEqualTo(ChallengeState.REVOKED);
    assertThatThrownBy(() -> revoked.consumeAt(retryAt)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void adminInvitation은_수락과_회수를_terminal_전이로_소유한다() {
    AdminInvitation open =
        new AdminInvitation(UUID.randomUUID(), NOW.plusSeconds(3600), null, null);

    AdminInvitation accepted = open.acceptAt(NOW);
    Instant retryAt = NOW.plusSeconds(1);

    assertThat(accepted.stateAt(NOW)).isEqualTo(ChallengeState.CONSUMED);
    assertThatThrownBy(() -> accepted.revokeAt(retryAt)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void passwordCredential은_비밀번호_변경과_강제변경_해제를_같이_전이한다() {
    PasswordCredential temporary =
        new PasswordCredential(PasswordHash.from("{argon2}temporary"), true);

    PasswordCredential changed = temporary.changeTo(PasswordHash.from("{argon2}changed"));

    assertThat(changed.passwordHash().value()).isEqualTo("{argon2}changed");
    assertThat(changed.passwordChangeRequired()).isFalse();
  }
}
