package com.ronams.carmunity.account.internal.application;

import com.ronams.carmunity.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * account 모듈의 HTTP API 업무 오류 코드입니다.
 *
 * <p>domain 내부 예외가 아니라 application/web 경계에서 공통 Problem Details 응답으로 변환할 계약입니다.
 */
public enum AccountErrorCode implements ErrorCode {
  EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "Email already used", "이미 사용 중인 이메일입니다."),
  DISPLAY_NAME_ALREADY_USED(HttpStatus.CONFLICT, "Display name already used", "이미 사용 중인 표시 이름입니다."),
  EMAIL_VERIFICATION_TOKEN_INVALID(
      HttpStatus.BAD_REQUEST, "Invalid email verification token", "이메일 인증 토큰이 올바르지 않습니다."),
  EMAIL_VERIFICATION_TOKEN_EXPIRED(
      HttpStatus.BAD_REQUEST, "Expired email verification token", "이메일 인증 토큰이 만료되었습니다."),
  PASSWORD_RESET_TOKEN_INVALID(
      HttpStatus.BAD_REQUEST, "Invalid password reset token", "비밀번호 재설정 토큰이 올바르지 않습니다."),
  PASSWORD_RESET_TOKEN_EXPIRED(
      HttpStatus.BAD_REQUEST, "Expired password reset token", "비밀번호 재설정 토큰이 만료되었습니다."),
  CURRENT_PASSWORD_MISMATCH(
      HttpStatus.UNAUTHORIZED, "Current password mismatch", "현재 비밀번호가 올바르지 않습니다."),
  ADMIN_INVITATION_NOT_FOUND(
      HttpStatus.NOT_FOUND, "Admin invitation not found", "관리자 초대를 찾을 수 없습니다."),
  ADMIN_INVITATION_TOKEN_INVALID(
      HttpStatus.BAD_REQUEST, "Invalid admin invitation token", "관리자 초대 토큰이 올바르지 않습니다."),
  ADMIN_INVITATION_TOKEN_EXPIRED(
      HttpStatus.BAD_REQUEST, "Expired admin invitation token", "관리자 초대 토큰이 만료되었습니다."),
  ADMIN_INVITATION_ALREADY_CLOSED(
      HttpStatus.CONFLICT, "Admin invitation already closed", "이미 종료된 관리자 초대입니다."),
  ADMIN_INVITATION_ROLE_NOT_ALLOWED(
      HttpStatus.BAD_REQUEST, "Admin invitation role not allowed", "초대할 수 없는 관리자 역할입니다."),
  AUTH_ROLE_NOT_CONFIGURED(
      HttpStatus.INTERNAL_SERVER_ERROR, "Auth role not configured", "필수 역할 기준 데이터가 설정되지 않았습니다.");

  private final HttpStatus status;
  private final String title;
  private final String detail;

  AccountErrorCode(HttpStatus status, String title, String detail) {
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
