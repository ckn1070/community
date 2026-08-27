package com.ronams.carmunity.account.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.account.api.AccountAuthStatus;
import com.ronams.carmunity.account.api.PasswordLoginAccountLookup;
import com.ronams.carmunity.account.api.PasswordLoginFailureReason;
import com.ronams.carmunity.account.api.PasswordLoginResultRecorder;
import com.ronams.carmunity.account.api.PasswordLoginSuccess;
import com.ronams.carmunity.account.api.StoredRefreshToken;
import com.ronams.carmunity.account.internal.application.command.RegisterMemberAccountCommand;
import com.ronams.carmunity.account.internal.application.port.in.RegisterMemberAccountUseCase;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 비밀번호 로그인용 account 공개 계약이 내부 DB id를 숨기고 account 관련 row를 원자적으로 갱신하는지 검증합니다.
 *
 * <p>실제 PostgreSQL schema의 FK, check constraint, timestamp 컬럼에 의존하므로 slice가 아니라 통합 테스트로 고정합니다.
 */
@SpringBootTest
class LoginAccountPersistenceIntegrationTest extends PostgreSqlIntegrationTestSupport {

  private static final String PASSWORD = "correct horse battery staple";
  private static final String REFRESH_TOKEN_HASH =
      "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
  private static final UUID FAMILY_ID = UUID.fromString("b43236f5-92d7-495e-aebc-2a3a66d7b21d");
  private static final Instant NOW = Instant.parse("2026-06-22T00:00:00Z");

  @Autowired private RegisterMemberAccountUseCase registerMemberAccountUseCase;
  @Autowired private PasswordLoginAccountLookup accountLookup;
  @Autowired private PasswordLoginResultRecorder resultRecorder;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** normalized email 조회는 password hash, public id, account 상태, 활성 role만 공개 계약으로 반환합니다. */
  @Test
  void findsPasswordLoginAccountByNormalizedEmail() {
    UUID accountId = registerMember("lookup-login@example.com", "Lookup Login");
    activate(accountId);

    var account = accountLookup.findByNormalizedEmail("lookup-login@example.com").orElseThrow();

    assertThat(account.publicId()).isEqualTo(accountId);
    assertThat(account.status()).isEqualTo(AccountAuthStatus.ACTIVE);
    assertThat(account.passwordHash()).startsWith("{argon2}$argon2id$");
    assertThat(account.roleCodes()).containsExactly("ROLE_MEMBER");
  }

