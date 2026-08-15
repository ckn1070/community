package com.ronams.carmunity.account.internal.application.command;

import java.util.Objects;

/**
 * 일반 회원 이메일 인증 요청 command입니다.
 *
 * @param email 인증 메일을 요청한 email입니다.
 */
public record RequestMemberEmailVerificationCommand(String email) {

  public RequestMemberEmailVerificationCommand {
    Objects.requireNonNull(email, "email must not be null");
  }
}
