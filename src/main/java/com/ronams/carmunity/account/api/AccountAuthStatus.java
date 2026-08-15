package com.ronams.carmunity.account.api;

/**
 * auth 모듈이 token 발급 가능 여부를 판단할 때 사용하는 account 상태 공개 계약입니다.
 *
 * <p>account 내부 domain enum을 직접 노출하지 않고, 인증 흐름이 필요한 상태 의미만 named interface로 제공합니다.
 */
public enum AccountAuthStatus {
  PENDING,
  ACTIVE,
  SUSPENDED,
  WITHDRAWN
}
