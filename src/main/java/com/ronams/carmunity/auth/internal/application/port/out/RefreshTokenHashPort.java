package com.ronams.carmunity.auth.internal.application.port.out;

/** refresh token 원문을 저장소 조회용 hash로 변환하는 port입니다. */
public interface RefreshTokenHashPort {

  /**
   * refresh token 원문을 저장소에 남기는 hash 표현으로 변환합니다.
   *
   * @param refreshToken 클라이언트가 보낸 refresh token 원문입니다.
   * @return 저장소 조회에 사용할 hash입니다.
   */
  String hash(String refreshToken);
}
