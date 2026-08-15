package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import org.junit.jupiter.api.Test;

/** 비밀번호 재설정 raw token을 저장 가능한 hash로 바꾸는 규칙을 검증합니다. */
class PasswordResetTokenHasherTest {

  private final PasswordResetTokenHasher hasher = new PasswordResetTokenHasher();

  /** 같은 raw token은 같은 SHA-256 lowercase hex hash로 변환됩니다. */
  @Test
  void hashesRawTokenToSha256Hex() {
    PasswordResetTokenHash tokenHash = hasher.hash("reset-raw-token");

    assertThat(tokenHash.value()).matches("^[0-9a-f]{64}$");
    assertThat(hasher.hash("reset-raw-token")).isEqualTo(tokenHash);
  }

  /** 빈 raw token은 token 조회 전에 요청 오류로 바뀔 수 있게 거부합니다. */
  @Test
  void rejectsBlankRawToken() {
    assertThatThrownBy(() -> hasher.hash(" ")).isInstanceOf(IllegalArgumentException.class);
  }
}
