package com.ronams.carmunity.securityaudit.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 다른 Context가 securityaudit에 전달하는 저장 독립적인 보안 감사 사실입니다. */
public record SecurityAuditRecord(
    UUID accountPublicId,
    SecurityAuditEventType eventType,
    SecurityAuditResult result,
    String reasonCode,
    String identifierHash,
    String metadata,
    Instant occurredAt) {

  public SecurityAuditRecord {
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(result, "result must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    metadata = metadata == null ? "{}" : metadata;
  }

  /** metadata가 없는 감사 사실을 생성합니다. */
  public static SecurityAuditRecord of(
      UUID accountPublicId,
      SecurityAuditEventType eventType,
      SecurityAuditResult result,
      String reasonCode,
      String identifierHash,
      Instant occurredAt) {
    return new SecurityAuditRecord(
        accountPublicId, eventType, result, reasonCode, identifierHash, "{}", occurredAt);
  }
}
