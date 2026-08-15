package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import java.time.Instant;

/**
 * 새 비밀번호 재설정 token 저장 요청입니다.
 *
 * @param accountId 내부 account id입니다.
 * @param recipientNormalized 발송 당시 account email snapshot입니다.
 * @param tokenHash 저장 가능한 SHA-256 hex token hash입니다.
 * @param issuedAt token 발급 시각입니다.
 * @param expiresAt token 만료 시각입니다.
 * @param lastSentAt 마지막 발송 시각입니다.
 */
public record PasswordResetTokenStoreRequest(
    Long accountId,
    String recipientNormalized,
    PasswordResetTokenHash tokenHash,
    Instant issuedAt,
    Instant expiresAt,
    Instant lastSentAt) {}
