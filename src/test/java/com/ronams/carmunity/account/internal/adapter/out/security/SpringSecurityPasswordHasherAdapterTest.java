package com.ronams.carmunity.account.internal.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.account.internal.domain.PasswordHash;
import org.junit.jupiter.api.Test;

/** Spring Security password encoder adapter가 domain에 저장할 hash 형식과 원문 미보관 정책을 지키는지 검증합니다. */
class SpringSecurityPasswordHasherAdapterTest {

  /** 저장값에는 DelegatingPasswordEncoder prefix가 포함되고 raw password가 남지 않아야 합니다. */
  @Test
  void hashesPasswordWithArgon2DelegatingPrefix() {
    SpringSecurityPasswordHasherAdapter adapter = new SpringSecurityPasswordHasherAdapter();

    PasswordHash hash = adapter.hash("correct horse battery staple");

    assertThat(hash.value()).startsWith("{argon2}$argon2id$");
    assertThat(hash.value()).doesNotContain("correct horse battery staple");
  }
}
