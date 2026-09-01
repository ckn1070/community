package com.ronams.carmunity.account.internal.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccountPasswordCredentialJpaRepository
    extends JpaRepository<AccountPasswordCredentialJpaEntity, Long> {

  /** 로그인 성공 후 credential 실패 상태만 초기화하고 password hash와 강제 변경 flag는 건드리지 않습니다. */
  @Modifying
  @Query(
      """
      update AccountPasswordCredentialJpaEntity credential
      set credential.failedLoginCount = 0,
          credential.lastFailedLoginAt = null,
          credential.lockedUntil = null,
          credential.updatedAt = :updatedAt
      where credential.accountId = :accountId
      """)
  int resetLoginFailureState(
      @Param("accountId") Long accountId, @Param("updatedAt") Instant updatedAt);

  /** account를 특정한 로그인 실패만 누적합니다. hard lock 적용 여부는 별도 abuse 정책에서 결정합니다. */
  @Modifying
  @Query(
      """
      update AccountPasswordCredentialJpaEntity credential
      set credential.failedLoginCount = credential.failedLoginCount + 1,
          credential.lastFailedLoginAt = :failedAt,
          credential.updatedAt = :failedAt
      where credential.accountId = :accountId
      """)
  int recordLoginFailure(@Param("accountId") Long accountId, @Param("failedAt") Instant failedAt);
}
