package com.ronams.carmunity.account.internal.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

  /** 비밀번호 변경 완료 후 기존 session을 계속 쓰지 못하도록 account의 활성 refresh token만 폐기합니다. */
  @Modifying
  @Query(
      value =
          """
          update refresh_token
          set revoked_at = :revokedAt,
              revoked_reason = 'PASSWORD_CHANGED'
          where account_id = :accountId
            and revoked_at is null
          """,
      nativeQuery = true)
  int revokeActiveTokensForPasswordChanged(
      @Param("accountId") Long accountId, @Param("revokedAt") Instant revokedAt);
}
