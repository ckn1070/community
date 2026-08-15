package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** EmailVerificationTokenHasher가 raw token을 저장 가능한 SHA-256 lowercase hex로만 변환하는지 검증합니다. */
class EmailVerificationTokenHasherTest {

  private final EmailVerificationTokenHasher hasher = new EmailVerificationTokenHasher();

  /** 같은 raw token은 항상 같은 SHA-256 lowercase hex hash로 변환됩니다. */
  @Test
  void hashesRawTokenDeterministically() {
    String tokenHash = hasher.hash("raw-verification-token").value();

    assertThat(tokenHash).hasSize(64).matches("^[0-9a-f]{64}$");
    assertThat(hasher.hash("raw-verification-token").value()).isEqualTo(tokenHash);
  }

  /** raw token이 다르면 DB unique constraint가 구분할 수 있는 다른 hash가 나와야 합니다. */
  @Test
  void createsDifferentHashesForDifferentTokens() {
    assertThat(hasher.hash("raw-token-a").value()).isNotEqualTo(hasher.hash("raw-token-b").value());
  }

  /** 비어 있는 raw token은 SHA-256 계산 전에 IllegalArgumentException으로 거부합니다. */
  @Test
  void rejectsBlankRawToken() {
    assertThatThrownBy(() -> hasher.hash(" ")).isInstanceOf(IllegalArgumentException.class);
  }
}
