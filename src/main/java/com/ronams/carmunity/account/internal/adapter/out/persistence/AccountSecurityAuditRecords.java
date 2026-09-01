package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.api.PasswordLoginFailureReason;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationConfirmFailureReason;
import com.ronams.carmunity.securityaudit.api.SecurityAuditEventType;
import com.ronams.carmunity.securityaudit.api.SecurityAuditRecord;
import com.ronams.carmunity.securityaudit.api.SecurityAuditResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** account 업무 결과를 securityaudit 공개 기록 계약으로 변환합니다. */
final class AccountSecurityAuditRecords {

  private static final String EMPTY_METADATA = "{}";

  private AccountSecurityAuditRecords() {}

  static SecurityAuditRecord signUpSuccess(UUID accountPublicId, Instant occurredAt) {
    return success(accountPublicId, SecurityAuditEventType.ACCOUNT_SIGN_UP, null, null, occurredAt);
  }

  static SecurityAuditRecord roleGrantedBySuperAdminBootstrap(
      UUID accountPublicId, Instant occurredAt) {
    return success(
        accountPublicId,
        SecurityAuditEventType.ROLE_CHANGE,
        "SUPER_ADMIN_BOOTSTRAP",
        """
        {"roleCode":"ROLE_SUPER_ADMIN","actorType":"SYSTEM","action":"GRANTED"}
        """,
        occurredAt);
  }

  static SecurityAuditRecord passwordResetRequiredByInitialAdminBootstrap(
      UUID accountPublicId, Instant occurredAt) {
    return success(
        accountPublicId,
        SecurityAuditEventType.PASSWORD_CHANGE_REQUIREMENT,
        "INITIAL_ADMIN_BOOTSTRAP",
        """
        {"actorType":"SYSTEM","action":"REQUIRED"}
        """,
        occurredAt);
  }

  static SecurityAuditRecord passwordChangedSuccess(UUID accountPublicId, Instant occurredAt) {
    return success(
        accountPublicId,
        SecurityAuditEventType.PASSWORD_CHANGE,
        null,
        """
        {"actorType":"ACCOUNT","action":"CHANGED"}
        """,
        occurredAt);
  }

  static SecurityAuditRecord passwordChangedFailure(UUID accountPublicId, Instant occurredAt) {
    return failure(
        accountPublicId,
        SecurityAuditEventType.PASSWORD_CHANGE,
        "CURRENT_PASSWORD_MISMATCH",
        null,
        occurredAt);
  }

  static SecurityAuditRecord loginSuccess(UUID accountPublicId, Instant occurredAt) {
    return success(accountPublicId, SecurityAuditEventType.PASSWORD_LOGIN, null, null, occurredAt);
  }

  static SecurityAuditRecord loginFailure(
      UUID accountPublicId,
      String identifierHash,
      PasswordLoginFailureReason reason,
      Instant occurredAt) {
    return new SecurityAuditRecord(
        accountPublicId,
        SecurityAuditEventType.PASSWORD_LOGIN,
        SecurityAuditResult.FAILURE,
        reason.name(),
        identifierHash,
        EMPTY_METADATA,
        occurredAt);
  }

  static SecurityAuditRecord unknownLoginFailure(String identifierHash, Instant occurredAt) {
    return new SecurityAuditRecord(
        null,
        SecurityAuditEventType.PASSWORD_LOGIN,
        SecurityAuditResult.FAILURE,
        "UNKNOWN_IDENTIFIER",
        identifierHash,
        EMPTY_METADATA,
        occurredAt);
  }

  static SecurityAuditRecord emailVerificationRequested(
      UUID accountPublicId, boolean successful, Instant occurredAt) {
    return new SecurityAuditRecord(
        accountPublicId,
        SecurityAuditEventType.EMAIL_VERIFICATION_REQUEST,
        successful ? SecurityAuditResult.SUCCESS : SecurityAuditResult.FAILURE,
        successful ? null : "DELIVERY_FAILED",
        null,
        EMPTY_METADATA,
        occurredAt);
  }

