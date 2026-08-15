package com.ronams.carmunity.auth.internal.adapter.out.persistence;

import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRecord;
import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * refresh_token 테이블의 JPA 저장 모델입니다.
 *
 * <p>refresh token 원문은 이 entity에 들어오지 않으며, 저장과 조회는 항상 SHA-256 hash 기준으로 수행합니다.
 */
@Entity(name = "AuthRefreshTokenJpaEntity")
@Table(name = "refresh_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AuthRefreshTokenJpaEntity {

  static final String ROTATED_REASON = "ROTATED";
  static final String LOGOUT_REASON = "LOGOUT";
  static final String REUSE_DETECTED_REASON = "REUSE_DETECTED";
  static final String ACCOUNT_SUSPENDED_REASON = "ACCOUNT_SUSPENDED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(
      name = "token_hash",
      nullable = false,
      length = 64,
      unique = true,
      columnDefinition = "char(64)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String tokenHash;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_reason", length = 50)
  private String revokedReason;

  @Column(name = "replaced_by_token_id")
  private Long replacedByTokenId;

  private AuthRefreshTokenJpaEntity(
      Long accountId, String tokenHash, UUID familyId, Instant issuedAt, Instant expiresAt) {
    this.accountId = accountId;
    this.tokenHash = tokenHash;
    this.familyId = familyId;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
  }

  /**
   * rotation으로 발급된 새 refresh token row를 생성합니다.
   *
   * <p>원문 token은 클라이언트에게 한 번만 전달하고, 저장소에는 hash와 lifecycle metadata만 남깁니다.
   */
  static AuthRefreshTokenJpaEntity issue(Long accountId, UUID familyId, IssuedRefreshToken token) {
    return new AuthRefreshTokenJpaEntity(
        accountId, token.hash(), familyId, token.issuedAt(), token.expiresAt());
  }

  RefreshTokenRecord toRecord(UUID accountPublicId) {
    return new RefreshTokenRecord(
        id,
        accountId,
        accountPublicId,
        familyId,
        issuedAt,
        expiresAt,
        revokedAt,
        revokedReason,
        replacedByTokenId);
  }

  /**
   * 기존 token을 rotation 완료 상태로 바꿉니다.
   *
   * <p>last_used_at은 정상 rotation 시각을 남기고, 재사용 탐지는 revoked/replaced 상태를 기준으로 수행합니다.
   */
  void rotateTo(Long replacementTokenId, Instant rotatedAt) {
    this.lastUsedAt = rotatedAt;
    this.revokedAt = rotatedAt;
    this.revokedReason = ROTATED_REASON;
    this.replacedByTokenId = replacementTokenId;
  }

  /** 클라이언트 로그아웃으로 제출된 단일 token을 폐기합니다. */
  void revokeForLogout(Instant loggedOutAt) {
    revoke(LOGOUT_REASON, loggedOutAt);
  }

  void revoke(String reason, Instant revokedAt) {
    if (this.revokedAt != null) {
      return;
    }
    this.revokedAt = revokedAt;
    this.revokedReason = reason;
  }

  Long id() {
    return id;
  }

  Long accountId() {
    return accountId;
  }
}
