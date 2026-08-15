package com.ronams.carmunity.account.internal.application.port.in;

import com.ronams.carmunity.account.internal.application.command.AcceptAdminInvitationCommand;
import com.ronams.carmunity.account.internal.application.result.AcceptedAdminInvitationResult;

/** 초대받은 사용자가 public token으로 관리자 초대를 수락하는 유스케이스입니다. */
public interface AcceptAdminInvitationUseCase {

  /**
   * 초대 token을 검증하고 새 관리자 account를 생성합니다.
   *
   * @param command 초대 public id, raw token, 비밀번호, 표시 이름입니다.
   * @return 생성된 관리자 account 공개 결과입니다.
   */
  AcceptedAdminInvitationResult execute(AcceptAdminInvitationCommand command);
}
