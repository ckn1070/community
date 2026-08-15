package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.Account;

/** 일반 회원 자체 가입으로 생성되는 account aggregate를 저장하는 port입니다. */
public interface MemberAccountStorePort {

  /**
   * account aggregate와 가입 성공 감사 이벤트를 하나의 저장 단위로 생성합니다.
   *
   * @param account 저장할 가입 account aggregate입니다.
   * @implSpec 구현체는 DB unique constraint race condition을 account 업무 오류 코드로 변환해야 합니다.
   */
  void save(Account account);
}
