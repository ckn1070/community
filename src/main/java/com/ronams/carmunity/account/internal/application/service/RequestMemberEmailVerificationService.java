package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.application.command.RequestMemberEmailVerificationCommand;
import com.ronams.carmunity.account.internal.application.port.in.RequestMemberEmailVerificationUseCase;
import com.ronams.carmunity.account.internal.application.port.out.AuthEventStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationAccount;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationAccountLookupPort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenGeneratorPort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.StoredEmailVerificationToken;
import com.ronams.carmunity.account.internal.domain.AccountStatus;
import com.ronams.carmunity.account.internal.domain.EmailAddress;
import com.ronams.carmunity.account.internal.domain.EmailVerificationTokenHash;
import com.ronams.carmunity.notification.api.EmailVerificationNotificationRequest;
import com.ronams.carmunity.notification.api.EmailVerificationNotificationSender;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 일반 회원 이메일 인증 요청 application service입니다. */
@Service
public class RequestMemberEmailVerificationService
    implements RequestMemberEmailVerificationUseCase {

  private static final Logger log =
      LoggerFactory.getLogger(RequestMemberEmailVerificationService.class);
  private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
  private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);

  private final EmailVerificationAccountLookupPort accountLookupPort;
  private final EmailVerificationTokenGeneratorPort tokenGeneratorPort;
  private final EmailVerificationTokenHasher tokenHasher;
  private final EmailVerificationTokenStorePort tokenStorePort;
  private final EmailVerificationNotificationSender notificationSender;
  private final AuthEventStorePort authEventStorePort;
  private final Clock clock;

  /** 운영 기본 시간 기준으로 Spring bean을 구성합니다. */
  @Autowired
  public RequestMemberEmailVerificationService(
      EmailVerificationAccountLookupPort accountLookupPort,
      EmailVerificationTokenGeneratorPort tokenGeneratorPort,
      EmailVerificationTokenHasher tokenHasher,
      EmailVerificationTokenStorePort tokenStorePort,
      EmailVerificationNotificationSender notificationSender,
      AuthEventStorePort authEventStorePort) {
    this(
        accountLookupPort,
        tokenGeneratorPort,
        tokenHasher,
        tokenStorePort,
        notificationSender,
        authEventStorePort,
        Clock.systemUTC());
  }

  /** 테스트가 token TTL과 cooldown 판단 시각을 통제할 수 있도록 Clock을 외부에서 주입받습니다. */
  RequestMemberEmailVerificationService(
      EmailVerificationAccountLookupPort accountLookupPort,
      EmailVerificationTokenGeneratorPort tokenGeneratorPort,
      EmailVerificationTokenHasher tokenHasher,
      EmailVerificationTokenStorePort tokenStorePort,
      EmailVerificationNotificationSender notificationSender,
      AuthEventStorePort authEventStorePort,
      Clock clock) {
    this.accountLookupPort = accountLookupPort;
    this.tokenGeneratorPort = tokenGeneratorPort;
    this.tokenHasher = tokenHasher;
    this.tokenStorePort = tokenStorePort;
    this.notificationSender = notificationSender;
    this.authEventStorePort = authEventStorePort;
    this.clock = clock;
  }

  /**
   * 이메일 인증 token을 준비하고 transaction commit 이후 발송을 시도합니다.
   *
   * <p>account 존재 여부, 이미 인증된 상태, cooldown 상태, provider 실패 세부사항은 public 응답에 노출하지 않습니다.
   */
  @Override
  @Transactional
  public void execute(RequestMemberEmailVerificationCommand command) {
    EmailAddress email = normalize(command.email());
    Optional<EmailVerificationAccount> account =
        accountLookupPort.findByNormalizedEmail(email.normalized());

    if (account.isEmpty() || account.orElseThrow().status() != AccountStatus.PENDING) {
      return;
    }

    Instant now = clock.instant();
    EmailVerificationAccount pendingAccount = account.orElseThrow();
    Optional<StoredEmailVerificationToken> openToken =
        tokenStorePort.findOpenEmailVerificationToken(pendingAccount.accountId());
    if (openToken.filter(token -> isInCooldown(token, now)).isPresent()) {
      return;
    }

    String rawToken = tokenGeneratorPort.generate();
    EmailVerificationTokenHash tokenHash = tokenHasher.hash(rawToken);
    Instant expiresAt = now.plus(TOKEN_TTL);

    openToken.ifPresent(
        ignored ->
            tokenStorePort.revokeOpenEmailVerificationTokens(pendingAccount.accountId(), now));
    tokenStorePort.save(
        new EmailVerificationTokenStoreRequest(
            pendingAccount.accountId(),
            pendingAccount.recipientNormalized(),
            tokenHash,
            now,
            expiresAt,
            now));

    runAfterCommit(
        () ->
            sendEmailAndRecordEvent(
                pendingAccount.accountId(),
                pendingAccount.recipientNormalized(),
                rawToken,
                expiresAt));
  }

  private EmailAddress normalize(String email) {
    try {
      return EmailAddress.from(email);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST, exception);
    }
  }

  private boolean isInCooldown(StoredEmailVerificationToken token, Instant now) {
    return token.lastSentAt().plus(REQUEST_COOLDOWN).isAfter(now);
  }

  private void runAfterCommit(Runnable task) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      task.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            task.run();
          }
        });
  }

  private void sendEmailAndRecordEvent(
      Long accountId, String recipientNormalized, String rawToken, Instant expiresAt) {
    try {
      notificationSender.send(
          new EmailVerificationNotificationRequest(recipientNormalized, rawToken, expiresAt));
      authEventStorePort.recordEmailVerificationRequested(accountId, true);
    } catch (RuntimeException exception) {
      log.warn("이메일 인증 발송에 실패했습니다. accountId={}", accountId, exception);
      authEventStorePort.recordEmailVerificationRequested(accountId, false);
    }
  }
}
