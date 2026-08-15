package com.ronams.carmunity.account.internal.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * account_role_assignment 테이블의 JPA 저장 모델입니다.
 *
 * <p>자체 가입에서 생성되는 초기 role은 시스템 자동 부여이며, 관리자 부여자 정보를 남기지 않습니다.
 */
@Entity
@Table(name = "account_role_assignment")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AccountRoleAssignmentJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "role_id", nullable = false)
  private Long roleId;

  @CreatedDate
  @Column(name = "granted_at", nullable = false)
  private Instant grantedAt;

  @Column(name = "granted_by_account_id")
  private Long grantedByAccountId;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  private AccountRoleAssignmentJpaEntity(Long accountId, Long roleId, Long grantedByAccountId) {
    this.accountId = accountId;
    this.roleId = roleId;
    this.grantedByAccountId = grantedByAccountId;
  }

  /**
   * 자체 가입 초기 role assignment를 시스템 부여 이력으로 저장합니다.
   *
   * <p>관리자 초대/부여 기능이 생기기 전까지 granted_by 계열 컬럼은 사용하지 않습니다.
   */
  static AccountRoleAssignmentJpaEntity systemGranted(Long accountId, Long roleId) {
    return new AccountRoleAssignmentJpaEntity(accountId, roleId, null);
  }

  /**
   * 관리자 초대 수락으로 생성되는 role assignment를 초대 생성자를 부여자로 저장합니다.
   *
   * <p>초대 수락은 시스템 seed가 아니라 최고 관리자의 권한 부여 결과이므로 부여자 account id를 남깁니다.
   */
  static AccountRoleAssignmentJpaEntity adminInvitationGranted(
      Long accountId, Long roleId, Long grantedByAccountId) {
    return new AccountRoleAssignmentJpaEntity(accountId, roleId, grantedByAccountId);
  }
}
