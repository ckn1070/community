package com.ronams.carmunity.account.internal.application.port.in;

import com.ronams.carmunity.account.internal.application.command.RevokeAdminInvitationCommand;

/** 최고 관리자가 open 관리자 초대를 회수하는 유스케이스입니다. */
public interface RevokeAdminInvitationUseCase {

  /**
   * open 관리자 초대를 회수 상태로 전환합니다.
   *
   * @param command 회수자 account public id와 초대 public id입니다.
   */
  void execute(RevokeAdminInvitationCommand command);
}
