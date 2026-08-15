package com.ronams.carmunity.account.internal.domain;

/**
 * account 생명주기 상태입니다.
 *
 * <p>Type: Domain Enum.
 */
public enum AccountStatus {
  PENDING,
  ACTIVE,
  SUSPENDED,
  WITHDRAWN
}
