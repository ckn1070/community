package com.ronams.carmunity.account.internal.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 관리자 초대의 bearer token 만료와 terminal 상태 규칙을 소유합니다. Type: Aggregate Root. */
public record AdminInvitation(
    UUID publicId, Instant expiresAt, Instant acceptedAt, Instant revokedAt) {

  public AdminInvitation {
    Objects.requireNonNull(publicId, "publicId must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (acceptedAt != null && revokedAt != null) {
      throw new IllegalArgumentException("accepted invitation cannot be revoked");
    }
  }

  /** 수락·회수·만료 우선순위로 현재 초대 상태를 판정합니다. */
  public ChallengeState stateAt(Instant now) {
    if (acceptedAt != null) {
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

  /** open 초대를 수락 완료 상태로 전이합니다. */
  public AdminInvitation acceptAt(Instant acceptedAt) {
    ensureOpenAt(acceptedAt);
    return new AdminInvitation(publicId, expiresAt, acceptedAt, null);
  }

  /** open 초대를 회수 상태로 전이합니다. */
  public AdminInvitation revokeAt(Instant revokedAt) {
    if (acceptedAt != null || this.revokedAt != null) {
      throw new IllegalStateException("terminal admin invitation cannot transition");
    }
    return new AdminInvitation(publicId, expiresAt, null, revokedAt);
  }

  private void ensureOpenAt(Instant now) {
    if (stateAt(now) != ChallengeState.OPEN) {
      throw new IllegalStateException("terminal admin invitation cannot transition");
    }
  }
}
