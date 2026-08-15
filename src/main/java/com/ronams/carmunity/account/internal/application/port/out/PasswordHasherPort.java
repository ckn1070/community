package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.PasswordHash;

/** 비밀번호 원문을 저장용 password hash로 변환하는 port입니다. */
public interface PasswordHasherPort {

  /**
   * 비밀번호 원문을 password encoder 정책에 따라 hash로 변환합니다.
   *
   * @param rawPassword policy 검증이 끝난 비밀번호 원문입니다.
   * @return 알고리즘 prefix와 salt가 포함된 저장용 password hash 값입니다.
   * @apiNote 호출자는 반환값만 저장하고 원문 비밀번호를 저장하거나 로그에 남기면 안 됩니다.
   */
  PasswordHash hash(String rawPassword);

  /**
   * 비밀번호 원문이 저장된 hash와 일치하는지 확인합니다.
   *
   * @param rawPassword 사용자가 제출한 비밀번호 원문입니다.
   * @param passwordHash 저장된 password encoder 결과입니다.
   * @return 일치하면 {@code true}입니다.
   */
  boolean matches(String rawPassword, String passwordHash);
}
