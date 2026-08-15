package com.ronams.carmunity.auth.internal.adapter.out.security;

import com.ronams.carmunity.auth.internal.application.port.out.IdentifierHasherPort;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** HMAC-SHA256으로 로그인 식별자 hash를 생성하는 adapter입니다. */
@RequiredArgsConstructor
@Component
public class HmacIdentifierHasherAdapter implements IdentifierHasherPort {

  private static final String HMAC_SHA_256 = "HmacSHA256";

  private final LoginIdentifierHashProperties properties;

  /**
   * normalized identifier를 64자 lowercase hex HMAC으로 변환합니다.
   *
   * <p>원문 email과 단순 SHA-256 hash는 추론 위험이 있으므로 저장 경계로 넘기지 않습니다.
   */
  @Override
  public String hash(String normalizedIdentifier) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA_256);
      mac.init(
          new SecretKeySpec(
              properties.hmacSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
      return HexFormat.of()
          .formatHex(mac.doFinal(normalizedIdentifier.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("identifier hash algorithm is not available", exception);
    }
  }
}
