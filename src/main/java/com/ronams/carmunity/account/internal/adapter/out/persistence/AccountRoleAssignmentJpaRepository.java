package com.ronams.carmunity.account.internal.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccountRoleAssignmentJpaRepository
    extends JpaRepository<AccountRoleAssignmentJpaEntity, Long> {

  /** 최고 관리자 bootstrap 중 이미 활성 role assignment가 있는지 빠르게 확인합니다. */
  boolean existsByRoleIdAndRevokedAtIsNull(Long roleId);

  /** token claim과 로그인 결과에 노출할 활성 role code만 조회하고 role assignment 내부 id는 숨깁니다. */
  @Query(
      """
      select role.code
      from AccountRoleAssignmentJpaEntity assignment
      join AuthRoleJpaEntity role on role.id = assignment.roleId
      where assignment.accountId = :accountId
        and assignment.revokedAt is null
      order by role.code
      """)
  List<String> findActiveRoleCodesByAccountId(@Param("accountId") Long accountId);
}
