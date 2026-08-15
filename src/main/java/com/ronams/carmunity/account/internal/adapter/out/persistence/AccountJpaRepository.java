package com.ronams.carmunity.account.internal.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, Long> {

  boolean existsByEmailNormalized(String emailNormalized);

  Optional<AccountJpaEntity> findByEmailNormalized(String emailNormalized);

  Optional<AccountJpaEntity> findByPublicId(UUID publicId);

  @Modifying
  @Query(
      """
      update AccountJpaEntity account
      set account.lastLoginAt = :lastLoginAt,
          account.updatedAt = :lastLoginAt
      where account.id = :accountId
      """)
  int updateLastLoginAt(
      @Param("accountId") Long accountId, @Param("lastLoginAt") Instant lastLoginAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update AccountJpaEntity account
      set account.status = com.ronams.carmunity.account.internal.domain.AccountStatus.ACTIVE,
          account.emailVerifiedAt = :verifiedAt,
          account.updatedAt = :verifiedAt
      where account.id = :accountId
        and account.status = com.ronams.carmunity.account.internal.domain.AccountStatus.PENDING
      """)
  int activatePendingAccount(
      @Param("accountId") Long accountId, @Param("verifiedAt") Instant verifiedAt);
}
