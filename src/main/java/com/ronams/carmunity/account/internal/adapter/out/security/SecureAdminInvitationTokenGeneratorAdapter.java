package com.ronams.carmunity.account.internal.adapter.out.security;

import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationTokenGeneratorPort;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SecureRandom 기반 관리자 초대 token 생성 adapter입니다.
 *
 * <p>생성된 raw token은 관리자 account 생성으로 이어지는 bearer secret이므로 DB에 저장하지 않습니다.
 */
@Component
public class SecureAdminInvitationTokenGeneratorAdapter
    implements AdminInvitationTokenGeneratorPort {

  private static final int TOKEN_BYTES = 32;

  private final SecureRandom secureRandom;

  /** 운영 기본 SecureRandom으로 adapter를 구성합니다. */
  @Autowired
  public SecureAdminInvitationTokenGeneratorAdapter() {
    this(new SecureRandom());
  }

  /** 테스트가 token 난수를 통제할 수 있도록 SecureRandom을 외부에서 주입받습니다. */
  SecureAdminInvitationTokenGeneratorAdapter(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  /**
   * 256-bit 난수를 padding 없는 Base64 URL-safe 문자열로 인코딩합니다.
   *
   * @return 관리자 초대 수락 링크에 사용할 raw token입니다.
   */
  @Override
  public String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
