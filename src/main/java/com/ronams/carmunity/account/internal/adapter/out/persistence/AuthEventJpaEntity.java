package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.api.PasswordLoginFailureReason;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationConfirmFailureReason;
import com.ronams.carmunity.account.internal.domain.AccountRegisteredEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * auth_event 테이블의 JPA 저장 모델입니다.
 *
 * <p>domain event를 감사 저장 형식으로 변환하며, domain 모델이 audit table의 event_type/result 문자열을 알지 않게 합니다.
 */
@Entity
@Table(name = "auth_event")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AuthEventJpaEntity {

  private static final String ACCOUNT_SIGN_UP_EVENT_TYPE = "ACCOUNT_SIGN_UP";
  private static final String PASSWORD_LOGIN_EVENT_TYPE = "PASSWORD_LOGIN";
  private static final String EMAIL_VERIFICATION_REQUEST_EVENT_TYPE = "EMAIL_VERIFICATION_REQUEST";
  private static final String EMAIL_VERIFICATION_CONFIRM_EVENT_TYPE = "EMAIL_VERIFICATION_CONFIRM";
  private static final String PASSWORD_CHANGE_EVENT_TYPE = "PASSWORD_CHANGE";
  private static final String PASSWORD_CHANGE_REQUIREMENT_EVENT_TYPE =
      "PASSWORD_CHANGE_REQUIREMENT";
  private static final String PASSWORD_RESET_REQUESTED_EVENT_TYPE = "PASSWORD_RESET_REQUESTED";
  private static final String PASSWORD_RESET_COMPLETED_EVENT_TYPE = "PASSWORD_RESET_COMPLETED";
  private static final String ROLE_CHANGE_EVENT_TYPE = "ROLE_CHANGE";
  private static final String ADMIN_INVITATION_CREATED_EVENT_TYPE = "ADMIN_INVITATION_CREATED";
  private static final String ADMIN_INVITATION_ACCEPTED_EVENT_TYPE = "ADMIN_INVITATION_ACCEPTED";
  private static final String ADMIN_INVITATION_REVOKED_EVENT_TYPE = "ADMIN_INVITATION_REVOKED";
  private static final String SUCCESS_RESULT = "SUCCESS";
  private static final String FAILURE_RESULT = "FAILURE";
  private static final String UNKNOWN_IDENTIFIER_REASON = "UNKNOWN_IDENTIFIER";
  private static final String DELIVERY_FAILED_REASON = "DELIVERY_FAILED";
  private static final String SUPER_ADMIN_BOOTSTRAP_REASON = "SUPER_ADMIN_BOOTSTRAP";
  private static final String INITIAL_ADMIN_BOOTSTRAP_REASON = "INITIAL_ADMIN_BOOTSTRAP";
  private static final String CURRENT_PASSWORD_MISMATCH_REASON = "CURRENT_PASSWORD_MISMATCH";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id")
  private Long accountId;

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
  private String metadata = "{}";

  @CreatedDate
  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  private AuthEventJpaEntity(Long accountId, String eventType, String result) {
    this.accountId = accountId;
    this.eventType = eventType;
    this.result = result;
  }

  /**
   * 일반 회원 자체 가입 domain event를 ACCOUNT_SIGN_UP/SUCCESS 감사 레코드로 변환합니다.
   *
   * <p>현재 event payload는 account id 연결만 필요하므로 metadata에는 민감하거나 중복되는 가입 입력을 남기지 않습니다.
   */
  static AuthEventJpaEntity signUpSuccess(Long accountId, AccountRegisteredEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    return new AuthEventJpaEntity(accountId, ACCOUNT_SIGN_UP_EVENT_TYPE, SUCCESS_RESULT);
  }

  /** 최초 최고 관리자 role 부여 감사 레코드를 생성합니다. */
  static AuthEventJpaEntity roleGrantedBySuperAdminBootstrap(Long accountId) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(accountId, ROLE_CHANGE_EVENT_TYPE, SUCCESS_RESULT);
    event.reasonCode = SUPER_ADMIN_BOOTSTRAP_REASON;
    event.metadata =
        """
        {"roleCode":"ROLE_SUPER_ADMIN","actorType":"SYSTEM","action":"GRANTED"}
        """;
    return event;
  }

  /** 최초 최고 관리자 초기 비밀번호 변경 강제 감사 레코드를 생성합니다. */
  static AuthEventJpaEntity passwordResetRequiredByInitialAdminBootstrap(Long accountId) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(accountId, PASSWORD_CHANGE_REQUIREMENT_EVENT_TYPE, SUCCESS_RESULT);
    event.reasonCode = INITIAL_ADMIN_BOOTSTRAP_REASON;
    event.metadata =
        """
        {"actorType":"SYSTEM","action":"REQUIRED"}
        """;
    return event;
  }

  /** 비밀번호 변경 성공 감사 레코드를 생성합니다. */
  static AuthEventJpaEntity passwordChangedSuccess(Long accountId, Instant occurredAt) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(accountId, PASSWORD_CHANGE_EVENT_TYPE, SUCCESS_RESULT);
    event.occurredAt = occurredAt;
    event.metadata =
        """
        {"actorType":"ACCOUNT","action":"CHANGED"}
        """;
    return event;
  }

  /** current password 불일치로 비밀번호 변경에 실패한 감사 레코드를 생성합니다. */
  static AuthEventJpaEntity passwordChangedFailureForCurrentMismatch(
      Long accountId, Instant occurredAt) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(accountId, PASSWORD_CHANGE_EVENT_TYPE, FAILURE_RESULT);
    event.reasonCode = CURRENT_PASSWORD_MISMATCH_REASON;
    event.occurredAt = occurredAt;
    return event;
  }

  /** 로그인 성공 감사 레코드를 생성합니다. */
  static AuthEventJpaEntity loginSuccess(Long accountId, Instant occurredAt) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(accountId, PASSWORD_LOGIN_EVENT_TYPE, SUCCESS_RESULT);
    event.occurredAt = occurredAt;
    return event;
  }

  /**
   * account를 특정한 로그인 실패 감사 레코드를 생성합니다.
   *
   * <p>account id를 연결하더라도 반복 실패 분석을 위해 identifier hash를 함께 남기고, 원문 email은 저장하지 않습니다.
   */
  static AuthEventJpaEntity loginFailureForKnownAccount(
      Long accountId,
      String identifierHash,
      PasswordLoginFailureReason reason,
      Instant occurredAt) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(accountId, PASSWORD_LOGIN_EVENT_TYPE, FAILURE_RESULT);
    event.reasonCode = reason.name();
    event.identifierHash = identifierHash;
    event.occurredAt = occurredAt;
    return event;
  }

  /** account를 특정하지 못한 로그인 실패 감사 레코드를 생성합니다. */
  static AuthEventJpaEntity loginFailureForUnknownAccount(
      String identifierHash, Instant occurredAt) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(null, PASSWORD_LOGIN_EVENT_TYPE, FAILURE_RESULT);
    event.reasonCode = UNKNOWN_IDENTIFIER_REASON;
    event.identifierHash = identifierHash;
    event.occurredAt = occurredAt;
    return event;
  }

  /**
   * 이메일 인증 메일 요청 결과를 인증 감사 이벤트로 변환합니다.
   *
   * <p>provider message id나 원문 email은 이번 범위의 delivery tracking이 아니므로 metadata에 남기지 않습니다.
   */
  static AuthEventJpaEntity emailVerificationRequested(Long accountId, boolean successful) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(
            accountId,
            EMAIL_VERIFICATION_REQUEST_EVENT_TYPE,
            successful ? SUCCESS_RESULT : FAILURE_RESULT);
    if (!successful) {
      event.reasonCode = DELIVERY_FAILED_REASON;
    }
    return event;
  }

  /** 이메일 인증 완료를 인증 감사 이벤트로 변환합니다. */
  static AuthEventJpaEntity emailVerified(Long accountId) {
    return new AuthEventJpaEntity(accountId, EMAIL_VERIFICATION_CONFIRM_EVENT_TYPE, SUCCESS_RESULT);
  }

  /** 이메일 인증 완료 실패를 reason code가 있는 인증 감사 이벤트로 변환합니다. */
  static AuthEventJpaEntity emailVerificationConfirmFailure(
      Long accountId, EmailVerificationConfirmFailureReason reason) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(accountId, EMAIL_VERIFICATION_CONFIRM_EVENT_TYPE, FAILURE_RESULT);
    event.reasonCode = reason.name();
    return event;
  }

  /** 관리자 초대 생성 결과를 인증 감사 이벤트로 변환합니다. */
  static AuthEventJpaEntity adminInvitationCreated(
      Long inviterAccountId,
      UUID invitationPublicId,
      String targetEmailNormalized,
      String roleCode,
      boolean previousInvitationRevoked) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(
            inviterAccountId, ADMIN_INVITATION_CREATED_EVENT_TYPE, SUCCESS_RESULT);
    event.metadata =
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
    return event;
  }

  /** 관리자 초대 수락 결과를 인증 감사 이벤트로 변환합니다. */
  static AuthEventJpaEntity adminInvitationAccepted(
      Long acceptedAccountId, UUID invitationPublicId, String roleCode, Long invitedByAccountId) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(
            acceptedAccountId, ADMIN_INVITATION_ACCEPTED_EVENT_TYPE, SUCCESS_RESULT);
    event.metadata =
        """
        {
          "invitationPublicId": "%s",
          "roleCode": "%s",
          "invitedByAccountId": %d
        }
        """
            .formatted(invitationPublicId, roleCode, invitedByAccountId);
    return event;
  }

  /** 관리자 초대 회수 결과를 인증 감사 이벤트로 변환합니다. */
  static AuthEventJpaEntity adminInvitationRevoked(
      Long revokerAccountId, UUID invitationPublicId, String targetEmailNormalized) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(
            revokerAccountId, ADMIN_INVITATION_REVOKED_EVENT_TYPE, SUCCESS_RESULT);
    event.metadata =
        """
        {
          "invitationPublicId": "%s",
          "targetEmailHash": "%s",
          "reasonCode": "ADMIN_REVOKED"
        }
        """
            .formatted(invitationPublicId, sha256Hex(targetEmailNormalized));
    return event;
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is not available", exception);
    }
  }

  /**
   * 비밀번호 재설정 메일 요청 결과를 인증 감사 이벤트로 변환합니다.
   *
   * <p>provider message id나 원문 email은 이번 범위의 delivery tracking이 아니므로 metadata에 남기지 않습니다.
   */
  static AuthEventJpaEntity passwordResetRequested(Long accountId, boolean successful) {
    return new AuthEventJpaEntity(
        accountId,
        PASSWORD_RESET_REQUESTED_EVENT_TYPE,
        successful ? SUCCESS_RESULT : FAILURE_RESULT);
  }

  /** 비밀번호 재설정 완료를 인증 감사 이벤트로 변환합니다. */
  static AuthEventJpaEntity passwordResetCompleted(Long accountId, Instant occurredAt) {
    AuthEventJpaEntity event =
        new AuthEventJpaEntity(accountId, PASSWORD_RESET_COMPLETED_EVENT_TYPE, SUCCESS_RESULT);
    event.occurredAt = occurredAt;
    return event;
  }
}
