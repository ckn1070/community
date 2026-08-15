package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import java.time.Instant;
import java.util.Optional;

/**
 * 저장소에서 조회한 비밀번호 재설정 token 상태입니다.
 *
 * @param id token row id입니다.
 * @param accountId token 대상 내부 account id입니다.
 * @param recipientNormalized 발급 당시 email snapshot입니다.
 * @param tokenHash 저장된 SHA-256 hex token hash입니다.
 * @param issuedAt token 발급 시각입니다.
 * @param expiresAt token 만료 시각입니다.
 * @param lastSentAt 마지막 발송 시각입니다.
 * @param consumedAt 소비 시각입니다.
 * @param revokedAt 폐기 시각입니다.
 */
public record StoredPasswordResetToken(
    Long id,
    Long accountId,
    String recipientNormalized,
    PasswordResetTokenHash tokenHash,
    Instant issuedAt,
    Instant expiresAt,
    Instant lastSentAt,
    Optional<Instant> consumedAt,
    Optional<Instant> revokedAt) {}
