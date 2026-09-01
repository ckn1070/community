package com.ronams.carmunity.media.internal.application;

import com.ronams.carmunity.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MediaErrorCode implements ErrorCode {
  INVALID_IMAGE(HttpStatus.BAD_REQUEST, "Invalid image", "지원하지 않거나 손상된 이미지입니다."),
  IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "Image too large", "이미지는 10 MiB 이하여야 합니다."),
  MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "Media not found", "이미지를 찾을 수 없습니다."),
  MEDIA_NOT_READY(HttpStatus.CONFLICT, "Media not ready", "이미지 검증이 아직 완료되지 않았습니다."),
  IDEMPOTENCY_KEY_REUSED(
      HttpStatus.CONFLICT, "Idempotency key reused", "같은 멱등 키를 다른 이미지에 사용할 수 없습니다."),
  DELIVERY_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Media delivery limited", "이미지 전달량 한도를 초과했습니다.");
  private final HttpStatus status;
  private final String title;
  private final String detail;

  MediaErrorCode(HttpStatus status, String title, String detail) {
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
