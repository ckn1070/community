package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.AccountStatus;
import java.util.Objects;

/**
 * 이메일 인증 요청 use case가 필요한 account 조회 결과입니다.
 *
 * @param accountId 내부 account id입니다.
 * @param recipientNormalized 발송 대상 email snapshot입니다.
 * @param status 현재 account 상태입니다.
 */
public record EmailVerificationAccount(
    Long accountId, String recipientNormalized, AccountStatus status) {

  public EmailVerificationAccount {
    Objects.requireNonNull(accountId, "accountId must not be null");
    Objects.requireNonNull(recipientNormalized, "recipientNormalized must not be null");
    Objects.requireNonNull(status, "status must not be null");
  }
}
