package com.ronams.carmunity.account.internal.application.port.out;

/**
 * 이메일 인증 완료 실패 감사 이벤트의 reason code입니다.
 *
 * <p>raw token과 token hash는 감사 이벤트에 저장하지 않고, 실패 분류만 남깁니다.
 */
public enum EmailVerificationConfirmFailureReason {
  TOKEN_INVALID,
  TOKEN_EXPIRED,
  TOKEN_ALREADY_USED,
  TOKEN_REVOKED,
  ACCOUNT_NOT_PENDING
}
