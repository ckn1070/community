package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import java.time.Instant;
import java.util.Optional;

/** 비밀번호 재설정 token 저장, 조회, 소비 상태 변경 persistence port입니다. */
public interface PasswordResetTokenStorePort {

  void save(PasswordResetTokenStoreRequest token);

  int revokeOpenPasswordResetTokens(Long accountId, Instant revokedAt);

  Optional<StoredPasswordResetToken> findByHash(PasswordResetTokenHash tokenHash);

  Optional<StoredPasswordResetToken> findOpenPasswordResetToken(Long accountId);

  void consume(Long tokenId, Instant consumedAt);
}
