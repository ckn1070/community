package com.ronams.carmunity.account.internal.application.port.out;

/** 비밀번호 재설정 raw token을 생성하는 port입니다. */
public interface PasswordResetTokenGeneratorPort {

  /**
   * 이메일 링크 query parameter에 사용할 bearer secret을 생성합니다.
   *
   * @return raw reset token입니다.
   */
  String generate();
}
