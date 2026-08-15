package com.ronams.carmunity.account.internal.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.StoredEmailVerificationToken;
import com.ronams.carmunity.account.internal.domain.EmailVerificationTokenHash;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 이메일 인증 token persistence adapter가 open token 저장/폐기/조회 계약을 DB constraint와 함께 지키는지 검증합니다. */
@SpringBootTest
class EmailVerificationTokenPersistenceAdapterTest extends PostgreSqlIntegrationTestSupport {

  @Autowired private EmailVerificationTokenStorePort storePort;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** 재발급 전 기존 open token을 폐기해야 partial unique index와 single-open-token 정책이 함께 유지됩니다. */
  @Test
  void revokesOpenEmailVerificationTokenBeforeSavingNewToken() {
    Long accountId = createAccount("token-reissue@example.com");
    Instant now = Instant.now();

    storePort.save(tokenToSave(accountId, "4".repeat(64), now));

    Instant revokedAt = Instant.now().plusSeconds(60);
    int revoked = storePort.revokeOpenEmailVerificationTokens(accountId, revokedAt);
    storePort.save(tokenToSave(accountId, "5".repeat(64), revokedAt));

    assertThat(revoked).isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
				select count(*)
				from account_verification_token
				where account_id = ? and revoked_at is not null
				""",
                Integer.class,
                accountId))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
				select count(*)
				from account_verification_token
				where account_id = ? and consumed_at is null and revoked_at is null
				""",
                Integer.class,
                accountId))
        .isOne();
  }

  /** 이 adapter는 hash 계산을 하지 않고, 이미 계산된 token hash로 저장한 row를 같은 hash로 조회합니다. */
  @Test
  void findsEmailVerificationTokenByHash() {
    Long accountId = createAccount("token-lookup@example.com");
    Instant issuedAt = Instant.parse("2026-06-29T01:00:00Z");
    String tokenHash = "6".repeat(64);
    storePort.save(tokenToSave(accountId, tokenHash, issuedAt));

    Optional<StoredEmailVerificationToken> found =
        storePort.findByHash(EmailVerificationTokenHash.from(tokenHash));

    assertThat(found).isPresent();
    assertThat(found.orElseThrow().accountId()).isEqualTo(accountId);
    assertThat(found.orElseThrow().tokenHash().value()).isEqualTo(tokenHash);
    assertThat(found.orElseThrow().expiresAt()).isEqualTo(issuedAt.plusSeconds(1800));
    assertThat(found.orElseThrow().consumedAt()).isEmpty();
    assertThat(found.orElseThrow().revokedAt()).isEmpty();
  }

  private EmailVerificationTokenStoreRequest tokenToSave(
      Long accountId, String tokenHash, Instant issuedAt) {
    return new EmailVerificationTokenStoreRequest(
        accountId,
        "token-recipient@example.com",
        EmailVerificationTokenHash.from(tokenHash),
        issuedAt,
        issuedAt.plusSeconds(1800),
        issuedAt);
  }

  private Long createAccount(String email) {
    return jdbcTemplate.queryForObject(
        """
				insert into account (public_id, email, email_normalized, status)
				values (?, ?, ?, 'PENDING')
				returning id
				""",
        Long.class,
        UUID.randomUUID(),
        email,
        email);
  }
}
