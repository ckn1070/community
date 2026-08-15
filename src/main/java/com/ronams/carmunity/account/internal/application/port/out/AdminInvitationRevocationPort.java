package com.ronams.carmunity.account.internal.application.port.out;

/** 관리자 초대 회수를 담당하는 persistence port입니다. */
public interface AdminInvitationRevocationPort {

  /**
   * open 초대를 회수 상태로 전환하고 감사 이벤트를 저장합니다.
   *
   * @param request 회수 저장 요청입니다.
   */
  void revoke(AdminInvitationRevokeRequest request);
}
