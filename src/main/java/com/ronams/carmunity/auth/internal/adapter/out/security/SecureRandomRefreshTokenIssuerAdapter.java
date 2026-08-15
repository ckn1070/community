package com.ronams.carmunity.auth.internal.adapter.out.security;

import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenHashPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** SecureRandom 기반 refresh token 발급과 SHA-256 hash 변환 adapter입니다. */
@Component
public class SecureRandomRefreshTokenIssuerAdapter
    implements RefreshTokenIssuerPort, RefreshTokenHashPort {

  private static final int TOKEN_BYTES = 32;
  private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

  private final SecureRandom secureRandom;
  private final Clock clock;

  /** 운영 기본 난수 생성기와 clock으로 adapter를 구성합니다. */
  @Autowired
  public SecureRandomRefreshTokenIssuerAdapter(Clock clock) {
    this(new SecureRandom(), clock);
  }

  /** 테스트가 발급 시각과 난수 구현을 통제할 수 있도록 Clock과 SecureRandom을 외부에서 주입받습니다. */
  SecureRandomRefreshTokenIssuerAdapter(SecureRandom secureRandom, Clock clock) {
    this.secureRandom = secureRandom;
    this.clock = clock;
  }

  /**
   * refresh token 원문과 DB 저장용 hash를 함께 생성합니다.
   *
   * <p>원문은 response body로만 전달하고, account 저장 경계에는 hash와 lifecycle 값만 넘깁니다.
   */
  @Override
  public IssuedRefreshToken issue() {
    Instant issuedAt = clock.instant();
    String value = randomTokenValue();
    return new IssuedRefreshToken(
        value, sha256Hex(value), UUID.randomUUID(), issuedAt, issuedAt.plus(REFRESH_TOKEN_TTL));
  }

  /** refresh token 원문을 DB 조회/저장에 사용할 64자 SHA-256 hex hash로 변환합니다. */
  @Override
  public String hash(String refreshToken) {
    return sha256Hex(refreshToken);
  }

  private String randomTokenValue() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("refresh token hash algorithm is not available", exception);
    }
  }
}
