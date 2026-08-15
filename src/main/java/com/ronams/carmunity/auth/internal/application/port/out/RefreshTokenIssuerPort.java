package com.ronams.carmunity.auth.internal.application.port.out;

import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;

/** application이 refresh token 원문 생성과 저장 hash 생성을 요청하는 port입니다. */
public interface RefreshTokenIssuerPort {

  /**
   * 클라이언트에 한 번 전달할 refresh token 원문과 DB 저장용 hash를 함께 발급합니다.
   *
   * @return 원문 refresh token과 hash/lifecycle 값입니다.
   */
  IssuedRefreshToken issue();
}
