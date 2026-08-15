package com.ronams.carmunity.auth.internal.application.result;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 발급된 refresh token의 application 결과 모델입니다.
 *
 * <p>원문 token은 응답으로 한 번만 전달하고, persistence에는 hash와 lifecycle 값만 전달합니다.
 *
 * @param value 클라이언트에 전달할 refresh token 원문입니다.
 * @param hash DB에 저장할 refresh token hash입니다.
 * @param familyId rotation과 reuse detection에 사용할 token family 식별자입니다.
 * @param issuedAt refresh token 발급 시각입니다.
 * @param expiresAt refresh token 만료 시각입니다.
 */
public record IssuedRefreshToken(
    String value, String hash, UUID familyId, Instant issuedAt, Instant expiresAt) {

  public IssuedRefreshToken {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
    if (hash == null || !hash.matches("^[0-9a-f]{64}$")) {
      throw new IllegalArgumentException("hash must be 64 lowercase hex characters");
    }
    Objects.requireNonNull(familyId, "familyId must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
  }
}
