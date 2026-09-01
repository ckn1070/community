package com.ronams.carmunity.moderation.internal.application;

import com.ronams.carmunity.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ModerationErrorCode implements ErrorCode {
  REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Report not found", "신고를 찾을 수 없습니다."),
  REPORT_ALREADY_OPEN(HttpStatus.CONFLICT, "Report already open", "같은 콘텐츠에 처리 중인 신고가 있습니다."),
  PRECONDITION_FAILED(
      HttpStatus.PRECONDITION_FAILED, "Precondition failed", "최신 신고 상태를 기준으로 다시 시도해 주세요."),
  INVALID_RESOLUTION(HttpStatus.BAD_REQUEST, "Invalid resolution", "신고 처리 값이 올바르지 않습니다.");
  private final HttpStatus status;
  private final String title;
  private final String detail;

  ModerationErrorCode(HttpStatus status, String title, String detail) {
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
