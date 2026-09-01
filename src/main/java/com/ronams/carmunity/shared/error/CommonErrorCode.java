package com.ronams.carmunity.shared.error;

import org.springframework.http.HttpStatus;

/**
 * 특정 업무 모듈에 속하지 않는 공통 API 오류 코드입니다.
 *
 * <p>업무 규칙 오류는 각 모듈에서 별도 {@link ErrorCode}로 정의하고, 여기는 framework/security/common 오류만 둡니다.
 */
public enum CommonErrorCode implements ErrorCode {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request", "요청 값이 올바르지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized", "인증이 필요합니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden", "요청한 리소스에 접근할 권한이 없습니다."),
  METHOD_NOT_ALLOWED(
      HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", "지원하지 않는 HTTP method입니다."),
  UNSUPPORTED_MEDIA_TYPE(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", "지원하지 않는 Content-Type입니다."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "예상하지 못한 서버 오류입니다.");

  private final HttpStatus status;
  private final String title;
  private final String detail;

  CommonErrorCode(HttpStatus status, String title, String detail) {
    this.status = status;
    this.title = title;
    this.detail = detail;
  }

  @Override
  public String code() {
    return name();
  }

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String title() {
    return title;
  }

  @Override
  public String detail() {
    return detail;
  }
}
