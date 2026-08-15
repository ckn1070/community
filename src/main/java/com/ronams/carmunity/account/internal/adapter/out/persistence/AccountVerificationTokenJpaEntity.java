package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.StoredEmailVerificationToken;
import com.ronams.carmunity.account.internal.application.port.out.StoredPasswordResetToken;
import com.ronams.carmunity.account.internal.domain.EmailVerificationTokenHash;
import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * account_verification_token 테이블의 JPA 저장 모델입니다.
 *
 * <p>EMAIL_VERIFICATION과 PASSWORD_RESET은 같은 one-time email token 생명주기를 공유하지만, purpose별 adapter가
 * application 모델을 분리합니다.
 */
@Entity
@Table(name = "account_verification_token")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AccountVerificationTokenJpaEntity {

  private static final String EMAIL_VERIFICATION_PURPOSE = "EMAIL_VERIFICATION";
  private static final String PASSWORD_RESET_PURPOSE = "PASSWORD_RESET";
  private static final String EMAIL_DELIVERY_CHANNEL = "EMAIL";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(nullable = false, length = 50)
  private String purpose;

  @Column(name = "delivery_channel", nullable = false, length = 20)
  private String deliveryChannel;

  @Column(name = "recipient_normalized", nullable = false, length = 254)
  private String recipientNormalized;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "char(64)")
  private String tokenHash;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "last_sent_at", nullable = false)
  private Instant lastSentAt;

  @Column(name = "send_count", nullable = false)
  private int sendCount = 1;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private AccountVerificationTokenJpaEntity(
      Long accountId,
      String purpose,
      String recipientNormalized,
      String tokenHash,
      Instant issuedAt,
      Instant expiresAt,
      Instant lastSentAt) {
    this.accountId = accountId;
    this.purpose = purpose;
    this.deliveryChannel = EMAIL_DELIVERY_CHANNEL;
    this.recipientNormalized = recipientNormalized;
    this.tokenHash = tokenHash;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
    this.lastSentAt = lastSentAt;
  }

  /** application port 입력 모델을 이메일 인증 token 저장 row로 변환합니다. */
  static AccountVerificationTokenJpaEntity from(EmailVerificationTokenStoreRequest token) {
    return new AccountVerificationTokenJpaEntity(
        token.accountId(),
        EMAIL_VERIFICATION_PURPOSE,
        token.recipientNormalized(),
        token.tokenHash().value(),
        token.issuedAt(),
        token.expiresAt(),
        token.lastSentAt());
  }

  /** application port 입력 모델을 비밀번호 재설정 token 저장 row로 변환합니다. */
  static AccountVerificationTokenJpaEntity from(PasswordResetTokenStoreRequest token) {
    return new AccountVerificationTokenJpaEntity(
        token.accountId(),
        PASSWORD_RESET_PURPOSE,
        token.recipientNormalized(),
        token.tokenHash().value(),
        token.issuedAt(),
        token.expiresAt(),
        token.lastSentAt());
  }

  /** JPA entity를 application이 판단할 token 상태 모델로 변환합니다. */
  StoredEmailVerificationToken toStoredEmailVerificationToken() {
    return new StoredEmailVerificationToken(
        id,
        accountId,
        recipientNormalized,
        EmailVerificationTokenHash.from(tokenHash),
        issuedAt,
        expiresAt,
        lastSentAt,
        Optional.ofNullable(consumedAt),
        Optional.ofNullable(revokedAt));
  }

  /** JPA entity를 password reset application token 상태 모델로 변환합니다. */
  StoredPasswordResetToken toStoredPasswordResetToken() {
    return new StoredPasswordResetToken(
        id,
        accountId,
        recipientNormalized,
        PasswordResetTokenHash.from(tokenHash),
        issuedAt,
        expiresAt,
        lastSentAt,
        Optional.ofNullable(consumedAt),
        Optional.ofNullable(revokedAt));
  }
}
