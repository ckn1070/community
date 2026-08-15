package com.ronams.carmunity.account.internal.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccountVerificationTokenJpaRepository
    extends JpaRepository<AccountVerificationTokenJpaEntity, Long> {

  /** {@link LockModeType#PESSIMISTIC_WRITE} JPA lock으로 token hash 조회 row를 잠급니다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AccountVerificationTokenJpaEntity> findByTokenHashAndPurposeAndDeliveryChannel(
      String tokenHash, String purpose, String deliveryChannel);

  /** {@link LockModeType#PESSIMISTIC_WRITE} JPA lock으로 open token 조회 row를 잠급니다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AccountVerificationTokenJpaEntity>
      findByAccountIdAndPurposeAndDeliveryChannelAndConsumedAtIsNullAndRevokedAtIsNull(
          Long accountId, String purpose, String deliveryChannel);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update AccountVerificationTokenJpaEntity token
      set token.revokedAt = :revokedAt,
          token.updatedAt = :revokedAt
      where token.accountId = :accountId
        and token.purpose = :purpose
        and token.deliveryChannel = :deliveryChannel
        and token.consumedAt is null
        and token.revokedAt is null
      """)
  int revokeOpenTokens(
      @Param("accountId") Long accountId,
      @Param("purpose") String purpose,
      @Param("deliveryChannel") String deliveryChannel,
      @Param("revokedAt") Instant revokedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update AccountVerificationTokenJpaEntity token
      set token.consumedAt = :consumedAt,
          token.updatedAt = :consumedAt
      where token.id = :tokenId
        and token.consumedAt is null
        and token.revokedAt is null
      """)
  int consume(@Param("tokenId") Long tokenId, @Param("consumedAt") Instant consumedAt);
}
