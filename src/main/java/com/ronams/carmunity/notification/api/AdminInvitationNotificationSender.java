package com.ronams.carmunity.notification.api;

/** account 모듈이 관리자 초대 발송을 요청할 때 사용하는 notification 공개 interface입니다. */
public interface AdminInvitationNotificationSender {

  /**
   * 관리자 초대 메시지를 발송합니다.
   *
   * @param request 발송 대상, 초대 public id, raw token, 만료 시각을 담은 요청입니다.
   */
  void send(AdminInvitationNotificationRequest request);
}
