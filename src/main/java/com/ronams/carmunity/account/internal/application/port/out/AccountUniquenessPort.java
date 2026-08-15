package com.ronams.carmunity.account.internal.application.port.out;

/** 회원가입 전에 account 고유값 사용 여부를 확인하는 port입니다. */
public interface AccountUniquenessPort {

  /**
   * normalized email이 이미 저장되어 있는지 확인합니다.
   *
   * @param normalizedEmail domain 값 객체가 정규화한 email입니다.
   * @return 이미 사용 중이면 {@code true}입니다.
   */
  boolean existsByNormalizedEmail(String normalizedEmail);

  /**
   * normalized display name이 이미 저장되어 있는지 확인합니다.
   *
   * @param normalizedDisplayName domain 값 객체가 정규화한 표시 이름입니다.
   * @return 이미 사용 중이면 {@code true}입니다.
   */
  boolean existsByNormalizedDisplayName(String normalizedDisplayName);
}
