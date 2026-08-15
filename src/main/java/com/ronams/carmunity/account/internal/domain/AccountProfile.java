package com.ronams.carmunity.account.internal.domain;

import java.util.Objects;

/**
 * account aggregate가 소유하는 공개 profile입니다.
 *
 * <p>Type: Account aggregate 내부 Value Object.
 *
 * <p>신규 account는 자체 가입이든 관리자 초대 수락이든 공개 표시 이름을 반드시 가지고 시작합니다.
 *
 * @param displayName 공개 표시 이름입니다.
 */
public record AccountProfile(DisplayName displayName) {

  public AccountProfile {
    Objects.requireNonNull(displayName, "displayName must not be null");
  }

  /** 자체 가입 회원이 반드시 가져야 하는 기본 공개 profile을 만듭니다. */
  static AccountProfile memberProfile(DisplayName displayName) {
    return new AccountProfile(displayName);
  }

  /** 관리자 초대 수락으로 생성되는 account의 공개 profile을 만듭니다. */
  static AccountProfile adminInvitationProfile(DisplayName displayName) {
    return new AccountProfile(displayName);
  }
}
