package com.ronams.carmunity.account.internal.application.command;

import java.util.Objects;

/**
 * 일반 회원 이메일 인증 완료 command입니다.
 *
 * @param token 사용자가 제출한 raw 이메일 인증 token입니다.
 */
public record ConfirmMemberEmailVerificationCommand(String token) {

  public ConfirmMemberEmailVerificationCommand {
    Objects.requireNonNull(token, "token must not be null");
  }
}
