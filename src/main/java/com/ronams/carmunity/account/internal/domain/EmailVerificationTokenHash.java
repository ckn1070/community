package com.ronams.carmunity.account.internal.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 이메일 인증 token hash 값 객체입니다.
 *
 * <p>Type: Value Object.
 *
 * <p>원문 token은 저장하지 않고, DB constraint와 같은 64자 lowercase SHA-256 hex 형식만 저장 경계로 통과시킵니다.
 *
 * @param value 저장과 조회에 사용할 SHA-256 hex hash입니다.
 */
public record EmailVerificationTokenHash(String value) {

  private static final Pattern SHA_256_HEX_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

  /**
   * 이미 계산된 token hash 문자열을 검증해 값 객체로 만듭니다.
   *
   * <p>해시 계산은 하지 않고, 저장 경계에서 허용하는 lowercase SHA-256 hex 형식만 통과시킵니다.
   *
   * @param value lowercase SHA-256 hex 문자열입니다.
   * @return 검증된 token hash 값입니다.
   * @throws IllegalArgumentException 값이 비어 있거나 SHA-256 lowercase hex 형식이 아닐 때 발생합니다.
   */
  public static EmailVerificationTokenHash from(String value) {
    String tokenHash = Objects.requireNonNullElse(value, "");
    if (!SHA_256_HEX_PATTERN.matcher(tokenHash).matches()) {
      throw new IllegalArgumentException("email verification token hash must be sha-256 hex");
    }
    return new EmailVerificationTokenHash(tokenHash);
  }
}
