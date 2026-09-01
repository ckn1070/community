package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.application.command.RequestPasswordResetCommand;
import com.ronams.carmunity.account.internal.application.port.in.RequestPasswordResetUseCase;
import com.ronams.carmunity.account.internal.application.port.out.AuthEventStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetAccount;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetAccountLookupPort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenGeneratorPort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.StoredPasswordResetToken;
import com.ronams.carmunity.account.internal.domain.AccountStatus;
import com.ronams.carmunity.account.internal.domain.EmailAddress;
import com.ronams.carmunity.account.internal.domain.PasswordResetChallenge;
import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import com.ronams.carmunity.notification.api.PasswordResetNotificationRequest;
import com.ronams.carmunity.notification.api.PasswordResetNotificationSender;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 비로그인 account 비밀번호 재설정 요청 application service입니다. */
@Service
public class RequestPasswordResetService implements RequestPasswordResetUseCase {

  private static final Logger log = LoggerFactory.getLogger(RequestPasswordResetService.class);
  private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
  private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);

  private final PasswordResetAccountLookupPort accountLookupPort;
  private final PasswordResetTokenGeneratorPort tokenGeneratorPort;
  private final PasswordResetTokenHasher tokenHasher;
  private final PasswordResetTokenStorePort tokenStorePort;
  private final PasswordResetNotificationSender notificationSender;
  private final AuthEventStorePort authEventStorePort;
  private final Clock clock;

  /** 테스트가 token TTL과 cooldown 판단 시각을 통제할 수 있도록 Clock을 외부에서 주입받습니다. */
  public RequestPasswordResetService(
      PasswordResetAccountLookupPort accountLookupPort,
      PasswordResetTokenGeneratorPort tokenGeneratorPort,
      PasswordResetTokenHasher tokenHasher,
      PasswordResetTokenStorePort tokenStorePort,
      PasswordResetNotificationSender notificationSender,
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
   * 비밀번호 재설정 token을 준비하고 transaction commit 이후 발송을 시도합니다.
   *
   * <p>account 존재 여부, 상태, cooldown, provider 실패 세부사항은 public 응답에 노출하지 않습니다.
   */
  @Override
  @Transactional
  public void execute(RequestPasswordResetCommand command) {
    EmailAddress email = normalize(command.email());
    Optional<PasswordResetAccount> account =
        accountLookupPort.findByNormalizedEmail(email.normalized());

    if (account.isEmpty() || !eligible(account.orElseThrow())) {
      return;
    }

    Instant now = clock.instant();
    PasswordResetAccount resetAccount = account.orElseThrow();
    Optional<StoredPasswordResetToken> openToken =
        tokenStorePort.findOpenPasswordResetToken(resetAccount.accountId());
    if (openToken.filter(token -> isInCooldown(token, now)).isPresent()) {
      return;
    }

    String rawToken = tokenGeneratorPort.generate();
    PasswordResetTokenHash tokenHash = tokenHasher.hash(rawToken);
    Instant expiresAt = now.plus(TOKEN_TTL);

    openToken.ifPresent(
        ignored -> tokenStorePort.revokeOpenPasswordResetTokens(resetAccount.accountId(), now));
    tokenStorePort.save(
        new PasswordResetTokenStoreRequest(
            resetAccount.accountId(),
            resetAccount.recipientNormalized(),
            tokenHash,
            now,
            expiresAt,
            now));

    runAfterCommit(() -> sendEmailAndRecordEvent(resetAccount, rawToken, expiresAt));
  }

  private EmailAddress normalize(String email) {
    try {
      return EmailAddress.from(email);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST, exception);
    }
  }

  private boolean eligible(PasswordResetAccount account) {
    return account.status() == AccountStatus.ACTIVE || account.status() == AccountStatus.PENDING;
  }

  private boolean isInCooldown(StoredPasswordResetToken token, Instant now) {
    return new PasswordResetChallenge(
            token.id(),
            token.accountId(),
            token.expiresAt(),
            token.lastSentAt(),
            token.consumedAt().orElse(null),
            token.revokedAt().orElse(null))
        .isInCooldownAt(now, REQUEST_COOLDOWN);
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
      PasswordResetAccount account, String rawToken, Instant expiresAt) {
    try {
      notificationSender.send(
          new PasswordResetNotificationRequest(account.recipientNormalized(), rawToken, expiresAt));
      authEventStorePort.recordPasswordResetRequested(account.accountId(), true);
    } catch (RuntimeException exception) {
      log.warn(
          "비밀번호 재설정 메일 발송에 실패했습니다. accountPublicId={}, failureClass={}",
          account.accountPublicId(),
          exception.getClass().getSimpleName());
      authEventStorePort.recordPasswordResetRequested(account.accountId(), false);
    }
  }
}
