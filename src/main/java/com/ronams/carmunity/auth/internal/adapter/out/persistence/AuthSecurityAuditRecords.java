package com.ronams.carmunity.auth.internal.adapter.out.persistence;

import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRejectionReason;
import com.ronams.carmunity.securityaudit.api.SecurityAuditEventType;
import com.ronams.carmunity.securityaudit.api.SecurityAuditRecord;
import com.ronams.carmunity.securityaudit.api.SecurityAuditResult;
import java.time.Instant;
import java.util.UUID;

/** auth session 결과를 securityaudit 공개 기록 계약으로 변환합니다. */
final class AuthSecurityAuditRecords {

  private AuthSecurityAuditRecords() {}

  static SecurityAuditRecord loginSuccess(UUID accountPublicId, Instant occurredAt) {
    return SecurityAuditRecord.of(
        accountPublicId,
        SecurityAuditEventType.PASSWORD_LOGIN,
        SecurityAuditResult.SUCCESS,
        null,
        null,
        occurredAt);
  }

  static SecurityAuditRecord tokenRefreshSuccess(UUID accountPublicId, Instant occurredAt) {
    return SecurityAuditRecord.of(
        accountPublicId,
        SecurityAuditEventType.TOKEN_REFRESH,
        SecurityAuditResult.SUCCESS,
        null,
        null,
        occurredAt);
  }

  static SecurityAuditRecord tokenRefreshRejected(
      UUID accountPublicId, RefreshTokenRejectionReason reason, Instant occurredAt) {
    return SecurityAuditRecord.of(
        accountPublicId,
        SecurityAuditEventType.TOKEN_REFRESH,
        SecurityAuditResult.FAILURE,
        reason.name(),
        null,
        occurredAt);
  }

  static SecurityAuditRecord logoutSuccess(UUID accountPublicId, Instant occurredAt) {
    return SecurityAuditRecord.of(
        accountPublicId,
        SecurityAuditEventType.LOGOUT,
        SecurityAuditResult.SUCCESS,
        null,
        null,
        occurredAt);
  }

  static SecurityAuditRecord tokenReuseDetected(UUID accountPublicId, Instant occurredAt) {
    return SecurityAuditRecord.of(
        accountPublicId,
        SecurityAuditEventType.TOKEN_REFRESH,
        SecurityAuditResult.FAILURE,
        "TOKEN_REUSE_DETECTED",
        null,
        occurredAt);
  }
}
