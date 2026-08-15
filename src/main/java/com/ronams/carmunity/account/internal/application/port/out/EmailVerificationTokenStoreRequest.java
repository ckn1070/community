package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.EmailVerificationTokenHash;
import java.time.Instant;
import java.util.Objects;

/**
 * 이메일 인증 token 저장 port 입력 모델입니다.
 *
 * @param accountId token을 소유하는 내부 account id입니다.
 * @param recipientNormalized 발급 시점의 수신자 email snapshot입니다.
 * @param tokenHash 저장할 token hash입니다.
 * @param issuedAt token 발급 시각입니다.
 * @param expiresAt token 만료 시각입니다.
 * @param lastSentAt 발송 성공 여부와 무관하게 cooldown 판단에 사용할 마지막 발송 시도 기준 시각입니다.
 */
public record EmailVerificationTokenStoreRequest(
    Long accountId,
    String recipientNormalized,
    EmailVerificationTokenHash tokenHash,
    Instant issuedAt,
    Instant expiresAt,
    Instant lastSentAt) {

  public EmailVerificationTokenStoreRequest {
    Objects.requireNonNull(accountId, "accountId must not be null");
    Objects.requireNonNull(recipientNormalized, "recipientNormalized must not be null");
    Objects.requireNonNull(tokenHash, "tokenHash must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    Objects.requireNonNull(lastSentAt, "lastSentAt must not be null");
    if (expiresAt.compareTo(issuedAt) <= 0) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
    if (lastSentAt.compareTo(issuedAt) < 0) {
      throw new IllegalArgumentException("lastSentAt must not be before issuedAt");
    }
  }
}
