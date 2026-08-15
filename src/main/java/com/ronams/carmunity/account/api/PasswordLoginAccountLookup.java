package com.ronams.carmunity.account.api;

import java.util.Optional;

/**
 * auth 모듈이 비밀번호 로그인 판단에 필요한 account 정보를 조회할 때 호출하는 공개 계약입니다.
 *
 * <p>구현체는 account 모듈 내부 저장소를 사용하지만, auth 모듈에는 normalized email 기반 조회 능력만 노출합니다.
 */
public interface PasswordLoginAccountLookup {

  /**
   * normalized email로 로그인 판단용 account 정보를 조회합니다.
   *
   * @param normalizedEmail 정규화가 끝난 email입니다.
   * @return account가 존재하면 password hash, 상태, 역할을 포함한 공개 조회 모델입니다.
   */
  Optional<PasswordLoginAccount> findByNormalizedEmail(String normalizedEmail);
}
