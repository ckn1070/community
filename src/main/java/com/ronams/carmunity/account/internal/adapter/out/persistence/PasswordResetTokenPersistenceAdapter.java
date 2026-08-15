package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.StoredPasswordResetToken;
import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 재설정 token 저장 port의 JPA adapter입니다. */
@RequiredArgsConstructor
@Component
class PasswordResetTokenPersistenceAdapter implements PasswordResetTokenStorePort {

  private static final String PASSWORD_RESET_PURPOSE = "PASSWORD_RESET";
  private static final String EMAIL_DELIVERY_CHANNEL = "EMAIL";

  private final AccountVerificationTokenJpaRepository repository;

  /** open token unique constraint와 충돌하지 않도록 호출부가 기존 open token 폐기 후 새 row를 저장합니다. */
  @Override
  @Transactional
  public void save(PasswordResetTokenStoreRequest token) {
    repository.saveAndFlush(AccountVerificationTokenJpaEntity.from(token));
  }

  /** 같은 account의 PASSWORD_RESET/EMAIL open token만 폐기합니다. */
  @Override
  @Transactional
  public int revokeOpenPasswordResetTokens(Long accountId, Instant revokedAt) {
    return repository.revokeOpenTokens(
        accountId, PASSWORD_RESET_PURPOSE, EMAIL_DELIVERY_CHANNEL, revokedAt);
  }

  /**
   * purpose/channel까지 포함한 {@code @Lock(PESSIMISTIC_WRITE)} 조회로 소비 대상 row에 JPA pessimistic write
   * lock을 적용합니다.
   */
  @Override
  @Transactional
  public Optional<StoredPasswordResetToken> findByHash(PasswordResetTokenHash tokenHash) {
    return repository
        .findByTokenHashAndPurposeAndDeliveryChannel(
            tokenHash.value(), PASSWORD_RESET_PURPOSE, EMAIL_DELIVERY_CHANNEL)
        .map(AccountVerificationTokenJpaEntity::toStoredPasswordResetToken);
  }

  /** open reset token 조회 row에 JPA pessimistic write lock을 적용해 cooldown 판단과 재발급 폐기를 직렬화합니다. */
  @Override
  @Transactional
  public Optional<StoredPasswordResetToken> findOpenPasswordResetToken(Long accountId) {
    return repository
        .findByAccountIdAndPurposeAndDeliveryChannelAndConsumedAtIsNullAndRevokedAtIsNull(
            accountId, PASSWORD_RESET_PURPOSE, EMAIL_DELIVERY_CHANNEL)
        .map(AccountVerificationTokenJpaEntity::toStoredPasswordResetToken);
  }

  /** reset confirmation transaction 안에서 token을 single-use 소비 상태로 전환합니다. */
  @Override
  @Transactional
  public void consume(Long tokenId, Instant consumedAt) {
    repository.consume(tokenId, consumedAt);
  }
}
