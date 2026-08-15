package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.domain.AccountProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * account_profile 테이블의 JPA 저장 모델입니다.
 *
 * <p>표시용 이름과 중복 판단용 normalized 값을 함께 저장해 domain 정규화 결과를 DB unique constraint와 연결합니다.
 */
@Entity
@Table(name = "account_profile")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AccountProfileJpaEntity {

  @Id
  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "display_name", nullable = false, length = 30)
  private String displayName;

  @Column(name = "display_name_normalized", nullable = false, length = 30, unique = true)
  private String displayNameNormalized;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private AccountProfileJpaEntity(Long accountId, AccountProfile profile) {
    this.accountId = accountId;
    this.displayName = profile.displayName().value();
    this.displayNameNormalized = profile.displayName().normalized();
  }

  /**
   * account root row와 profile row를 1:1로 연결합니다.
   *
   * <p>normalized display name은 동시 가입 중복을 막는 DB constraint의 입력값입니다.
   */
  static AccountProfileJpaEntity of(Long accountId, AccountProfile profile) {
    return new AccountProfileJpaEntity(accountId, profile);
  }
}
