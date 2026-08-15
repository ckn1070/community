package com.ronams.carmunity.auth.internal.application.model;

/**
 * refresh token rotation이 account 상태 때문에 거부된 reason code입니다.
 *
 * <p>persistence adapter는 이 값을 `TOKEN_REFRESH` 실패 감사 이벤트의 `reason_code`로 저장합니다.
 */
public enum RefreshTokenRejectionReason {
  ACCOUNT_SUSPENDED,
  ACCOUNT_WITHDRAWN
}
