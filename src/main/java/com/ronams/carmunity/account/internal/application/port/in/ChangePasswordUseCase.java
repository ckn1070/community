package com.ronams.carmunity.account.internal.application.port.in;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ChangePasswordCommand;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;

/** 인증된 사용자의 비밀번호 변경 유스케이스입니다. */
public interface ChangePasswordUseCase {

  /**
   * current password를 확인한 뒤 새 비밀번호 hash를 저장하고 refresh token family를 폐기합니다.
   *
   * @param command 인증된 사용자와 비밀번호 변경 입력입니다.
   * @throws ApiException {@link CommonErrorCode#INVALID_REQUEST}, {@link
   *     AccountErrorCode#CURRENT_PASSWORD_MISMATCH} 상황에서 발생합니다.
   */
  void execute(ChangePasswordCommand command);
}
