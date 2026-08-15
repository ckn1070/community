package com.ronams.carmunity.auth.internal.application.command;

/**
 * refresh token 로그아웃 유스케이스 입력입니다.
 *
 * @param refreshToken 클라이언트가 보낸 refresh token 원문입니다. 저장하지 않고 hash 조회에만 사용합니다.
 */
public record LogoutCommand(String refreshToken) {

  public LogoutCommand {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new IllegalArgumentException("refreshToken must not be blank");
    }
  }
}
