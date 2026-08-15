package com.ronams.carmunity.account.internal.application.port.out;

/** 관리자 초대 저장과 감사 이벤트 기록을 담당하는 persistence port입니다. */
public interface AdminInvitationStorePort {

  /**
   * 관리자 초대를 저장합니다.
   *
   * @param request 초대 저장에 필요한 정규화된 입력입니다.
   * @return 저장된 초대 snapshot입니다.
   */
  AdminInvitationRecord save(AdminInvitationSaveRequest request);

  /**
   * 관리자 초대 생성 감사 이벤트를 기록합니다.
   *
   * @param invitation 저장된 초대 snapshot입니다.
   * @param notificationSuccessful 초대 이메일 발송 성공 여부입니다.
   */
  void recordCreated(AdminInvitationRecord invitation, boolean notificationSuccessful);
}