  /** 로그인 성공 기록은 refresh token 저장, 실패 횟수 초기화, last_login_at, 성공 감사 이벤트를 함께 반영합니다. */
  @Test
  void recordsPasswordLoginSuccessWithRefreshTokenAndAuditEvent() {
    UUID accountId = registerMember("success-login@example.com", "Success Login");
    alignStoredTimestampsBefore(accountId, NOW.minusSeconds(120));
    Long internalAccountId = internalId(accountId);
    resultRecorder.recordKnownFailure(
        accountId,
        "0".repeat(64),
        PasswordLoginFailureReason.PASSWORD_MISMATCH,
        NOW.minusSeconds(60));

    resultRecorder.recordSuccess(
        new PasswordLoginSuccess(
            accountId,
            new StoredRefreshToken(
                REFRESH_TOKEN_HASH, FAMILY_ID, NOW, NOW.plusSeconds(30L * 24 * 60 * 60))));

    assertThat(
            jdbcTemplate.queryForObject(
                "select token_hash from refresh_token where account_id = ?",
                String.class,
                internalAccountId))
        .isEqualTo(REFRESH_TOKEN_HASH);
    assertThat(
            jdbcTemplate.queryForObject(
                "select failed_login_count from account_password_credential where account_id = ?",
                Integer.class,
                internalAccountId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "select last_failed_login_at from account_password_credential where account_id = ?",
                Instant.class,
                internalAccountId))
        .isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "select last_login_at from account where id = ?", Instant.class, internalAccountId))
        .isEqualTo(NOW);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_id = ? and event_type = 'PASSWORD_LOGIN' and result = 'SUCCESS'
                """,
                Integer.class,
                internalAccountId))
        .isOne();
  }

  /** browser session 로그인 성공은 refresh token 없이 실패 상태, last login과 감사 이벤트만 갱신합니다. */
  @Test
  void recordsInteractiveLoginSuccessWithoutRefreshToken() {
    UUID accountId = registerMember("interactive-login@example.com", "Interactive Login");
    alignStoredTimestampsBefore(accountId, NOW.minusSeconds(120));
    Long internalAccountId = internalId(accountId);
    resultRecorder.recordKnownFailure(
        accountId,
        "3".repeat(64),
        PasswordLoginFailureReason.PASSWORD_MISMATCH,
        NOW.minusSeconds(60));

    resultRecorder.recordInteractiveSuccess(accountId, NOW);

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where account_id = ?",
                Integer.class,
                internalAccountId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "select failed_login_count from account_password_credential where account_id = ?",
                Integer.class,
                internalAccountId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "select last_login_at from account where id = ?", Instant.class, internalAccountId))
        .isEqualTo(NOW);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_id = ? and event_type = 'PASSWORD_LOGIN' and result = 'SUCCESS'
                """,
                Integer.class,
                internalAccountId))
        .isOne();
  }

  /** account를 특정한 실패는 credential 실패 횟수와 PASSWORD_LOGIN 실패 reason을 account에 연결합니다. */
  @Test
  void recordsKnownPasswordLoginFailure() {
    UUID accountId = registerMember("failure-login@example.com", "Failure Login");
    alignStoredTimestampsBefore(accountId, NOW.minusSeconds(120));
    Long internalAccountId = internalId(accountId);
    String identifierHash = "1".repeat(64);

    resultRecorder.recordKnownFailure(
        accountId, identifierHash, PasswordLoginFailureReason.PASSWORD_MISMATCH, NOW);

    assertThat(
            jdbcTemplate.queryForObject(
                "select failed_login_count from account_password_credential where account_id = ?",
                Integer.class,
                internalAccountId))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "select last_failed_login_at from account_password_credential where account_id = ?",
                Instant.class,
                internalAccountId))
        .isEqualTo(NOW);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select identifier_hash
                from auth_event
                where account_id = ?
                  and event_type = 'PASSWORD_LOGIN'
                  and result = 'FAILURE'
                  and reason_code = 'PASSWORD_MISMATCH'
                """,
                String.class,
                internalAccountId))
        .isEqualTo(identifierHash);
  }

  /** account를 특정하지 못한 실패는 account_id 없이 identifier_hash만 남겨 원문 email을 저장하지 않습니다. */
  @Test
  void recordsUnknownPasswordLoginFailureWithoutAccountId() {
    String identifierHash = "2".repeat(64);

    resultRecorder.recordUnknownFailure(identifierHash, NOW);

    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_id is null
                  and identifier_hash = ?
                  and event_type = 'PASSWORD_LOGIN'
                  and result = 'FAILURE'
                  and reason_code = 'UNKNOWN_IDENTIFIER'
                """,
                Integer.class,
                identifierHash))
        .isOne();
  }

  private UUID registerMember(String email, String displayName) {
    return registerMemberAccountUseCase
        .execute(new RegisterMemberAccountCommand(email, PASSWORD, displayName))
        .accountPublicId();
  }

  private void activate(UUID accountPublicId) {
    jdbcTemplate.update(
        "update account set status = 'ACTIVE' where public_id = ?", accountPublicId);
  }

  private Long internalId(UUID accountPublicId) {
    return jdbcTemplate.queryForObject(
        "select id from account where public_id = ?", Long.class, accountPublicId);
  }

  /**
   * repository에 고정 시각을 직접 넘기는 테스트라 DB 감사 timestamp도 같은 시계 기준으로 맞춥니다.
   *
   * <p>실제 API 흐름은 service clock이 생성 이후 시각을 사용하지만, 이 통합 테스트는 adapter 계약만 검증하기 위해 repository를 직접
   * 호출합니다.
   */
  private void alignStoredTimestampsBefore(UUID accountPublicId, Instant baseline) {
    Timestamp baselineTimestamp = Timestamp.from(baseline);
    jdbcTemplate.update(
        """
        update account
        set created_at = ?, updated_at = ?
        where public_id = ?
        """,
        baselineTimestamp,
        baselineTimestamp,
        accountPublicId);
    jdbcTemplate.update(
        """
        update account_password_credential
        set password_updated_at = ?,
            created_at = ?,
            updated_at = ?
        where account_id = (select id from account where public_id = ?)
        """,
        baselineTimestamp,
        baselineTimestamp,
        baselineTimestamp,
        accountPublicId);
  }
}
