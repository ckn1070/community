package com.ronams.carmunity.auth.internal.application.result;

import java.time.Instant;
import java.util.Objects;

/**
 * 발급된 access token의 application 결과 모델입니다.
 *
 * <p>원문 token은 클라이언트 전달에만 사용하고, application log나 persistence 경로에 남기지 않습니다.
 *
 * @param value Bearer token 원문입니다.
 * @param expiresAt token 만료 시각입니다.
 * @param tokenType HTTP Authorization scheme입니다.
 */
public record IssuedAccessToken(String value, Instant expiresAt, String tokenType) {

  public IssuedAccessToken {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!"Bearer".equals(tokenType)) {
      throw new IllegalArgumentException("tokenType must be Bearer");
    }
  }
}
