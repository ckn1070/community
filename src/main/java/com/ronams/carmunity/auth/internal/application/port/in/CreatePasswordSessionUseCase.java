package com.ronams.carmunity.auth.internal.application.port.in;

import com.ronams.carmunity.auth.internal.application.command.CreatePasswordSessionCommand;
import com.ronams.carmunity.auth.internal.application.result.CreatedSessionResult;

/** email/password로 인증 session을 생성하는 usecase입니다. */
public interface CreatePasswordSessionUseCase {

  /**
   * 비밀번호 인증을 수행하고 access/refresh token을 발급합니다.
   *
   * @param command 정규화 전 email과 원문 password입니다.
   * @return 클라이언트에 전달할 session 생성 결과입니다.
   */
  CreatedSessionResult execute(CreatePasswordSessionCommand command);
}
