package com.ronams.carmunity.account.internal.domain;

import java.util.Objects;

/**
 * 저장 가능한 비밀번호 hash 값 객체입니다.
 *
 * <p>Type: Value Object.
 *
 * <p>비밀번호 원문은 domain에 들어오지 않고, password hasher가 만든 hash만 account aggregate가 소유합니다.
 *
 * @param value password encoder가 생성한 저장용 hash입니다.
 */
public record PasswordHash(String value) {

  public PasswordHash {
    if (Objects.requireNonNullElse(value, "").isBlank()) {
      throw new IllegalArgumentException("password hash must not be blank");
    }
  }

  /**
   * password hasher 결과를 저장 가능한 hash 값으로 감쌉니다.
   *
   * @param value password encoder가 생성한 hash입니다.
   * @return 비어 있지 않은 비밀번호 hash입니다.
   */
  public static PasswordHash from(String value) {
    return new PasswordHash(value);
  }
}
