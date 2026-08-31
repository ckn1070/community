package com.ronams.carmunity.auth.internal.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** refresh token family 안에서 한 인증 session의 저장 상태와 유효성 규칙을 소유합니다. Type: Aggregate Root. */
public record RefreshSession(
    Long id,
    UUID accountPublicId,
    UUID familyId,
    Instant issuedAt,
    Instant expiresAt,
    Instant revokedAt,
    String revokedReason,
    Long replacedByTokenId) {

  public RefreshSession {
    Objects.requireNonNull(accountPublicId, "accountPublicId must not be null");
    Objects.requireNonNull(familyId, "familyId must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }

  /** 최초 발급된 아직 저장되지 않은 refresh session을 생성합니다. */
  public static RefreshSession issue(
      UUID accountPublicId, UUID familyId, Instant issuedAt, Instant expiresAt) {
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
    return new RefreshSession(
        null, accountPublicId, familyId, issuedAt, expiresAt, null, null, null);
  }

  /** 현재 시각이 만료 시각에 도달했는지 판단합니다. */
  public boolean isExpiredAt(Instant now) {
    return !expiresAt.isAfter(now);
  }

  /** 이미 폐기되었거나 교체된 session인지 판단합니다. */
  public boolean isReuseCandidate() {
    return revokedAt != null || replacedByTokenId != null;
  }

  /** 활성 session을 새 token row로 교체한 상태를 반환합니다. */
  public RefreshSession rotateTo(Long replacementTokenId, Instant rotatedAt) {
    Objects.requireNonNull(replacementTokenId, "replacementTokenId must not be null");
    ensureActiveAt(rotatedAt);
    if (isExpiredAt(rotatedAt)) {
      throw new IllegalStateException("expired refresh session cannot rotate");
    }
    return new RefreshSession(
        id,
        accountPublicId,
        familyId,
        issuedAt,
        expiresAt,
        rotatedAt,
        "ROTATED",
        replacementTokenId);
  }

  /** 활성 session을 지정 사유로 폐기한 상태를 반환합니다. */
  public RefreshSession revokeAt(String reason, Instant revokedAt) {
    Objects.requireNonNull(reason, "reason must not be null");
    ensureActiveAt(revokedAt);
    return new RefreshSession(
        id, accountPublicId, familyId, issuedAt, expiresAt, revokedAt, reason, replacedByTokenId);
  }

  private void ensureActiveAt(Instant changedAt) {
    Objects.requireNonNull(changedAt, "changedAt must not be null");
    if (isReuseCandidate()) {
      throw new IllegalStateException("terminal refresh session cannot transition again");
    }
    if (changedAt.isBefore(issuedAt)) {
      throw new IllegalArgumentException("transition cannot precede issue");
    }
  }
}
