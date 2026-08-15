package com.ronams.carmunity.account.internal.application.port.out;

import java.time.Instant;

/** 이메일 인증 완료 시 account 상태를 활성화하는 persistence port입니다. */
public interface EmailVerificationAccountActivationPort {

  /**
   * PENDING account를 ACTIVE로 전환하고 이메일 인증 시각을 기록합니다.
   *
   * @param accountId 내부 account id입니다.
   * @param verifiedAt 이메일 인증 완료 시각입니다.
   * @return PENDING account가 실제로 활성화되었으면 true입니다.
   */
  boolean activatePendingAccount(Long accountId, Instant verifiedAt);
}
