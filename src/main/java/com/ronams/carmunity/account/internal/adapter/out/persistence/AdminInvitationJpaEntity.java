package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationForAcceptance;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationRecord;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationSaveRequest;
import com.ronams.carmunity.account.internal.domain.AdminInvitation;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * admin_invitation 테이블의 JPA 저장 모델입니다.
 *
 * <p>초대 token 원문은 이 entity에 들어오지 않고, SHA-256 hash와 terminal state만 저장합니다.
 */
@Entity
@Table(name = "admin_invitation")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AdminInvitationJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "email_normalized", nullable = false, length = 254)
  private String emailNormalized;

  @Column(name = "role_id", nullable = false)
  private Long roleId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "char(64)")
  private String tokenHash;

  @Column(name = "invited_by_account_id", nullable = false)
  private Long invitedByAccountId;

  @Column(name = "accepted_account_id")
  private Long acceptedAccountId;

  @Column(name = "revoked_by_account_id")
  private Long revokedByAccountId;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private AdminInvitationJpaEntity(
      AdminInvitationSaveRequest request, Long invitedByAccountId, Long roleId) {
    this.publicId = UUID.randomUUID();
    this.email = request.email().value();
    this.emailNormalized = request.email().normalized();
    this.roleId = roleId;
    this.tokenHash = request.tokenHash();
    this.invitedByAccountId = invitedByAccountId;
    this.issuedAt = request.issuedAt();
    this.expiresAt = request.expiresAt();
  }

  /** 저장 요청과 FK id를 새 초대 row로 변환합니다. */
  static AdminInvitationJpaEntity issue(
      AdminInvitationSaveRequest request, Long invitedByAccountId, Long roleId) {
    return new AdminInvitationJpaEntity(request, invitedByAccountId, roleId);
  }

  /** open 초대를 폐기 상태로 전환합니다. */
  void revoke(Long revokedByAccountId, Instant revokedAt) {
    AdminInvitation revoked = toDomain().revokeAt(revokedAt);
    this.revokedByAccountId = revokedByAccountId;
    this.revokedAt = revoked.revokedAt();
    this.updatedAt = revokedAt;
  }

  /** open 초대를 수락 상태로 전환합니다. */
  void accept(Long acceptedAccountId, Instant acceptedAt) {
    AdminInvitation accepted = toDomain().acceptAt(acceptedAt);
    this.acceptedAccountId = acceptedAccountId;
    this.acceptedAt = accepted.acceptedAt();
    this.updatedAt = acceptedAt;
  }

  private AdminInvitation toDomain() {
    return new AdminInvitation(publicId, expiresAt, acceptedAt, revokedAt);
  }

  /** 저장된 초대 row를 application snapshot으로 변환합니다. */
  AdminInvitationRecord toRecord(RoleCode roleCode, boolean previousInvitationRevoked) {
    return new AdminInvitationRecord(
        id, publicId, email, roleCode, invitedByAccountId, expiresAt, previousInvitationRevoked);
  }

  /** 저장된 초대 row를 수락 검증용 application snapshot으로 변환합니다. */
  AdminInvitationForAcceptance toAcceptance(RoleCode roleCode) {
    return new AdminInvitationForAcceptance(
        id,
        publicId,
        email,
        roleId,
        roleCode,
        tokenHash,
        invitedByAccountId,
        expiresAt,
        acceptedAt != null || revokedAt != null);
  }

  Long id() {
    return id;
  }

  UUID publicId() {
    return publicId;
  }

  String emailNormalized() {
    return emailNormalized;
  }

  Long invitedByAccountId() {
    return invitedByAccountId;
  }

  Long roleId() {
    return roleId;
  }

  boolean closed() {
    return acceptedAt != null || revokedAt != null;
  }
}
