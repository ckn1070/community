package com.ronams.carmunity.account.internal.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** PasswordPolicy가 raw password의 길이와 passphrase 허용 범위를 domain 경계에서 검증하는지 확인합니다. */
class PasswordPolicyTest {

  /** 최소 길이만 만족하면 문자 조합 규칙 없이 passphrase를 허용하는 정책을 고정합니다. */
  @Test
  void acceptsPasswordAtMinimumLengthWithoutCompositionRule() {
    assertThatCode(() -> PasswordPolicy.validate("passphrase")).doesNotThrowAnyException();
  }

  /** 내부 공백은 passphrase UX를 위해 허용하지만 전체 blank와는 구분합니다. */
  @Test
  void allowsSpacesInsidePassphrase() {
    assertThatCode(() -> PasswordPolicy.validate("correct horse battery staple"))
        .doesNotThrowAnyException();
  }

  /** brute-force 방어의 하한으로 최소 길이 미만 raw password를 거부합니다. */
  @Test
  void rejectsPasswordShorterThanMinimumLength() {
    assertThatThrownBy(() -> PasswordPolicy.validate("123456789"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** 공백뿐인 입력은 길이 조건을 만족해도 유효한 password로 보지 않습니다. */
  @Test
  void rejectsEntirelyBlankPassword() {
    String blankPassword = " ".repeat(10);

    assertThatThrownBy(() -> PasswordPolicy.validate(blankPassword))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** 과도하게 긴 raw password는 hashing 비용과 저장 경계를 흔들 수 있어 상한에서 거부합니다. */
  @Test
  void rejectsPasswordLongerThanMaximumLength() {
    String tooLongPassword = "a".repeat(129);

    assertThatThrownBy(() -> PasswordPolicy.validate(tooLongPassword))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
