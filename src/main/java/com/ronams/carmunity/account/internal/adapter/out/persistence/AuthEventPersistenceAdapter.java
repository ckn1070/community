package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.application.port.out.AuthEventStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationConfirmFailureReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 인증 감사 이벤트 저장 port의 JPA adapter입니다. */
@RequiredArgsConstructor
@Component
class AuthEventPersistenceAdapter implements AuthEventStorePort {

  private final AuthEventJpaRepository repository;

  /** 이메일 발송은 account/token transaction commit 이후 실행되므로 감사 이벤트도 별도 transaction에서 저장합니다. */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordEmailVerificationRequested(Long accountId, boolean successful) {
    repository.save(AuthEventJpaEntity.emailVerificationRequested(accountId, successful));
  }

  /** confirmation transaction 안에서 account 활성화와 함께 이메일 인증 완료 감사를 저장합니다. */
  @Override
  @Transactional
  public void recordEmailVerified(Long accountId) {
    repository.save(AuthEventJpaEntity.emailVerified(accountId));
  }

  /** 실패 응답 rollback과 독립적으로 이메일 인증 완료 실패 감사를 남깁니다. */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordEmailVerificationConfirmFailure(
      Long accountId, EmailVerificationConfirmFailureReason reason) {
    repository.save(AuthEventJpaEntity.emailVerificationConfirmFailure(accountId, reason));
  }

  /** reset 메일 발송은 token transaction commit 이후 실행되므로 감사 이벤트도 별도 transaction에서 저장합니다. */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordPasswordResetRequested(Long accountId, boolean successful) {
    repository.save(AuthEventJpaEntity.passwordResetRequested(accountId, successful));
  }
}