  static SecurityAuditRecord emailVerified(UUID accountPublicId, Instant occurredAt) {
    return success(
        accountPublicId, SecurityAuditEventType.EMAIL_VERIFICATION_CONFIRM, null, null, occurredAt);
  }

  static SecurityAuditRecord emailVerificationConfirmFailure(
      UUID accountPublicId, EmailVerificationConfirmFailureReason reason, Instant occurredAt) {
    return failure(
        accountPublicId,
        SecurityAuditEventType.EMAIL_VERIFICATION_CONFIRM,
        reason.name(),
        null,
        occurredAt);
  }

  static SecurityAuditRecord adminInvitationCreated(
      UUID inviterPublicId,
      UUID invitationPublicId,
      String targetEmailNormalized,
      String roleCode,
      boolean previousInvitationRevoked,
      Instant occurredAt) {
    String metadata =
        """
        {
          "invitationPublicId": "%s",
          "targetEmailHash": "%s",
          "roleCode": "%s",
          "previousInvitationRevoked": %s
        }
        """
            .formatted(
                invitationPublicId,
                sha256Hex(targetEmailNormalized),
                roleCode,
                previousInvitationRevoked);
    return success(
        inviterPublicId,
        SecurityAuditEventType.ADMIN_INVITATION_CREATED,
        null,
        metadata,
        occurredAt);
  }

  static SecurityAuditRecord adminInvitationAccepted(
      UUID acceptedAccountPublicId,
      UUID invitationPublicId,
      String roleCode,
      UUID invitedByAccountPublicId,
      Instant occurredAt) {
    String metadata =
        """
        {
          "invitationPublicId": "%s",
          "roleCode": "%s",
          "invitedByAccountPublicId": "%s"
        }
        """
            .formatted(invitationPublicId, roleCode, invitedByAccountPublicId);
    return success(
        acceptedAccountPublicId,
        SecurityAuditEventType.ADMIN_INVITATION_ACCEPTED,
        null,
        metadata,
        occurredAt);
  }

  static SecurityAuditRecord adminInvitationRevoked(
      UUID revokerPublicId,
      UUID invitationPublicId,
      String targetEmailNormalized,
      Instant occurredAt) {
    String metadata =
        """
        {
          "invitationPublicId": "%s",
          "targetEmailHash": "%s",
          "reasonCode": "ADMIN_REVOKED"
        }
        """
            .formatted(invitationPublicId, sha256Hex(targetEmailNormalized));
    return success(
        revokerPublicId,
        SecurityAuditEventType.ADMIN_INVITATION_REVOKED,
        null,
        metadata,
        occurredAt);
  }

  static SecurityAuditRecord passwordResetRequested(
      UUID accountPublicId, boolean successful, Instant occurredAt) {
    return new SecurityAuditRecord(
        accountPublicId,
        SecurityAuditEventType.PASSWORD_RESET_REQUESTED,
        successful ? SecurityAuditResult.SUCCESS : SecurityAuditResult.FAILURE,
        null,
        null,
        EMPTY_METADATA,
        occurredAt);
  }

  static SecurityAuditRecord passwordResetCompleted(UUID accountPublicId, Instant occurredAt) {
    return success(
        accountPublicId, SecurityAuditEventType.PASSWORD_RESET_COMPLETED, null, null, occurredAt);
  }

  private static SecurityAuditRecord success(
      UUID accountPublicId,
      SecurityAuditEventType eventType,
      String reasonCode,
      String metadata,
      Instant occurredAt) {
    return new SecurityAuditRecord(
        accountPublicId,
        eventType,
        SecurityAuditResult.SUCCESS,
        reasonCode,
        null,
        metadata,
        occurredAt);
  }

  private static SecurityAuditRecord failure(
      UUID accountPublicId,
      SecurityAuditEventType eventType,
      String reasonCode,
      String metadata,
      Instant occurredAt) {
    return new SecurityAuditRecord(
        accountPublicId,
        eventType,
        SecurityAuditResult.FAILURE,
        reasonCode,
        null,
        metadata,
        occurredAt);
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is not available", exception);
    }
  }
}
