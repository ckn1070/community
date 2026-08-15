package com.ronams.carmunity.account.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 비밀번호 재설정 token hash 값 객체의 저장 경계 검증을 고정합니다. */
class PasswordResetTokenHashTest {

  /** 저장 가능한 token hash는 64자 lowercase SHA-256 hex 형식입니다. */
  @Test
  void acceptsLowercaseSha256Hex() {
    PasswordResetTokenHash tokenHash = PasswordResetTokenHash.from("a".repeat(64));

    assertThat(tokenHash.value()).isEqualTo("a".repeat(64));
  }

  /** raw token이나 대문자/짧은 값은 persistence 경계로 들어오지 못해야 합니다. */
  @Test
  void rejectsNonSha256HexValue() {
    String uppercaseHash = "A".repeat(64);

    assertThatThrownBy(() -> PasswordResetTokenHash.from("raw-token"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PasswordResetTokenHash.from(uppercaseHash))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
