package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.EmailVerificationTokenHash;
import java.time.Instant;
import java.util.Optional;

/** 이메일 인증 token의 저장, 폐기, 조회를 담당하는 persistence port입니다. */
public interface EmailVerificationTokenStorePort {

  /**
   * 새 이메일 인증 token row를 저장합니다.
   *
   * @param token 저장할 token 입력 모델입니다.
   */
  void save(EmailVerificationTokenStoreRequest token);

  /**
   * account의 기존 open 이메일 인증 token을 폐기합니다.
   *
   * @param accountId 내부 account id입니다.
   * @param revokedAt 폐기 시각입니다.
   * @return 폐기된 row 수입니다.
   */
  int revokeOpenEmailVerificationTokens(Long accountId, Instant revokedAt);

  /**
   * confirmation용 token hash로 token row를 조회합니다.
   *
   * @param tokenHash 사용자가 제출한 raw token에서 계산한 hash입니다.
   * @return 저장된 token row입니다.
   */
  Optional<StoredEmailVerificationToken> findByHash(EmailVerificationTokenHash tokenHash);

  /**
   * account의 open 이메일 인증 token을 조회합니다.
   *
   * @param accountId 내부 account id입니다.
   * @return 아직 소비되거나 폐기되지 않은 이메일 인증 token입니다.
   */
  Optional<StoredEmailVerificationToken> findOpenEmailVerificationToken(Long accountId);

  /**
   * token을 소비 상태로 전환합니다.
   *
   * @param tokenId 내부 token id입니다.
   * @param consumedAt 소비 시각입니다.
   */
  void consume(Long tokenId, Instant consumedAt);
}
