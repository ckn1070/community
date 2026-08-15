package com.ronams.carmunity.account.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** EmailAddress 값 객체가 입력 정규화와 DB 저장 한계를 domain 경계에서 책임지는지 검증합니다. */
class EmailAddressTest {

  /** email은 가입 전후 비교가 흔들리지 않도록 trim과 lowercase를 같은 normalized 값으로 보관합니다. */
  @Test
  void trimsAndLowercasesEmail() {
    EmailAddress email = EmailAddress.from("  USER@Example.COM  ");

    assertThat(email.value()).isEqualTo("user@example.com");
    assertThat(email.normalized()).isEqualTo("user@example.com");
  }

  /** 빈 email은 Web/JPA에 도달하기 전에 domain 값 객체에서 거부합니다. */
  @Test
  void rejectsBlankEmail() {
    assertThatThrownBy(() -> EmailAddress.from(" ")).isInstanceOf(IllegalArgumentException.class);
  }

  /** 최소 형식 검증은 외부 annotation에만 맡기지 않고 domain 경계에서도 고정합니다. */
  @Test
  void rejectsEmailWithoutAtSign() {
    assertThatThrownBy(() -> EmailAddress.from("user.example.com"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** DB column 길이와 domain 정책이 어긋나지 않도록 저장 한계를 domain 테스트로 고정합니다. */
  @Test
  void rejectsEmailLongerThanStorageLimit() {
    String email = "a".repeat(245) + "@example.com";

    assertThatThrownBy(() -> EmailAddress.from(email)).isInstanceOf(IllegalArgumentException.class);
  }
}
