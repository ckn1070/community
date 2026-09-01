package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ConfirmMemberEmailVerificationCommand;
import com.ronams.carmunity.account.internal.application.port.in.ConfirmMemberEmailVerificationUseCase;
import com.ronams.carmunity.account.internal.application.port.out.AuthEventStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationAccountActivationPort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationConfirmFailureReason;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.StoredEmailVerificationToken;
import com.ronams.carmunity.account.internal.domain.ChallengeState;
import com.ronams.carmunity.account.internal.domain.EmailVerificationChallenge;
import com.ronams.carmunity.account.internal.domain.EmailVerificationTokenHash;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import com.ronams.carmunity.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
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

  /** 테스트가 token 만료 판단 시각을 통제할 수 있도록 Clock을 외부에서 주입받습니다. */
  public ConfirmMemberEmailVerificationService(
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
    EmailVerificationChallenge challenge = validateToken(token, now);

    boolean activated = accountActivationPort.activatePendingAccount(token.accountId(), now);
    if (!activated) {
      throw emailVerificationConfirmFailure(
          token.accountId(),
          EmailVerificationConfirmFailureReason.ACCOUNT_NOT_PENDING,
          AccountErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
    }

    challenge.consumeAt(now);
    tokenStorePort.consume(challenge.id(), now);
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

  private EmailVerificationChallenge validateToken(
      StoredEmailVerificationToken token, Instant now) {
    EmailVerificationChallenge challenge =
        new EmailVerificationChallenge(
            token.id(),
            token.accountId(),
            token.expiresAt(),
            token.lastSentAt(),
            token.consumedAt().orElse(null),
            token.revokedAt().orElse(null));
    ChallengeState state = challenge.stateAt(now);
    switch (state) {
      case OPEN -> {
        return challenge;
      }
      case CONSUMED ->
          throw emailVerificationConfirmFailure(
              token.accountId(),
              EmailVerificationConfirmFailureReason.TOKEN_ALREADY_USED,
              AccountErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
      case REVOKED ->
          throw emailVerificationConfirmFailure(
              token.accountId(),
              EmailVerificationConfirmFailureReason.TOKEN_REVOKED,
              AccountErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
      case EXPIRED ->
          throw emailVerificationConfirmFailure(
              token.accountId(),
              EmailVerificationConfirmFailureReason.TOKEN_EXPIRED,
              AccountErrorCode.EMAIL_VERIFICATION_TOKEN_EXPIRED);
    }
    throw new IllegalStateException("email verification state is not handled");
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
