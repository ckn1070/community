package com.ronams.carmunity.account.internal.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuthRoleJpaRepository extends JpaRepository<AuthRoleJpaEntity, Long> {

  /** application 계층이 요청한 role code가 기준 데이터로 존재하는지 확인합니다. */
  boolean existsByCode(String code);

  /** account 저장 graph 생성 중 role code를 내부 role id로 매핑합니다. */
  Optional<AuthRoleJpaEntity> findByCode(String code);

  /** 최초 최고 관리자 bootstrap 중 같은 role 기준 row를 잠가 중복 생성 race condition을 막습니다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select role from AuthRoleJpaEntity role where role.code = :code")
  Optional<AuthRoleJpaEntity> findByCodeForUpdate(@Param("code") String code);
}
