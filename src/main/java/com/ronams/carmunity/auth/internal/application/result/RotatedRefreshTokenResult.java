package com.ronams.carmunity.auth.internal.application.result;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * refresh token rotation 성공 결과입니다.
 *
 * <p>내부 token hash, token family id, 내부 account id는 클라이언트 응답에 포함하지 않습니다.
 *
 * @param accessToken 새 access token 원문입니다.
 * @param tokenType HTTP Authorization scheme입니다.
 * @param accessTokenExpiresAt access token 만료 시각입니다.
 * @param refreshToken 새 refresh token 원문입니다.
 * @param refreshTokenExpiresAt refresh token 만료 시각입니다.
 * @param memberId 외부에 노출 가능한 account 식별자입니다.
 * @param roles access token에 반영된 role code 목록입니다.
 */
public record RotatedRefreshTokenResult(
    String accessToken,
    String tokenType,
    Instant accessTokenExpiresAt,
    String refreshToken,
    Instant refreshTokenExpiresAt,
    UUID memberId,
    List<String> roles) {

  public RotatedRefreshTokenResult {
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
    Objects.requireNonNull(memberId, "memberId must not be null");
    roles = List.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
  }
}
