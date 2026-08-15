package com.ronams.carmunity.auth.internal.application.port.out;

/** application이 password encoder 구현에 기대하는 검증 능력입니다. */
public interface PasswordVerifierPort {

  /**
   * 원문 password가 저장된 password hash와 일치하는지 검증합니다.
   *
   * @param rawPassword 요청에서 받은 원문 password입니다. 저장하거나 로그에 남기지 않습니다.
   * @param encodedPassword account 모듈이 저장한 password hash입니다.
   * @return 일치하면 {@code true}입니다.
   */
  boolean matches(String rawPassword, String encodedPassword);
}
