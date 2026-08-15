package com.ronams.carmunity.auth.internal.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT access token 발급과 검증에 필요한 설정입니다.
 *
 * <p>HMAC secret은 운영값을 저장소에 두지 않고 외부 설정으로 주입합니다. HS256 최소 보안 강도를 위해 32 bytes 이상만 허용합니다.
 *
 * @param issuer JWT `iss` claim 값입니다.
 * @param accessTokenTtl access token 유효 시간입니다.
 * @param hmacSecret HS256 서명과 검증에 사용할 secret입니다.
 */
@ConfigurationProperties(prefix = "carmunity.auth.jwt")
public record JwtTokenProperties(String issuer, Duration accessTokenTtl, String hmacSecret) {

  public JwtTokenProperties {
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("issuer must not be blank");
    }
    if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
      throw new IllegalArgumentException("accessTokenTtl must be positive");
    }
    if (hmacSecret == null || hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("hmacSecret must be at least 32 bytes");
    }
  }
}
