package com.ronams.carmunity.account.internal.application.port.in;

import com.ronams.carmunity.account.internal.application.command.RequestPasswordResetCommand;

/** 비로그인 비밀번호 재설정 요청을 처리하는 inbound port입니다. */
public interface RequestPasswordResetUseCase {

  /**
   * 비밀번호 재설정 token 발급과 발송을 요청합니다.
   *
   * <p>account 존재 여부와 발송 여부는 public 응답에 노출하지 않습니다.
   *
   * @param command reset token 발급 요청입니다.
   */
  void execute(RequestPasswordResetCommand command);
}
