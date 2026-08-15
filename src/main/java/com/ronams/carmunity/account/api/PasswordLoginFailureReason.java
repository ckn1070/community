package com.ronams.carmunity.account.api;

/**
 * 비밀번호 로그인 실패 감사 이벤트의 reason code 공개 계약입니다.
 *
 * <p>auth 모듈은 외부 응답과 별개로 내부 감사 이벤트가 구분해야 하는 실패 원인을 account 저장 경계에 명시해서 전달합니다.
 */
public enum PasswordLoginFailureReason {
  PASSWORD_MISMATCH,
  ACCOUNT_SUSPENDED,
  ACCOUNT_WITHDRAWN
}
