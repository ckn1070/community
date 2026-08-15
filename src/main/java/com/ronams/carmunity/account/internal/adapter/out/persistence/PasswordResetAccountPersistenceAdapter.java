package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.application.port.out.PasswordResetAccount;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetAccountLookupPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 재설정 요청 email로 account reset 발급 판단 snapshot을 조회하는 JPA adapter입니다. */
@RequiredArgsConstructor
@Component
class PasswordResetAccountPersistenceAdapter implements PasswordResetAccountLookupPort {

  private final AccountJpaRepository accountRepository;

  /** normalized email과 일치하는 account의 내부 id, public id, 상태, 발송 email snapshot만 반환합니다. */
  @Override
  @Transactional(readOnly = true)
  public Optional<PasswordResetAccount> findByNormalizedEmail(String normalizedEmail) {
    return accountRepository
        .findByEmailNormalized(normalizedEmail)
        .map(
            account ->
                new PasswordResetAccount(
                    account.id(), account.publicId(), account.emailNormalized(), account.status()));
  }
}
