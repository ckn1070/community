package com.ronams.carmunity.account.internal.domain;

/** one-time challenge의 현재 처리 가능 상태입니다. Type: Value Object. */
public enum ChallengeState {
  OPEN,
  CONSUMED,
  REVOKED,
  EXPIRED
}
