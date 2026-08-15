package com.ronams.carmunity.account.internal.adapter.out.security;

import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenGeneratorPort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenGeneratorPort;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SecureRandom 기반 account email token 생성 adapter입니다.
 *
 * <p>생성된 raw token은 bearer secret이므로 DB에 저장하지 않고, application service가 목적별 hash 값만 persistence로
 * 전달합니다.
 */
@Component
public class SecureEmailVerificationTokenGeneratorAdapter
    implements EmailVerificationTokenGeneratorPort, PasswordResetTokenGeneratorPort {

  private static final int TOKEN_BYTES = 32;

  private final SecureRandom secureRandom;

  /** 운영 기본 SecureRandom으로 adapter를 구성합니다. */
  @Autowired
  public SecureEmailVerificationTokenGeneratorAdapter() {
    this(new SecureRandom());
  }

  /** 테스트가 token 난수를 통제할 수 있도록 SecureRandom을 외부에서 주입받습니다. */
  SecureEmailVerificationTokenGeneratorAdapter(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  /**
   * 256-bit 난수를 padding 없는 Base64 URL-safe 문자열로 인코딩합니다.
   *
   * @return 이메일 링크 query parameter에 사용할 raw token입니다.
   */
  @Override
  public String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
