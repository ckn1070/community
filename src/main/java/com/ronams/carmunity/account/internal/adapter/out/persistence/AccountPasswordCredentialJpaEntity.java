package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.domain.PasswordCredential;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
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
 * account_password_credential 테이블의 JPA 저장 모델입니다.
 *
 * <p>비밀번호 원문은 이 저장 모델에 들어오지 않고, encoder가 만든 hash와 로그인 잠금 상태만 저장합니다.
 */
@Entity
@Table(name = "account_password_credential")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AccountPasswordCredentialJpaEntity {

  @Id
  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @CreatedDate
  @Column(name = "password_updated_at", nullable = false)
  private Instant passwordUpdatedAt;

  @Column(name = "password_change_required", nullable = false)
  private boolean passwordChangeRequired;

  @Column(name = "failed_login_count", nullable = false)
  private int failedLoginCount;

  @Column(name = "last_failed_login_at")
  private Instant lastFailedLoginAt;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private AccountPasswordCredentialJpaEntity(Long accountId, PasswordCredential credential) {
    this.accountId = accountId;
    this.passwordHash = credential.passwordHash().value();
    this.passwordChangeRequired = credential.passwordChangeRequired();
  }

  /**
   * account root row와 비밀번호 인증 수단을 1:1 credential row로 연결합니다.
   *
   * <p>자체 가입 시점의 실패 횟수와 잠금 정보는 DB 기본값으로 시작합니다.
   */
  static AccountPasswordCredentialJpaEntity of(Long accountId, PasswordCredential credential) {
    return new AccountPasswordCredentialJpaEntity(accountId, credential);
  }

  /** 비밀번호 검증 경계로만 전달할 저장 hash이며 로그나 응답으로 노출하지 않습니다. */
  String passwordHash() {
    return passwordHash;
  }

  /** access token과 refresh 재발급 판단에 반영할 강제 변경 flag입니다. */
  boolean passwordChangeRequired() {
    return passwordChangeRequired;
  }

  /** credential 변경 규칙을 Domain Model에 위임한 뒤 저장 상태에 반영합니다. */
  void changePassword(PasswordCredential changedCredential, Instant changedAt) {
    this.passwordHash = changedCredential.passwordHash().value();
    this.passwordChangeRequired = changedCredential.passwordChangeRequired();
    this.passwordUpdatedAt = changedAt;
    this.updatedAt = changedAt;
  }

  /** 저장 상태를 credential Domain Model로 복원합니다. */
  PasswordCredential toDomain() {
    return new PasswordCredential(PasswordHash.from(passwordHash), passwordChangeRequired);
  }
}
