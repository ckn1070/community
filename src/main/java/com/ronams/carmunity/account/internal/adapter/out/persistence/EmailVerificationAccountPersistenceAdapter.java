package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationAccount;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationAccountActivationPort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationAccountLookupPort;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 이메일 인증 use case가 필요한 account 조회와 활성화 Port의 JPA adapter입니다. */
@RequiredArgsConstructor
@Component
class EmailVerificationAccountPersistenceAdapter
    implements EmailVerificationAccountLookupPort, EmailVerificationAccountActivationPort {

  private final AccountJpaRepository accountRepository;

  /**
   * 이메일 인증 요청 판단에 필요한 최소 account 상태만 조회합니다.
   *
   * <p>로그인 조회 port와 같은 normalized email 입력을 쓰지만 반환 모델이 달라, 별도 adapter로 분리해 port 계약 충돌을 피합니다.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<EmailVerificationAccount> findByNormalizedEmail(String normalizedEmail) {
    return accountRepository
        .findByEmailNormalized(normalizedEmail)
        .map(
            account ->
                new EmailVerificationAccount(
                    account.id(), account.emailNormalized(), account.status()));
  }

  /**
   * 아직 PENDING 상태인 account만 ACTIVE로 전환합니다.
   *
   * <p>동시 confirmation 요청은 update row count로 구분하고, 이미 활성화된 account는 use case가 실패로 처리합니다.
   */
  @Override
  @Transactional
  public boolean activatePendingAccount(Long accountId, Instant verifiedAt) {
    return accountRepository.activatePendingAccount(accountId, verifiedAt) == 1;
  }
}
