package com.ronams.carmunity.auth.internal.adapter.out.security;

import com.ronams.carmunity.auth.internal.application.port.out.PasswordVerifierPort;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Spring Security password encoder 기반 password 검증 adapter입니다. */
@Component
public class SpringSecurityPasswordVerifierAdapter implements PasswordVerifierPort {

  private static final String ARGON2_ID = "argon2";
  private static final int SALT_LENGTH = 16;
  private static final int HASH_LENGTH = 32;
  private static final int PARALLELISM = 1;
  private static final int MEMORY_IN_KIB = 19 * 1024;
  private static final int ITERATIONS = 2;

  private final PasswordEncoder passwordEncoder;

  /** 저장된 DelegatingPasswordEncoder hash를 검증할 운영 기본 encoder를 구성합니다. */
  @Autowired
  public SpringSecurityPasswordVerifierAdapter() {
    this(
        new DelegatingPasswordEncoder(
            ARGON2_ID,
            Map.of(
                ARGON2_ID,
                new Argon2PasswordEncoder(
                    SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_IN_KIB, ITERATIONS))));
  }

  SpringSecurityPasswordVerifierAdapter(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * 원문 password와 저장 hash를 검증합니다.
   *
   * <p>검증 결과만 application에 돌려주고, 원문 password와 hash는 로그나 감사 이벤트로 넘기지 않습니다.
   */
  @Override
  public boolean matches(String rawPassword, String encodedPassword) {
    return passwordEncoder.matches(rawPassword, encodedPassword);
  }
}
