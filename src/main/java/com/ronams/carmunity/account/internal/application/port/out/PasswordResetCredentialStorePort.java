package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.PasswordHash;
import java.time.Instant;

/** reset token 검증 후 password credential을 갱신하는 persistence port입니다. */
public interface PasswordResetCredentialStorePort {

  /**
   * 새 password hash 저장, 강제 변경 flag 해제, 활성 refresh token 폐기, 완료 감사를 한 저장 경계에 위임합니다.
   *
   * @param accountId 내부 account id입니다.
   * @param passwordHash 저장 가능한 새 password hash입니다.
   * @param changedAt reset 완료 시각입니다.
   */
  void resetPassword(Long accountId, PasswordHash passwordHash, Instant changedAt);
}
