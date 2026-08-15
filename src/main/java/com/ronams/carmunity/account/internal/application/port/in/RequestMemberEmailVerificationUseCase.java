package com.ronams.carmunity.account.internal.application.port.in;

import com.ronams.carmunity.account.internal.application.command.RequestMemberEmailVerificationCommand;

/** 일반 회원 이메일 인증 요청 use case입니다. */
public interface RequestMemberEmailVerificationUseCase {

  /**
   * 인증 메일 요청을 처리합니다.
   *
   * @param command 인증 메일 요청 command입니다.
   */
  void execute(RequestMemberEmailVerificationCommand command);
}
