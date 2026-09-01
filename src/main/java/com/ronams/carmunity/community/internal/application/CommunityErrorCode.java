package com.ronams.carmunity.community.internal.application;

import com.ronams.carmunity.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CommunityErrorCode implements ErrorCode {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid community request", "커뮤니티 요청 값이 올바르지 않습니다."),
  INVALID_PAGINATION_PARAMETER(
      HttpStatus.BAD_REQUEST, "Invalid pagination parameter", "페이지 번호 또는 크기가 올바르지 않습니다."),
  CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Content not found", "콘텐츠를 찾을 수 없습니다."),
  CONTENT_FORBIDDEN(HttpStatus.FORBIDDEN, "Content forbidden", "콘텐츠를 변경할 권한이 없습니다."),
  IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key reused", "멱등성 키가 다른 요청에 사용되었습니다."),
  PRECONDITION_FAILED(
      HttpStatus.PRECONDITION_FAILED, "Precondition failed", "최신 버전을 기준으로 다시 시도해 주세요.");

  private final HttpStatus status;
  private final String title;
  private final String detail;

  CommunityErrorCode(HttpStatus status, String title, String detail) {
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
