package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ConfirmMemberEmailVerificationCommand;
import com.ronams.carmunity.account.internal.application.port.in.ConfirmMemberEmailVerificationUseCase;
import com.ronams.carmunity.account.internal.application.port.out.AuthEventStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationAccountActivationPort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationConfirmFailureReason;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.StoredEmailVerificationToken;
import com.ronams.carmunity.account.internal.domain.EmailVerificationTokenHash;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import com.ronams.carmunity.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일반 회원 이메일 인증 완료 application service입니다. */
@Service
public class ConfirmMemberEmailVerificationService
    implements ConfirmMemberEmailVerificationUseCase {

  private final EmailVerificationTokenHasher tokenHasher;
  private final EmailVerificationTokenStorePort tokenStorePort;
  private final EmailVerificationAccountActivationPort accountActivationPort;
  private final AuthEventStorePort authEventStorePort;
  private final Clock clock;

  /** 운영 기본 시간 기준으로 Spring bean을 구성합니다. */
  @Autowired
  public ConfirmMemberEmailVerificationService(
      EmailVerificationTokenHasher tokenHasher,
      EmailVerificationTokenStorePort tokenStorePort,
      EmailVerificationAccountActivationPort accountActivationPort,
      AuthEventStorePort authEventStorePort) {
    this(tokenHasher, tokenStorePort, accountActivationPort, authEventStorePort, Clock.systemUTC());
  }

  /** 테스트가 token 만료 판단 시각을 통제할 수 있도록 Clock을 외부에서 주입받습니다. */
  ConfirmMemberEmailVerificationService(
      EmailVerificationTokenHasher tokenHasher,
      EmailVerificationTokenStorePort tokenStorePort,
      EmailVerificationAccountActivationPort accountActivationPort,
      AuthEventStorePort authEventStorePort,
      Clock clock) {
    this.tokenHasher = tokenHasher;
    this.tokenStorePort = tokenStorePort;
    this.accountActivationPort = accountActivationPort;
    this.authEventStorePort = authEventStorePort;
    this.clock = clock;
  }

  /**
   * account ACTIVE 전환, token 소비, EMAIL_VERIFICATION_CONFIRM 감사 이벤트를 하나의 transaction에서 처리합니다.
   *
   * <p>raw token은 즉시 hash로 바꾸고, 이미 소비/폐기/만료된 token은 account 상태를 바꾸지 않습니다.
   */
  @Override
  @Transactional
  public void execute(ConfirmMemberEmailVerificationCommand command) {
    EmailVerificationTokenHash tokenHash = hash(command.token());
    StoredEmailVerificationToken token =
        tokenStorePort
            .findByHash(tokenHash)
            .orElseThrow(
                () ->
                    emailVerificationConfirmFailure(
                        null,
                        EmailVerificationConfirmFailureReason.TOKEN_INVALID,
                        AccountErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID));

    Instant now = clock.instant();
    validateToken(token, now);

    boolean activated = accountActivationPort.activatePendingAccount(token.accountId(), now);
    if (!activated) {
      throw emailVerificationConfirmFailure(
          token.accountId(),
          EmailVerificationConfirmFailureReason.ACCOUNT_NOT_PENDING,
          AccountErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
    }

    tokenStorePort.consume(token.id(), now);
    authEventStorePort.recordEmailVerified(token.accountId());
  }

  private EmailVerificationTokenHash hash(String rawToken) {
    try {
      return tokenHasher.hash(rawToken);
    } catch (IllegalArgumentException exception) {
      throw emailVerificationConfirmFailure(
          null,
          EmailVerificationConfirmFailureReason.TOKEN_INVALID,
          CommonErrorCode.INVALID_REQUEST,
          exception);
    }
  }

  private void validateToken(StoredEmailVerificationToken token, Instant now) {
    if (token.consumedAt().isPresent()) {
      throw emailVerificationConfirmFailure(
          token.accountId(),
          EmailVerificationConfirmFailureReason.TOKEN_ALREADY_USED,
          AccountErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
    }
    if (token.revokedAt().isPresent()) {
      throw emailVerificationConfirmFailure(
          token.accountId(),
          EmailVerificationConfirmFailureReason.TOKEN_REVOKED,
          AccountErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
    }
    if (!token.expiresAt().isAfter(now)) {
      throw emailVerificationConfirmFailure(
          token.accountId(),
          EmailVerificationConfirmFailureReason.TOKEN_EXPIRED,
          AccountErrorCode.EMAIL_VERIFICATION_TOKEN_EXPIRED);
    }
  }

  private ApiException emailVerificationConfirmFailure(
      Long accountId, EmailVerificationConfirmFailureReason reason, ErrorCode errorCode) {
    authEventStorePort.recordEmailVerificationConfirmFailure(accountId, reason);
    return new ApiException(errorCode);
  }

  private ApiException emailVerificationConfirmFailure(
      Long accountId,
      EmailVerificationConfirmFailureReason reason,
      ErrorCode errorCode,
      Throwable cause) {
    authEventStorePort.recordEmailVerificationConfirmFailure(accountId, reason);
    return new ApiException(errorCode, cause);
  }
}
