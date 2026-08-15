package com.ronams.carmunity.account.api;

import java.time.Instant;
import java.util.UUID;

/**
 * auth 모듈이 비밀번호 로그인 결과를 account 저장 경계에 기록할 때 호출하는 공개 계약입니다.
 *
 * <p>구현체는 account 내부 id 조회, credential 실패 횟수 갱신, refresh token 저장, auth event 저장을 같은 transaction에서
 * 처리합니다.
 */
public interface PasswordLoginResultRecorder {

  /**
   * 로그인 성공 결과를 기록합니다.
   *
   * @param success account public id와 refresh token hash 저장 값입니다.
   */
  void recordSuccess(PasswordLoginSuccess success);

  /**
   * account를 특정한 로그인 실패를 기록합니다.
   *
   * @param accountPublicId 실패한 account public id입니다.
   * @param identifierHash normalized email의 HMAC hash입니다.
   * @param reason 내부 감사 이벤트가 구분할 로그인 실패 원인입니다.
   * @param occurredAt 실패 시각입니다.
   */
  void recordKnownFailure(
      UUID accountPublicId,
      String identifierHash,
      PasswordLoginFailureReason reason,
      Instant occurredAt);

  /**
   * account를 특정하지 못한 로그인 실패를 기록합니다.
   *
   * @param identifierHash normalized email의 HMAC hash입니다.
   * @param occurredAt 실패 시각입니다.
   */
  void recordUnknownFailure(String identifierHash, Instant occurredAt);
}
