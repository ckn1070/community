package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.domain.Account;
import com.ronams.carmunity.account.internal.domain.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * account 테이블의 JPA 저장 모델입니다.
 *
 * <p>domain {@link Account}는 profile, password credential, role assignment, auth event와 함께 저장되므로 이
 * entity는 account root row만 표현합니다.
 */
@Entity
@Table(name = "account")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AccountJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "email_normalized", nullable = false, length = 254, unique = true)
  private String emailNormalized;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccountStatus status;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private AccountJpaEntity(
      UUID publicId,
      String email,
      String emailNormalized,
      AccountStatus status,
      Instant emailVerifiedAt) {
    this.publicId = publicId;
    this.email = email;
    this.emailNormalized = emailNormalized;
    this.status = status;
    this.emailVerifiedAt = emailVerifiedAt;
  }

  /**
   * domain account root를 account 테이블 row로 변환합니다.
   *
   * <p>profile과 password credential은 별도 entity가 저장하므로 이 factory에서 함께 펼치지 않습니다.
   */
  static AccountJpaEntity from(Account account) {
    return new AccountJpaEntity(
        account.publicId(),
        account.email().value(),
        account.email().normalized(),
        account.status(),
        account.emailVerifiedAt().orElse(null));
  }

  Long id() {
    return id;
  }

  UUID publicId() {
    return publicId;
  }

  String email() {
    return email;
  }

  String emailNormalized() {
    return emailNormalized;
  }

  AccountStatus status() {
    return status;
  }
}
