package com.ronams.carmunity.auth.internal.adapter.out.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 실패 감사 이벤트의 identifier hash HMAC 설정입니다.
 *
 * <p>JWT 서명 secret과 분리해 token rotation과 감사 식별자 hash 정책이 서로 묶이지 않게 합니다.
 *
 * @param hmacSecret normalized identifier를 HMAC-SHA256으로 hash할 때 사용할 secret입니다.
 */
@ConfigurationProperties(prefix = "carmunity.auth.login.identifier-hash")
public record LoginIdentifierHashProperties(String hmacSecret) {

  public LoginIdentifierHashProperties {
    if (hmacSecret == null || hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("hmacSecret must be at least 32 bytes");
    }
  }
}
