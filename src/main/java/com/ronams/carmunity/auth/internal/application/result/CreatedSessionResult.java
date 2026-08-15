package com.ronams.carmunity.auth.internal.application.result;

import com.ronams.carmunity.auth.internal.application.service.SessionAccess;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 비밀번호 session 생성 결과입니다. */
public record CreatedSessionResult(
    String accessToken,
    String tokenType,
    Instant accessTokenExpiresAt,
    String refreshToken,
    Instant refreshTokenExpiresAt,
    UUID accountId,
    List<String> roles,
    SessionAccess sessionAccess) {

  public CreatedSessionResult {
    if (accessToken == null || accessToken.isBlank()) {
      throw new IllegalArgumentException("accessToken must not be blank");
    }
    if (!"Bearer".equals(tokenType)) {
      throw new IllegalArgumentException("tokenType must be Bearer");
    }
    Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt must not be null");
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new IllegalArgumentException("refreshToken must not be blank");
    }
    Objects.requireNonNull(refreshTokenExpiresAt, "refreshTokenExpiresAt must not be null");
    Objects.requireNonNull(accountId, "accountId must not be null");
    roles = List.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
    Objects.requireNonNull(sessionAccess, "sessionAccess must not be null");
  }
}
