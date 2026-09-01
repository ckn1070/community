package com.ronams.carmunity.community.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PostTitleTest {

  @Test
  void normalizesUnicodeWhitespace() {
    assertThat(new PostTitle("  주말\t\n드라이브　코스  ").value()).isEqualTo("주말 드라이브 코스");
  }

  @Test
  void countsUnicodeCodePointsAndRejectsOver120() {
    assertThat(new PostTitle("🚗".repeat(120)).value()).hasSize(240);
    assertThatThrownBy(() -> new PostTitle("🚗".repeat(121)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
