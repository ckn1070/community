package com.ronams.carmunity.account.internal.application.port.in;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ConfirmPasswordResetCommand;
import com.ronams.carmunity.shared.error.ApiException;

/** 비밀번호 재설정 token을 소비해 새 비밀번호를 저장하는 inbound port입니다. */
public interface ConfirmPasswordResetUseCase {

  /**
   * reset token을 검증한 뒤 새 password hash를 저장합니다.
   *
   * @param command reset token과 새 비밀번호 입력입니다.
   * @throws ApiException {@link AccountErrorCode#PASSWORD_RESET_TOKEN_INVALID}, {@link
   *     AccountErrorCode#PASSWORD_RESET_TOKEN_EXPIRED} 상황에서 발생합니다.
   */
  void execute(ConfirmPasswordResetCommand command);
}
