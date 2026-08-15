package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.RoleCode;

/** 가입에 필요한 role 기준 데이터 존재 여부를 확인하는 port입니다. */
public interface AuthRoleLookupPort {

  /**
   * role seed가 현재 DB에 존재하는지 확인합니다.
   *
   * @param roleCode 가입 처리에서 참조할 role code입니다.
   * @return 기준 데이터가 있으면 {@code true}입니다.
   */
  boolean existsByCode(RoleCode roleCode);
}
