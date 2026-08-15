package com.ronams.carmunity.account.internal.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 일반 회원 자체 가입으로 account가 생성되었음을 나타내는 domain event입니다.
 *
 * <p>Type: Domain Event.
 *
 * <p>감사 저장 형식은 persistence adapter가 결정하고, domain event는 발생한 도메인 사실만 표현합니다.
 *
 * @param accountPublicId 이벤트가 발생한 account의 공개 식별자입니다.
 */
public record AccountRegisteredEvent(UUID accountPublicId) {

  public AccountRegisteredEvent {
    Objects.requireNonNull(accountPublicId, "accountPublicId must not be null");
  }

  /** audit 저장 형식과 분리해 account 생성이라는 도메인 사실만 기록합니다. */
  static AccountRegisteredEvent occurred(UUID accountPublicId) {
    return new AccountRegisteredEvent(accountPublicId);
  }
}
