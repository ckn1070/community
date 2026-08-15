package com.ronams.carmunity.account.internal.application.port.out;

import java.util.Optional;

/** 이메일 인증 요청 대상 account를 email 기준으로 조회하는 persistence port입니다. */
public interface EmailVerificationAccountLookupPort {

  /**
   * 정규화된 email로 account를 조회합니다.
   *
   * @param normalizedEmail 정규화된 email입니다.
   * @return 이메일 인증 요청 대상 후보 account입니다.
   */
  Optional<EmailVerificationAccount> findByNormalizedEmail(String normalizedEmail);
}
