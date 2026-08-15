package com.ronams.carmunity.auth.internal.application.port.in;

import com.ronams.carmunity.auth.internal.application.command.LogoutCommand;

/** refresh token 원문으로 인증 session을 로그아웃 처리하는 유스케이스입니다. */
public interface LogoutUseCase {

  /**
   * refresh token을 로그아웃 사유로 폐기합니다.
   *
   * <p>token이 없거나 이미 사용할 수 없는 상태여도 token 상태를 외부에 노출하지 않고 멱등 성공으로 닫습니다.
   *
   * @param command refresh token 원문을 담은 입력입니다.
   */
  void execute(LogoutCommand command);
}
