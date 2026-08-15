package com.ronams.carmunity.account.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * account 모듈이 저장할 refresh token 표현입니다.
 *
 * <p>원문 refresh token은 포함하지 않고, DB에 저장 가능한 hash와 lifecycle 값만 전달합니다.
 *
 * @param tokenHash SHA-256 hex hash입니다.
 * @param familyId rotation과 reuse detection에 사용할 token family 식별자입니다.
 * @param issuedAt token 발급 시각입니다.
 * @param expiresAt token 만료 시각입니다.
 */
public record StoredRefreshToken(
    String tokenHash, UUID familyId, Instant issuedAt, Instant expiresAt) {

  public StoredRefreshToken {
    if (tokenHash == null || !tokenHash.matches("^[0-9a-f]{64}$")) {
      throw new IllegalArgumentException("tokenHash must be 64 lowercase hex characters");
    }
    Objects.requireNonNull(familyId, "familyId must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
  }
}
