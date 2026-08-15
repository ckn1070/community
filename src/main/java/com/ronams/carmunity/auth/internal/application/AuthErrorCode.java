package com.ronams.carmunity.auth.internal.application;

import com.ronams.carmunity.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * auth 모듈의 HTTP API 업무 오류 코드입니다.
 *
 * <p>비밀번호 로그인은 계정 존재 여부와 비밀번호 불일치를 같은 오류로 닫고, 계정 상태 제한은 별도 403 계약으로 노출합니다.
 *
 * <p>refresh token 검증 실패는 token 존재 여부, 만료, 폐기, 재사용 여부를 구분해 노출하지 않습니다.
 */
public enum AuthErrorCode implements ErrorCode {
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials", "이메일 또는 비밀번호가 올바르지 않습니다."),
  ACCOUNT_LOGIN_RESTRICTED(HttpStatus.FORBIDDEN, "Login restricted", "로그인할 수 없는 계정 상태입니다."),
  INVALID_REFRESH_TOKEN(
      HttpStatus.UNAUTHORIZED, "Invalid refresh token", "refresh token이 유효하지 않습니다.");

  private final HttpStatus status;
  private final String title;
  private final String detail;

  AuthErrorCode(HttpStatus status, String title, String detail) {
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
