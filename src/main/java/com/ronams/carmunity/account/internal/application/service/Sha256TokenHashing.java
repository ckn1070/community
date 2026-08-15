package com.ronams.carmunity.account.internal.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** account email 기반 bearer token을 저장 가능한 SHA-256 hex hash로 변환하는 내부 유틸리티입니다. */
final class Sha256TokenHashing {

  private Sha256TokenHashing() {}

  static String hashRawToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("raw token must not be blank");
    }
    byte[] hash = sha256().digest(rawToken.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash);
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is not available", exception);
    }
  }
}
