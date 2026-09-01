package com.ronams.carmunity.catalog.internal.application;

import com.ronams.carmunity.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 공개 카탈로그 조회에서 사용하는 안정적인 API 오류 코드입니다. */
public enum CatalogErrorCode implements ErrorCode {
  CATALOG_MODEL_NOT_FOUND(
      HttpStatus.NOT_FOUND, "Catalog model not found", "조회할 수 있는 카탈로그 모델 또는 연식이 없습니다.");

  private final HttpStatus status;
  private final String title;
  private final String detail;

  CatalogErrorCode(HttpStatus status, String title, String detail) {
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
