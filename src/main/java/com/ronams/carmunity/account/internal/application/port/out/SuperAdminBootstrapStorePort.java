package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.Account;
import java.util.Optional;

/** 최초 최고 관리자 bootstrap 저장 경계입니다. */
public interface SuperAdminBootstrapStorePort {

  /**
   * 활성 최고 관리자 role assignment가 이미 있는지 확인합니다.
   *
   * @return 활성 `ROLE_SUPER_ADMIN` 계정이 있으면 {@code true}입니다.
   */
  boolean activeSuperAdminExists();

  /**
   * 저장 transaction 안에서 활성 최고 관리자 존재 여부를 다시 확인한 뒤 없을 때만 저장합니다.
   *
   * @param account 저장할 최초 최고 관리자 account graph입니다.
   * @return 저장했다면 account, 이미 존재한다면 empty입니다.
   */
  Optional<Account> saveIfNoActiveSuperAdmin(Account account);
}
