package com.ronams.carmunity.auth.internal.application.service;

import com.ronams.carmunity.account.api.PasswordLoginAccount;
import java.util.Objects;

/** 비밀번호 검증과 account 상태 판정을 통과한 인증 결과입니다. */
public record AuthenticatedPasswordAccount(
    PasswordLoginAccount account, SessionAccess sessionAccess) {

  public AuthenticatedPasswordAccount {
    Objects.requireNonNull(account, "account must not be null");
    Objects.requireNonNull(sessionAccess, "sessionAccess must not be null");
  }
}
