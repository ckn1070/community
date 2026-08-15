package com.ronams.carmunity.auth.internal.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 로그인 실패 감사 이벤트에 남길 identifier hash가 secret 기반 HMAC인지 검증합니다. */
class HmacIdentifierHasherAdapterTest {

  /** 같은 normalized identifier와 secret은 같은 64자 hex hash를 만들고 원문을 포함하지 않습니다. */
  @Test
  void hashesNormalizedIdentifierWithHmacSha256() {
    HmacIdentifierHasherAdapter hasher =
        new HmacIdentifierHasherAdapter(
            new LoginIdentifierHashProperties("identifier-secret-must-be-at-least-32-bytes"));

    String hash = hasher.hash("user@example.com");

    assertThat(hash)
        .matches("^[0-9a-f]{64}$")
        .isEqualTo(hasher.hash("user@example.com"))
        .doesNotContain("user@example.com");
  }

  /** secret이 다르면 같은 email도 다른 hash가 되어 단순 SHA-256 대입 공격을 줄입니다. */
  @Test
  void changesHashWhenSecretChanges() {
    HmacIdentifierHasherAdapter first =
        new HmacIdentifierHasherAdapter(
            new LoginIdentifierHashProperties("identifier-secret-must-be-at-least-32-bytes"));
    HmacIdentifierHasherAdapter second =
        new HmacIdentifierHasherAdapter(
            new LoginIdentifierHashProperties("another-secret-must-be-at-least-32-bytes-value"));

    assertThat(first.hash("user@example.com")).isNotEqualTo(second.hash("user@example.com"));
  }
}
