package com.ronams.carmunity.auth.internal.adapter.out.persistence;

import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRejectionReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * auth_event 테이블에 refresh token lifecycle 감사 이벤트를 저장하는 JPA 모델입니다.
 *
 * <p>account 모듈의 가입 이벤트 mapper와 같은 테이블을 쓰지만, auth 모듈은 token lifecycle 이벤트만 생성합니다.
 */
@Entity
@Table(name = "auth_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AuthTokenEventJpaEntity {

  private static final String TOKEN_REFRESH_EVENT_TYPE = "TOKEN_REFRESH";
  private static final String LOGOUT_EVENT_TYPE = "LOGOUT";
  private static final String TOKEN_REUSE_DETECTED_REASON = "TOKEN_REUSE_DETECTED";
  private static final String SUCCESS_RESULT = "SUCCESS";
  private static final String FAILURE_RESULT = "FAILURE";

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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String metadata = "{}";

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  private AuthTokenEventJpaEntity(
      Long accountId, String eventType, String result, String reasonCode, Instant occurredAt) {
    this.accountId = accountId;
    this.eventType = eventType;
    this.result = result;
    this.reasonCode = reasonCode;
    this.occurredAt = occurredAt;
  }

  static AuthTokenEventJpaEntity tokenRefreshSuccess(Long accountId, Instant occurredAt) {
    return new AuthTokenEventJpaEntity(
        accountId, TOKEN_REFRESH_EVENT_TYPE, SUCCESS_RESULT, null, occurredAt);
  }

  static AuthTokenEventJpaEntity tokenRefreshFailureForRejectedAccount(
      Long accountId, RefreshTokenRejectionReason reason, Instant occurredAt) {
    return new AuthTokenEventJpaEntity(
        accountId, TOKEN_REFRESH_EVENT_TYPE, FAILURE_RESULT, reason.name(), occurredAt);
  }

  static AuthTokenEventJpaEntity logoutSuccess(Long accountId, Instant occurredAt) {
    return new AuthTokenEventJpaEntity(
        accountId, LOGOUT_EVENT_TYPE, SUCCESS_RESULT, null, occurredAt);
  }

  static AuthTokenEventJpaEntity tokenReuseDetected(Long accountId, Instant occurredAt) {
    return new AuthTokenEventJpaEntity(
        accountId,
        TOKEN_REFRESH_EVENT_TYPE,
        FAILURE_RESULT,
        TOKEN_REUSE_DETECTED_REASON,
        occurredAt);
  }
}
