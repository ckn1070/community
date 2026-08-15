package com.ronams.carmunity.auth.internal.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** refresh token 발급 adapter가 원문과 저장 hash를 분리하는지 검증합니다. */
class SecureRandomRefreshTokenIssuerAdapterTest {

  private static final Instant NOW = Instant.parse("2026-06-22T00:00:00Z");

  /** 원문 token은 URL-safe 값이고, DB 저장용 hash는 원문과 다른 64자 hex 값이어야 합니다. */
  @Test
  void issuesRawRefreshTokenAndSha256HashWithThirtyDayTtl() {
    SecureRandomRefreshTokenIssuerAdapter issuer =
        new SecureRandomRefreshTokenIssuerAdapter(
            new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC));

    var token = issuer.issue();

    assertThat(token.value()).matches("^[A-Za-z0-9_-]+$");
    assertThat(token.hash()).matches("^[0-9a-f]{64}$");
    assertThat(token.hash()).isNotEqualTo(token.value());
    assertThat(token.hash()).isEqualTo(issuer.hash(token.value()));
    assertThat(token.issuedAt()).isEqualTo(NOW);
    assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
  }

  /** 같은 원문 token은 같은 hash로 조회되어야 기존 refresh_token row를 찾을 수 있습니다. */
  @Test
  void hashesRefreshTokenDeterministically() {
    SecureRandomRefreshTokenIssuerAdapter issuer =
        new SecureRandomRefreshTokenIssuerAdapter(
            new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(issuer.hash("raw-refresh-token")).isEqualTo(issuer.hash("raw-refresh-token"));
  }
}
