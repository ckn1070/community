package com.ronams.carmunity.account.internal.application.port.out;

/** 관리자 초대와 생성 감사 이벤트를 하나의 저장 단위로 기록하는 persistence port입니다. */
public interface AdminInvitationStorePort {

  /**
   * 기존 open 초대 회수, 새 초대와 생성 감사 이벤트를 같은 transaction에 저장합니다.
   *
   * @param request 초대 저장에 필요한 정규화된 입력입니다.
   * @return 저장된 초대 snapshot입니다.
   */
  AdminInvitationRecord save(AdminInvitationSaveRequest request);
}
