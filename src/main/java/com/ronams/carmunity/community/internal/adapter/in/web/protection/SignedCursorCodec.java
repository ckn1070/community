package com.ronams.carmunity.community.internal.adapter.in.web.protection;

import com.ronams.carmunity.community.internal.application.CursorCodec;
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
public class SignedCursorCodec implements CursorCodec {
  private final byte[] secret;

  public SignedCursorCodec(@Value("${carmunity.community.cursor-hmac-secret}") String secret) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("Community cursor HMAC secret은 32 bytes 이상이어야 합니다.");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  public String encode(String sort, Instant createdAt, long internalId) {
    String payload = "1|" + sort + "|" + createdAt.toEpochMilli() + "|" + internalId;
    return url(payload) + "." + url(sign(payload));
  }

  public Position decode(String encoded, String expectedSort) {
    try {
      String[] parts = encoded.split("\\.", -1);
      String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
      if (parts.length != 2
          || !MessageDigestSupport.constantTime(
              sign(payload), Base64.getUrlDecoder().decode(parts[1])))
        throw new IllegalArgumentException();
      String[] values = payload.split("\\|", -1);
      if (values.length != 4 || !"1".equals(values[0]) || !expectedSort.equals(values[1]))
        throw new IllegalArgumentException();
      return new Position(
          Instant.ofEpochMilli(Long.parseLong(values[2])), Long.parseLong(values[3]));
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("cursor가 올바르지 않습니다.", exception);
    }
  }

  private byte[] sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String url(String value) {
    return url(value.getBytes(StandardCharsets.UTF_8));
  }

  private String url(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private static final class MessageDigestSupport {
    private static boolean constantTime(byte[] left, byte[] right) {
      return java.security.MessageDigest.isEqual(left, right);
    }
  }
}
