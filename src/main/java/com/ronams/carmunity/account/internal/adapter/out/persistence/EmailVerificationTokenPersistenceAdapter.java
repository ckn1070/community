package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.StoredEmailVerificationToken;
import com.ronams.carmunity.account.internal.domain.EmailVerificationTokenHash;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 이메일 인증 token 저장 port의 JPA adapter입니다. */
@RequiredArgsConstructor
@Component
class EmailVerificationTokenPersistenceAdapter implements EmailVerificationTokenStorePort {

  private static final String EMAIL_VERIFICATION_PURPOSE = "EMAIL_VERIFICATION";
  private static final String EMAIL_DELIVERY_CHANNEL = "EMAIL";

  private final AccountVerificationTokenJpaRepository repository;

  /** open token unique constraint와 충돌하지 않도록 호출부가 기존 open token 폐기 후 새 row를 저장합니다. */
  @Override
  @Transactional
  public void save(EmailVerificationTokenStoreRequest token) {
    repository.saveAndFlush(AccountVerificationTokenJpaEntity.from(token));
  }

  /** 같은 account의 EMAIL_VERIFICATION/EMAIL open token만 폐기합니다. */
  @Override
  @Transactional
  public int revokeOpenEmailVerificationTokens(Long accountId, Instant revokedAt) {
    return repository.revokeOpenTokens(
        accountId, EMAIL_VERIFICATION_PURPOSE, EMAIL_DELIVERY_CHANNEL, revokedAt);
  }

  /**
   * purpose/channel까지 포함한 {@code @Lock(PESSIMISTIC_WRITE)} 조회로 소비 대상 row에 JPA pessimistic write
   * lock을 적용합니다.
   */
  @Override
  @Transactional
  public Optional<StoredEmailVerificationToken> findByHash(EmailVerificationTokenHash tokenHash) {
    return repository
        .findByTokenHashAndPurposeAndDeliveryChannel(
            tokenHash.value(), EMAIL_VERIFICATION_PURPOSE, EMAIL_DELIVERY_CHANNEL)
        .map(AccountVerificationTokenJpaEntity::toStoredEmailVerificationToken);
  }

  /**
   * {@code
   * AccountVerificationTokenJpaRepository.findByAccountIdAndPurposeAndDeliveryChannelAndConsumedAtIsNullAndRevokedAtIsNull}의
   * {@code @Lock(PESSIMISTIC_WRITE)} 조회로 cooldown 판단 대상 row에 JPA pessimistic write lock을 적용합니다.
   */
  @Override
  @Transactional
  public Optional<StoredEmailVerificationToken> findOpenEmailVerificationToken(Long accountId) {
    return repository
        .findByAccountIdAndPurposeAndDeliveryChannelAndConsumedAtIsNullAndRevokedAtIsNull(
            accountId, EMAIL_VERIFICATION_PURPOSE, EMAIL_DELIVERY_CHANNEL)
        .map(AccountVerificationTokenJpaEntity::toStoredEmailVerificationToken);
  }

  /** confirmation transaction 안에서 token을 single-use 소비 상태로 전환합니다. */
  @Override
  @Transactional
  public void consume(Long tokenId, Instant consumedAt) {
    repository.consume(tokenId, consumedAt);
  }
}
