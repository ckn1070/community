package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.api.StoredRefreshToken;
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
 * <p>원문 refresh token은 저장하지 않고, hash와 rotation을 위한 family id만 저장합니다.
 */
@Entity
@Table(name = "refresh_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RefreshTokenJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(
      name = "token_hash",
      nullable = false,
      length = 64,
      unique = true,
      columnDefinition = "char(64)")
  private String tokenHash;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  private RefreshTokenJpaEntity(Long accountId, StoredRefreshToken refreshToken) {
    this.accountId = accountId;
    this.tokenHash = refreshToken.tokenHash();
    this.familyId = refreshToken.familyId();
    this.issuedAt = refreshToken.issuedAt();
    this.expiresAt = refreshToken.expiresAt();
  }

  /** account 내부 id와 저장용 refresh token 값을 테이블 row로 변환합니다. */
  static RefreshTokenJpaEntity of(Long accountId, StoredRefreshToken refreshToken) {
    return new RefreshTokenJpaEntity(accountId, refreshToken);
  }
}
