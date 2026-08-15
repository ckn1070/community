package com.ronams.carmunity.account.internal.application.port.in;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.BootstrapSuperAdminAccountCommand;
import com.ronams.carmunity.account.internal.application.result.BootstrapSuperAdminAccountResult;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;

/** 최초 최고 관리자 bootstrap 유스케이스입니다. */
public interface BootstrapSuperAdminAccountUseCase {

  /**
   * 활성 최고 관리자가 없으면 최초 최고 관리자 account graph를 생성합니다.
   *
   * @param command 정규화 전 운영자 입력입니다. email과 displayName은 domain 값 객체에서 정규화합니다.
   * @return 생성 또는 이미 bootstrap됨 상태를 나타내는 결과입니다.
   * @throws ApiException {@link CommonErrorCode#INVALID_REQUEST}, {@link
   *     AccountErrorCode#AUTH_ROLE_NOT_CONFIGURED} 상황에서 발생합니다.
   * @apiNote 비밀번호 원문은 command adapter와 hashing adapter 사이에서만 사용하고 저장/출력하지 않습니다.
   */
  BootstrapSuperAdminAccountResult execute(BootstrapSuperAdminAccountCommand command);
}
