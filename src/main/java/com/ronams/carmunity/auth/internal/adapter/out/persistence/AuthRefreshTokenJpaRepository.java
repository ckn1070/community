package com.ronams.carmunity.auth.internal.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuthRefreshTokenJpaRepository extends JpaRepository<AuthRefreshTokenJpaEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select token from AuthRefreshTokenJpaEntity token where token.tokenHash = :tokenHash")
  Optional<AuthRefreshTokenJpaEntity> findByTokenHashForUpdate(
      @Param("tokenHash") String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select token
      from AuthRefreshTokenJpaEntity token
      where token.familyId = :familyId
        and token.revokedAt is null
      """)
  List<AuthRefreshTokenJpaEntity> findActiveFamilyTokensForUpdate(@Param("familyId") UUID familyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select token
      from AuthRefreshTokenJpaEntity token
      where token.accountPublicId = :accountPublicId
        and token.revokedAt is null
      """)
  List<AuthRefreshTokenJpaEntity> findActiveAccountTokensForUpdate(
      @Param("accountPublicId") UUID accountPublicId);
}
