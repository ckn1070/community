package com.ronams.carmunity.securityaudit.internal.adapter.out.persistence;

import com.ronams.carmunity.securityaudit.api.SecurityAuditRecord;
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

/** auth_event 테이블의 유일한 JPA 저장 모델입니다. */
@Entity
@Table(name = "auth_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SecurityAuditJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_public_id")
  private UUID accountPublicId;

  @Column(name = "event_type", nullable = false, length = 50)
  private String eventType;

  @Column(nullable = false, length = 20)
  private String result;

  @Column(name = "reason_code", length = 50)
  private String reasonCode;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "identifier_hash", length = 64, columnDefinition = "char(64)")
  private String identifierHash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String metadata;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  private SecurityAuditJpaEntity(SecurityAuditRecord auditRecord) {
    this.accountPublicId = auditRecord.accountPublicId();
    this.eventType = auditRecord.eventType().name();
    this.result = auditRecord.result().name();
    this.reasonCode = auditRecord.reasonCode();
    this.identifierHash = auditRecord.identifierHash();
    this.metadata = auditRecord.metadata();
    this.occurredAt = auditRecord.occurredAt();
  }

  static SecurityAuditJpaEntity from(SecurityAuditRecord auditRecord) {
    return new SecurityAuditJpaEntity(auditRecord);
  }
}
