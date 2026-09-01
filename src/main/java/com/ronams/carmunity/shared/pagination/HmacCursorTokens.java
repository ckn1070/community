package com.ronams.carmunity.shared.pagination;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
class HmacCursorTokens implements CursorTokens {
  private final byte[] secret;

  HmacCursorTokens(@Value("${carmunity.community.cursor-hmac-secret}") String secret) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalStateException("Community cursor HMAC secret은 32 bytes 이상이어야 합니다.");
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  public String encode(String sort, Instant createdAt, long id) {
    String payload = "1|" + sort + "|" + createdAt.toEpochMilli() + "|" + id;
    return url(payload.getBytes(StandardCharsets.UTF_8)) + "." + url(sign(payload));
  }

  public Position decode(String token, String sort) {
    try {
      String[] parts = token.split("\\.", -1);
      String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
      if (parts.length != 2
          || !java.security.MessageDigest.isEqual(
              sign(payload), Base64.getUrlDecoder().decode(parts[1])))
        throw new IllegalArgumentException();
      String[] values = payload.split("\\|", -1);
      if (values.length != 4 || !"1".equals(values[0]) || !sort.equals(values[1]))
        throw new IllegalArgumentException();
      return new Position(
          Instant.ofEpochMilli(Long.parseLong(values[2])), Long.parseLong(values[3]));
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("cursor가 올바르지 않습니다.", e);
    }
  }

  private byte[] sign(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private String url(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }
}
