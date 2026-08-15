package com.ronams.carmunity.account.internal.adapter.out.security;

import com.ronams.carmunity.account.internal.application.port.out.PasswordHasherPort;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Spring Security password encoder 기반 hashing adapter입니다.
 *
 * <p>새 가입 hash는 Argon2id로 생성하고, {@link DelegatingPasswordEncoder} prefix를 남겨 나중에 알고리즘을 교체하거나 검증할 수
 * 있게 합니다.
 */
@Component
public class SpringSecurityPasswordHasherAdapter implements PasswordHasherPort {

  // Spring Security 기본 id인 argon2를 사용하고, 실제 해시 본문은 Argon2id 형식으로 저장합니다.
  private static final String ARGON2_ID = "argon2";
  // 신규 가입 hash 강도와 저장 형식을 결정하는 값이므로 변경 시 성능과 기존 hash 검증을 함께 확인해야 합니다.
  private static final int SALT_LENGTH = 16;
  private static final int HASH_LENGTH = 32;
  private static final int PARALLELISM = 1;
  private static final int MEMORY_IN_KIB = 19 * 1024;
  private static final int ITERATIONS = 2;

  private final PasswordEncoder passwordEncoder;

  /** 운영 기본 password encoder 정책으로 adapter를 구성합니다. */
  @Autowired
  public SpringSecurityPasswordHasherAdapter() {
    this(
        new DelegatingPasswordEncoder(
            ARGON2_ID,
            Map.of(
                ARGON2_ID,
                new Argon2PasswordEncoder(
                    SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_IN_KIB, ITERATIONS))));
  }

  /** 테스트에서 hash 형식과 원문 미노출 계약을 통제하기 위해 encoder를 주입합니다. */
  SpringSecurityPasswordHasherAdapter(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * 비밀번호 원문을 Argon2id 기반 hash로 변환합니다.
   *
   * @param rawPassword policy 검증이 끝난 비밀번호 원문입니다.
   * @return {@code {argon2}} prefix가 붙은 저장용 hash입니다.
   */
  @Override
  public PasswordHash hash(String rawPassword) {
    return PasswordHash.from(passwordEncoder.encode(rawPassword));
  }

  /** 로그인과 비밀번호 변경 검증에서는 raw password를 저장하지 않고 encoder 비교에만 사용합니다. */
  @Override
  public boolean matches(String rawPassword, String passwordHash) {
    return passwordEncoder.matches(rawPassword, passwordHash);
  }
}
