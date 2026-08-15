package com.ronams.carmunity.account.internal.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.StoredPasswordResetToken;
import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 비밀번호 재설정 token persistence adapter가 purpose별 open token 저장/조회 계약을 지키는지 검증합니다. */
@SpringBootTest
class PasswordResetTokenPersistenceAdapterTest extends PostgreSqlIntegrationTestSupport {

  @Autowired private PasswordResetTokenStorePort storePort;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** reset token 재발급 전 기존 open reset token만 폐기하고 새 reset token을 저장합니다. */
  @Test
  void revokesOpenPasswordResetTokenBeforeSavingNewToken() {
    Long accountId = createAccount("reset-token-reissue@example.com");
    Instant now = Instant.now();

    storePort.save(tokenToSave(accountId, "4".repeat(64), now));

    Instant revokedAt = now.plusSeconds(60);
    int revoked = storePort.revokeOpenPasswordResetTokens(accountId, revokedAt);
    storePort.save(tokenToSave(accountId, "5".repeat(64), revokedAt));

    assertThat(revoked).isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from account_verification_token
                where account_id = ?
                  and purpose = 'PASSWORD_RESET'
                  and revoked_at is not null
                """,
                Integer.class,
                accountId))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from account_verification_token
                where account_id = ?
                  and purpose = 'PASSWORD_RESET'
                  and consumed_at is null
                  and revoked_at is null
                """,
                Integer.class,
                accountId))
        .isOne();
  }

  /** password reset adapter는 PASSWORD_RESET purpose row만 hash로 조회해야 합니다. */
  @Test
  void findsPasswordResetTokenByHashAndIgnoresEmailVerificationToken() {
    Long accountId = createAccount("reset-token-lookup@example.com");
    Instant issuedAt = Instant.parse("2026-07-06T01:00:00Z");
    String resetTokenHash = "6".repeat(64);
    String emailVerificationTokenHash = "7".repeat(64);
    storePort.save(tokenToSave(accountId, resetTokenHash, issuedAt));
    createEmailVerificationToken(accountId, emailVerificationTokenHash);

    Optional<StoredPasswordResetToken> found =
        storePort.findByHash(PasswordResetTokenHash.from(resetTokenHash));
    Optional<StoredPasswordResetToken> wrongPurpose =
        storePort.findByHash(PasswordResetTokenHash.from(emailVerificationTokenHash));

    assertThat(found).isPresent();
    assertThat(found.orElseThrow().accountId()).isEqualTo(accountId);
    assertThat(found.orElseThrow().tokenHash().value()).isEqualTo(resetTokenHash);
    assertThat(found.orElseThrow().expiresAt()).isEqualTo(issuedAt.plusSeconds(1800));
    assertThat(wrongPurpose).isEmpty();
  }

  private PasswordResetTokenStoreRequest tokenToSave(
      Long accountId, String tokenHash, Instant issuedAt) {
    return new PasswordResetTokenStoreRequest(
        accountId,
        "reset-token-recipient@example.com",
        PasswordResetTokenHash.from(tokenHash),
        issuedAt,
        issuedAt.plusSeconds(1800),
        issuedAt);
  }

  private Long createAccount(String email) {
    return jdbcTemplate.queryForObject(
        """
        insert into account (public_id, email, email_normalized, status)
        values (?, ?, ?, 'ACTIVE')
        returning id
        """,
        Long.class,
        UUID.randomUUID(),
        email,
        email);
  }

  private void createEmailVerificationToken(Long accountId, String tokenHash) {
    jdbcTemplate.update(
        """
        insert into account_verification_token (
            account_id,
            purpose,
            delivery_channel,
            recipient_normalized,
            token_hash,
            expires_at,
            last_sent_at
        )
        values (?, 'EMAIL_VERIFICATION', 'EMAIL', ?, ?, now() + interval '1 day', now())
        """,
        accountId,
        "reset-token-recipient@example.com",
        tokenHash);
  }
}
