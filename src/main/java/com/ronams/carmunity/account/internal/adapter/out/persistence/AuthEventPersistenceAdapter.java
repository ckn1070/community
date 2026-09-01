package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.application.port.out.AuthEventStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationConfirmFailureReason;
import com.ronams.carmunity.securityaudit.api.IndependentSecurityAuditAppender;
import com.ronams.carmunity.securityaudit.api.SecurityAuditAppender;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 인증 감사 이벤트 저장 port의 JPA adapter입니다. */
@RequiredArgsConstructor
@Component
class AuthEventPersistenceAdapter implements AuthEventStorePort {

  private final AccountJpaRepository accountRepository;
  private final SecurityAuditAppender securityAuditAppender;
  private final IndependentSecurityAuditAppender independentSecurityAuditAppender;
  private final Clock clock;

  /** 이메일 발송은 account/token transaction commit 이후 실행되므로 감사 이벤트도 별도 transaction에서 저장합니다. */
  @Override
  public void recordEmailVerificationRequested(Long accountId, boolean successful) {
    independentSecurityAuditAppender.appendIndependent(
        AccountSecurityAuditRecords.emailVerificationRequested(
            accountPublicId(accountId), successful, clock.instant()));
  }

  /** confirmation transaction 안에서 account 활성화와 함께 이메일 인증 완료 감사를 저장합니다. */
  @Override
  public void recordEmailVerified(Long accountId) {
    securityAuditAppender.append(
        AccountSecurityAuditRecords.emailVerified(accountPublicId(accountId), clock.instant()));
  }

  /** 실패 응답 rollback과 독립적으로 이메일 인증 완료 실패 감사를 남깁니다. */
  @Override
  public void recordEmailVerificationConfirmFailure(
      Long accountId, EmailVerificationConfirmFailureReason reason) {
    independentSecurityAuditAppender.appendIndependent(
        AccountSecurityAuditRecords.emailVerificationConfirmFailure(
            accountPublicId(accountId), reason, clock.instant()));
  }

  /** reset 메일 발송은 token transaction commit 이후 실행되므로 감사 이벤트도 별도 transaction에서 저장합니다. */
  @Override
  public void recordPasswordResetRequested(Long accountId, boolean successful) {
    independentSecurityAuditAppender.appendIndependent(
        AccountSecurityAuditRecords.passwordResetRequested(
            accountPublicId(accountId), successful, clock.instant()));
  }

  private UUID accountPublicId(Long accountId) {
    if (accountId == null) {
      return null;
    }
    return accountRepository.findById(accountId).map(AccountJpaEntity::publicId).orElse(null);
  }
}
