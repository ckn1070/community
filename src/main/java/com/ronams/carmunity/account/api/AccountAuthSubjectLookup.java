package com.ronams.carmunity.account.api;

import java.util.Optional;
import java.util.UUID;

/**
 * 인증 모듈이 account 내부 구현을 알지 않고 token subject 정보를 조회할 때 호출하는 공개 계약입니다.
 *
 * <p>구현체는 account 모듈이 소유하며, auth 모듈은 이 interface만 의존합니다.
 */
public interface AccountAuthSubjectLookup {

  /**
   * public id로 token subject에 필요한 account 정보를 조회합니다.
   *
   * @param accountPublicId 외부 노출 account 식별자입니다.
   * @return token subject로 사용할 수 있는 account 정보입니다.
   */
  Optional<AccountAuthSubject> findByPublicId(UUID accountPublicId);
}
