package com.ronams.carmunity.notification.internal.application;

import com.ronams.carmunity.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NotificationErrorCode implements ErrorCode {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid notification request", "알림 요청 값이 올바르지 않습니다."),
  NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found", "알림을 찾을 수 없습니다.");

  private final HttpStatus status;
  private final String title;
  private final String detail;

  NotificationErrorCode(HttpStatus status, String title, String detail) {
    this.status = status;
    this.title = title;
    this.detail = detail;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  public String detail() {
    return detail;
  }

  public String code() {
    return name();
  }
}
