package com.ronams.carmunity.account.internal.application.port.out;

/** 이메일 인증 링크에 포함할 raw token을 생성하는 application port입니다. */
public interface EmailVerificationTokenGeneratorPort {

  /**
   * 새 raw token을 생성합니다.
   *
   * @return 저장하지 않고 사용자에게만 전달할 URL-safe token입니다.
   */
  String generate();
}
