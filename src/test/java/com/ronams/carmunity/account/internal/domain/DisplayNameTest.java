package com.ronams.carmunity.account.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** DisplayName 값 객체가 사용자 표시값과 중복 비교용 normalized 값을 함께 소유하는지 검증합니다. */
class DisplayNameTest {

  /** 표시값은 사람이 보는 공백을 정리하고, normalized 값은 중복 비교를 위해 소문자로 고정합니다. */
  @Test
  void trimsAndCollapsesWhitespaceForDisplayValue() {
    DisplayName displayName = DisplayName.from("  Road   Runner  ");

    assertThat(displayName.value()).isEqualTo("Road Runner");
    assertThat(displayName.normalized()).isEqualTo("road runner");
  }

  /** 공백뿐인 이름은 표시와 중복 비교 모두 의미가 없으므로 domain 경계에서 거부합니다. */
  @Test
  void rejectsBlankDisplayName() {
    assertThatThrownBy(() -> DisplayName.from("   ")).isInstanceOf(IllegalArgumentException.class);
  }

  /** storage limit은 정규화 이후 저장될 표시값 기준으로 초과를 막습니다. */
  @Test
  void rejectsDisplayNameLongerThanStorageLimitAfterNormalization() {
    String tooLongDisplayName = "a".repeat(31);

    assertThatThrownBy(() -> DisplayName.from(tooLongDisplayName))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
