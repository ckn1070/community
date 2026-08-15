package com.ronams.carmunity.notification.api;

/**
 * notification 모듈이 발송 실패를 호출 모듈에 전달할 때 사용하는 공개 예외입니다.
 *
 * <p>provider 응답 본문, API key, raw request 같은 외부 연동 세부정보는 이 예외 메시지에 담지 않습니다.
 */
public class EmailNotificationException extends RuntimeException {

  private final EmailNotificationFailureType failureType;

  public EmailNotificationException(EmailNotificationFailureType failureType, Throwable cause) {
    super(failureType.name(), cause);
    this.failureType = failureType;
  }

  public EmailNotificationException(EmailNotificationFailureType failureType) {
    super(failureType.name());
    this.failureType = failureType;
  }

  /** 실패 분류를 반환합니다. */
  public EmailNotificationFailureType failureType() {
    return failureType;
  }
}
