package com.ronams.carmunity.account.internal.domain;

import java.util.Objects;

/**
 * 자체 비밀번호 가입에서 적용하는 password-only 인증 정책입니다.
 *
 * <p>Type: Domain Policy.
 */
public final class PasswordPolicy {

  private static final int MIN_LENGTH = 10;
  private static final int MAX_LENGTH = 128;

  private PasswordPolicy() {}

  /**
   * 비밀번호 원문이 현재 가입 정책을 만족하는지 검증합니다.
   *
   * <p>passphrase 사용을 막지 않기 위해 composition rule은 두지 않고, 전체 공백만 금지합니다.
   *
   * @param rawPassword 해시 전 사용자 입력 비밀번호입니다.
   * @throws IllegalArgumentException 길이 또는 공백 정책을 만족하지 못할 때 발생합니다.
   */
  public static void validate(String rawPassword) {
    String password = Objects.requireNonNullElse(rawPassword, "");
    if (password.length() < MIN_LENGTH) {
      throw new IllegalArgumentException("password must be at least 10 characters");
    }
    if (password.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("password must not exceed 128 characters");
    }
    if (password.isBlank()) {
      throw new IllegalArgumentException("password must not be entirely blank");
    }
  }
}
