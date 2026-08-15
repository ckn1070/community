package com.ronams.carmunity.auth.internal.adapter.out.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** JWT 설정 secret을 HS256 서명/검증에 사용할 JCA key로 변환하는 내부 helper입니다. */
final class HmacJwtSecretKey {

  private static final String HMAC_SHA_256 = "HmacSHA256";

  private HmacJwtSecretKey() {}

  /**
   * UTF-8 secret 문자열을 HS256용 key로 변환합니다.
   *
   * <p>secret 길이와 공백 검증은 설정 binding 경계인 {@link JwtTokenProperties}가 먼저 수행합니다.
   */
  static SecretKey from(String secret) {
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256);
  }
}
