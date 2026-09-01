package com.ronams.carmunity.account.internal.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 비밀번호 재설정 one-time token의 cooldown과 terminal 상태 규칙을 소유합니다. Type: Aggregate Root. */
public record PasswordResetChallenge(
    Long id,
    Long accountId,
    Instant expiresAt,
    Instant lastSentAt,
    Instant consumedAt,
    Instant revokedAt) {

  public PasswordResetChallenge {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(accountId, "accountId must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    Objects.requireNonNull(lastSentAt, "lastSentAt must not be null");
  }

  /** 마지막 발송 시도 이후 cooldown이 아직 끝나지 않았는지 판단합니다. */
  public boolean isInCooldownAt(Instant now, Duration cooldown) {
    return lastSentAt.plus(cooldown).isAfter(now);
  }

  /** 소비·회수·만료 우선순위로 현재 challenge 상태를 판정합니다. */
  public ChallengeState stateAt(Instant now) {
    if (consumedAt != null) {
      return ChallengeState.CONSUMED;
    }
    if (revokedAt != null) {
      return ChallengeState.REVOKED;
    }
    if (!expiresAt.isAfter(now)) {
      return ChallengeState.EXPIRED;
    }
    return ChallengeState.OPEN;
  }

  /** open challenge를 소비 완료 상태로 전이합니다. */
  public PasswordResetChallenge consumeAt(Instant consumedAt) {
    ensureOpenAt(consumedAt);
    return new PasswordResetChallenge(id, accountId, expiresAt, lastSentAt, consumedAt, null);
  }

  /** open challenge를 회수 상태로 전이합니다. */
  public PasswordResetChallenge revokeAt(Instant revokedAt) {
    ensureOpenAt(revokedAt);
    return new PasswordResetChallenge(id, accountId, expiresAt, lastSentAt, null, revokedAt);
  }

  private void ensureOpenAt(Instant now) {
    if (stateAt(now) != ChallengeState.OPEN) {
      throw new IllegalStateException("terminal password reset challenge cannot transition");
    }
  }
}
