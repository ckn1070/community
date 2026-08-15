package com.ronams.carmunity.account.internal.application.port.out;

/** 인증 감사 이벤트를 저장하는 persistence port입니다. */
public interface AuthEventStorePort {

  /**
   * 이메일 인증 요청 처리 결과를 감사 이벤트로 저장합니다.
   *
   * @param accountId 내부 account id입니다.
   * @param successful 발송 처리 성공 여부입니다.
   */
  void recordEmailVerificationRequested(Long accountId, boolean successful);

  /**
   * 이메일 인증 완료 감사 이벤트를 저장합니다.
   *
   * @param accountId 내부 account id입니다.
   */
  void recordEmailVerified(Long accountId);

  /**
   * 이메일 인증 완료 실패 감사 이벤트를 저장합니다.
   *
   * @param accountId token에서 account를 특정할 수 있으면 내부 account id, 특정할 수 없으면 null입니다.
   * @param reason 실패 reason code입니다.
   */
  void recordEmailVerificationConfirmFailure(
      Long accountId, EmailVerificationConfirmFailureReason reason);

  /**
   * 비밀번호 재설정 요청 처리 결과를 감사 이벤트로 저장합니다.
   *
   * @param accountId 내부 account id입니다.
   * @param successful 발송 처리 성공 여부입니다.
   */
  void recordPasswordResetRequested(Long accountId, boolean successful);
}
