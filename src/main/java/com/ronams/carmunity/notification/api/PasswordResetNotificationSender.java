package com.ronams.carmunity.notification.api;

/** account 모듈이 비밀번호 재설정 발송을 요청할 때 사용하는 notification 공개 interface입니다. */
public interface PasswordResetNotificationSender {

  /**
   * 비밀번호 재설정 메시지를 발송합니다.
   *
   * @param request 발송 대상, raw token, 만료 시각을 담은 요청입니다.
   */
  void send(PasswordResetNotificationRequest request);
}
