package com.ronams.carmunity.auth.internal.application.port.in;

import com.ronams.carmunity.auth.internal.application.command.RotateRefreshTokenCommand;
import com.ronams.carmunity.auth.internal.application.result.RotatedRefreshTokenResult;

/** refresh token 원문으로 access token과 refresh token을 재발급하는 유스케이스입니다. */
public interface RotateRefreshTokenUseCase {

  /**
   * refresh token rotation을 수행합니다.
   *
   * @param command refresh token 원문을 담은 입력입니다.
   * @return 클라이언트에 한 번 전달할 새 token 묶음입니다.
   */
  RotatedRefreshTokenResult execute(RotateRefreshTokenCommand command);
}
