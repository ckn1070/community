package com.ronams.carmunity.account.internal.application.port.in;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.InviteAdminCommand;
import com.ronams.carmunity.account.internal.application.result.AdminInvitationResult;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;

/** 최고 관리자가 관리자 초대를 생성하는 유스케이스입니다. */
public interface InviteAdminUseCase {

  /**
   * 관리자 초대를 생성하고 commit 이후 초대 이메일 발송을 시도합니다.
   *
   * @param command 초대 생성 입력입니다.
   * @return 생성된 초대의 public id, 대상 email, role, 만료 시각입니다.
   * @throws ApiException {@link CommonErrorCode#INVALID_REQUEST}, {@link
   *     AccountErrorCode#EMAIL_ALREADY_USED}, {@link
   *     AccountErrorCode#ADMIN_INVITATION_ROLE_NOT_ALLOWED}, {@link
   *     AccountErrorCode#AUTH_ROLE_NOT_CONFIGURED} 상황에서 발생합니다.
   */
  AdminInvitationResult execute(InviteAdminCommand command);
}
