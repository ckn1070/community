package com.ronams.carmunity.account.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** EmailVerificationTokenHash 값 객체가 DB에 저장 가능한 hash 형식만 domain 경계로 통과시키는지 검증합니다. */
class EmailVerificationTokenHashTest {

  /** SHA-256 hex hash는 DB CHAR(64)와 check constraint가 기대하는 lowercase hex 문자열입니다. */
  @Test
  void acceptsLowercaseSha256Hex() {
    EmailVerificationTokenHash tokenHash = EmailVerificationTokenHash.from("a".repeat(64));

    assertThat(tokenHash.value()).isEqualTo("a".repeat(64));
  }

  /** 원문 token이나 잘린 hash가 저장 경계로 들어오면 DB constraint 전에 domain에서 거부합니다. */
  @Test
  void rejectsWrongLengthHash() {
    String shortHash = "a".repeat(63);

    assertThatThrownBy(() -> EmailVerificationTokenHash.from(shortHash))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** hash 비교와 저장 형식을 단순하게 유지하기 위해 uppercase hex는 허용하지 않습니다. */
  @Test
  void rejectsUppercaseHash() {
    String uppercaseHash = "A".repeat(64);

    assertThatThrownBy(() -> EmailVerificationTokenHash.from(uppercaseHash))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** hex 이외 문자는 raw token 오염이나 잘못된 hashing 구현을 조기에 드러냅니다. */
  @Test
  void rejectsNonHexHash() {
    String nonHexHash = "g".repeat(64);

    assertThatThrownBy(() -> EmailVerificationTokenHash.from(nonHexHash))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
