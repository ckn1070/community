package com.ronams.carmunity.account.internal.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AdminInvitationJpaRepository extends JpaRepository<AdminInvitationJpaEntity, Long> {

  /** 같은 email의 open 초대를 폐기할 수 있도록 pessimistic write lock으로 조회합니다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select invitation
      from AdminInvitationJpaEntity invitation
      where invitation.emailNormalized = :emailNormalized
        and invitation.acceptedAt is null
        and invitation.revokedAt is null
      """)
  List<AdminInvitationJpaEntity> findOpenByEmailNormalizedForUpdate(
      @Param("emailNormalized") String emailNormalized);

  /** 초대 수락/회수 transaction에서 terminal state를 판정할 수 있도록 row를 잠급니다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AdminInvitationJpaEntity> findByPublicId(UUID publicId);

  /** 초대 수락 저장에서 같은 transaction 안에 초대 row를 다시 잠급니다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AdminInvitationJpaEntity> findById(Long id);
}
