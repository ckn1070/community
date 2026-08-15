package com.ronams.carmunity.auth.internal.application.port.out;

import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRecord;
import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRejectionReason;
import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import java.time.Instant;
import java.util.Optional;

/**
 * refresh token lifecycle 저장소 port입니다.
 *
 * <p>구현체는 같은 token을 동시에 rotation하지 못하도록 조회 시점에 필요한 DB lock을 잡아야 합니다.
 */
public interface RefreshTokenLifecycleStorePort {

  /**
   * token hash로 refresh token을 조회하고 rotation 판단 동안 다른 transaction이 같은 row를 바꾸지 못하게 잠급니다.
   *
   * @param tokenHash refresh token 원문의 저장용 hash입니다.
   * @return rotation 판단에 사용할 저장 token snapshot입니다.
   */
  Optional<RefreshTokenRecord> findByHashForUpdate(String tokenHash);

  /**
   * 새 token을 저장하고 기존 token을 ROTATED로 폐기합니다.
   *
   * @param currentToken lock이 잡힌 기존 refresh token입니다.
   * @param replacementToken 클라이언트에 전달할 새 refresh token 발급 결과입니다.
   * @param rotatedAt rotation 시각입니다.
   */
  void rotate(
      RefreshTokenRecord currentToken, IssuedRefreshToken replacementToken, Instant rotatedAt);

  /**
   * 제출된 단일 refresh token을 LOGOUT으로 폐기합니다.
   *
   * <p>호출자는 {@link #findByHashForUpdate(String)}로 가져온 row lock을 같은 transaction 안에서 유지해야 합니다.
   *
   * @param currentToken lock이 잡힌 로그아웃 대상 refresh token입니다.
   * @param loggedOutAt 로그아웃 처리 시각입니다.
   */
  void revokeForLogout(RefreshTokenRecord currentToken, Instant loggedOutAt);

  /**
   * 재사용이 감지된 token family의 활성 token을 REUSE_DETECTED로 폐기합니다.
   *
   * @param reusedToken 재사용으로 판단된 refresh token입니다.
   * @param detectedAt 탐지 시각입니다.
   */
  void revokeFamilyForReuse(RefreshTokenRecord reusedToken, Instant detectedAt);

  /**
   * refresh 시점의 account 상태가 token 발급을 허용하지 않을 때 family를 폐기합니다.
   *
   * @param currentToken 상태 거부를 유발한 refresh token입니다.
   * @param reason account 상태 거부 reason code입니다.
   * @param rejectedAt 거부 시각입니다.
   */
  void revokeFamilyForRejectedAccount(
      RefreshTokenRecord currentToken, RefreshTokenRejectionReason reason, Instant rejectedAt);
}
