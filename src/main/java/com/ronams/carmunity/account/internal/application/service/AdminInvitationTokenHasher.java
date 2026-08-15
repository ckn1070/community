package com.ronams.carmunity.account.internal.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 관리자 초대 raw token을 저장 가능한 hash로 변환합니다.
 *
 * <p>raw token은 이메일 링크와 수락 request에만 존재하고, persistence 경계에는 SHA-256 hash만 전달합니다.
 */
@Component
public class AdminInvitationTokenHasher {

  /**
   * raw token을 SHA-256 lowercase hex로 변환합니다.
   *
   * @param rawToken 이메일 링크에 담긴 원문 token입니다.
   * @return 저장과 조회에 사용할 token hash입니다.
   * @throws IllegalArgumentException raw token이 비어 있을 때 발생합니다.
   */
  public String hash(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("raw token must not be blank");
    }
    MessageDigest digest = sha256();
    byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash);
  }

  private MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is not available", exception);
    }
  }
}
