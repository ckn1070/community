package com.ronams.carmunity.account.internal.application.port.in;

import com.ronams.carmunity.account.internal.application.command.ConfirmMemberEmailVerificationCommand;

/** 일반 회원 이메일 인증 완료 use case입니다. */
public interface ConfirmMemberEmailVerificationUseCase {

  /**
   * 이메일 인증 token을 소비하고 account를 활성화합니다.
   *
   * @param command 인증 완료 command입니다.
   */
  void execute(ConfirmMemberEmailVerificationCommand command);
}
