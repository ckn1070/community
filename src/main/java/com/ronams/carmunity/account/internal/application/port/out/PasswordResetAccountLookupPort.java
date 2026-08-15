package com.ronams.carmunity.account.internal.application.port.out;

import java.util.Optional;

/** 비밀번호 재설정 요청 email로 account 상태를 조회하는 persistence port입니다. */
public interface PasswordResetAccountLookupPort {

  /**
   * normalized email과 일치하는 account를 조회합니다.
   *
   * @param normalizedEmail domain EmailAddress로 정규화한 email입니다.
   * @return reset 발급 판단에 필요한 account snapshot입니다.
   */
  Optional<PasswordResetAccount> findByNormalizedEmail(String normalizedEmail);
}
