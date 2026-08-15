package com.ronams.carmunity.account.internal.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** SecureEmailVerificationTokenGeneratorAdapter가 이메일 링크에 실을 raw token을 안전한 형식으로 생성하는지 검증합니다. */
class SecureEmailVerificationTokenGeneratorAdapterTest {

  private final SecureEmailVerificationTokenGeneratorAdapter adapter =
      new SecureEmailVerificationTokenGeneratorAdapter();

  /** 생성 token은 URL query에 padding 없이 넣을 수 있는 Base64 URL-safe 문자열이어야 합니다. */
  @Test
  void generatesUrlSafeTokenWithoutPadding() {
    String token = adapter.generate();

    assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
  }

  /** raw token은 계정 인증 링크의 bearer secret이므로 호출마다 새 값을 반환해야 합니다. */
  @Test
  void generatesDifferentTokens() {
    assertThat(adapter.generate()).isNotEqualTo(adapter.generate());
  }
}
