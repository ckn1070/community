package com.ronams.carmunity.auth.internal.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import com.ronams.carmunity.auth.internal.domain.RefreshSession;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * refresh token lifecycle adapter가 PostgreSQL schema를 올바르게 사용하는지 검증합니다.
 *
 * <p>rotation과 reuse detection은 self reference, partial active index, check constraint에 기대므로 실제
 * PostgreSQL에서 확인합니다.
 */
@SpringBootTest
class RefreshTokenPersistenceAdapterTest extends PostgreSqlIntegrationTestSupport {

  private static final Instant NOW = Instant.parse("2099-06-23T00:00:00Z");

  @Autowired private RefreshTokenPersistenceAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** hash 조회는 lock 대상 refresh token과 account public id를 함께 application snapshot으로 변환합니다. */
  @Test
  void findsRefreshTokenByHashWithAccountPublicId() {
    UUID publicId = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
    createAccount(publicId, "refresh-lookup@example.com", "ACTIVE");
    UUID familyId = UUID.fromString("6e74f192-9256-4569-8579-6b8f557d9cb1");
    Long tokenId = createRefreshToken(publicId, "a".repeat(64), familyId);

    RefreshSession token = adapter.findByHashForUpdate("a".repeat(64)).orElseThrow();

    assertThat(token.id()).isEqualTo(tokenId);
    assertThat(token.accountPublicId()).isEqualTo(publicId);
    assertThat(token.familyId()).isEqualTo(familyId);
  }

  /** rotation은 새 token 저장, 기존 token 폐기, TOKEN_REFRESH 감사 이벤트를 같은 transaction 경계에 둡니다. */
  @Test
  void rotatesRefreshTokenAndRecordsRefreshEvent() {
    UUID publicId = UUID.fromString("38d1dcb8-7bbf-4390-8e28-c3a8cd3e2f0d");
    createAccount(publicId, "refresh-rotate@example.com", "ACTIVE");
    UUID familyId = UUID.fromString("154c0fef-4f88-449f-a070-c8b0ed201501");
    Long oldTokenId = createRefreshToken(publicId, "b".repeat(64), familyId);
    RefreshSession current = adapter.findByHashForUpdate("b".repeat(64)).orElseThrow();
    IssuedRefreshToken replacement =
        new IssuedRefreshToken(
            "new-refresh-token", "c".repeat(64), familyId, NOW, NOW.plusSeconds(2_592_000));

    adapter.rotate(current, replacement, NOW);

    Long newTokenId =
        jdbcTemplate.queryForObject(
            "select id from refresh_token where token_hash = ?", Long.class, "c".repeat(64));
    assertThat(
            jdbcTemplate.queryForObject(
                "select revoked_reason from refresh_token where id = ?", String.class, oldTokenId))
        .isEqualTo("ROTATED");
    assertThat(
            jdbcTemplate.queryForObject(
                "select replaced_by_token_id from refresh_token where id = ?",
                Long.class,
                oldTokenId))
        .isEqualTo(newTokenId);
    assertThat(
            jdbcTemplate.queryForObject(
                "select family_id from refresh_token where id = ?", UUID.class, newTokenId))
        .isEqualTo(familyId);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_public_id = ? and event_type = 'TOKEN_REFRESH' and result = 'SUCCESS'
                """,
                Integer.class,
                publicId))
        .isOne();
  }

  /** reuse detection은 같은 family의 활성 token 폐기와 실패 감사 이벤트 저장을 요청합니다. */
  @Test
  void revokesActiveFamilyTokensForReuseDetectionAndRecordsEvent() {
    UUID publicId = UUID.fromString("bbd1502f-fc98-4c73-a5b2-bc53558e1b62");
    createAccount(publicId, "refresh-reuse@example.com", "ACTIVE");
    UUID familyId = UUID.fromString("c1c60ef3-5571-42fb-a46a-a10cadc14e2f");
    Long reusedTokenId = createRefreshToken(publicId, "d".repeat(64), familyId);
    Long activeTokenId = createRefreshToken(publicId, "e".repeat(64), familyId);
    markRotated(reusedTokenId, activeTokenId);
    RefreshSession reused = adapter.findByHashForUpdate("d".repeat(64)).orElseThrow();

    adapter.revokeFamilyForReuse(reused, NOW);

    assertThat(
            jdbcTemplate.queryForObject(
                "select revoked_reason from refresh_token where id = ?",
                String.class,
                activeTokenId))
        .isEqualTo("REUSE_DETECTED");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_public_id = ?
                  and event_type = 'TOKEN_REFRESH'
                  and result = 'FAILURE'
                  and reason_code = 'TOKEN_REUSE_DETECTED'
                """,
                Integer.class,
                publicId))
        .isOne();
  }

  /** 로그아웃은 제출된 단일 token만 LOGOUT으로 폐기하고 성공 감사 이벤트를 같은 transaction에 남깁니다. */
  @Test
  void revokesSingleRefreshTokenForLogoutAndRecordsEvent() {
    UUID publicId = UUID.fromString("c8f7975b-6601-4bea-a448-dfd630a7f983");
    createAccount(publicId, "refresh-logout@example.com", "ACTIVE");
    UUID familyId = UUID.fromString("33f8ae96-1ef1-4c97-b4de-3b239284d19c");
    Long logoutTokenId = createRefreshToken(publicId, "f".repeat(64), familyId);
    Long otherTokenId = createRefreshToken(publicId, "0".repeat(64), familyId);
    RefreshSession current = adapter.findByHashForUpdate("f".repeat(64)).orElseThrow();

    adapter.revokeForLogout(current, NOW);

    assertThat(
            jdbcTemplate.queryForObject(
                "select revoked_reason from refresh_token where id = ?",
                String.class,
                logoutTokenId))
        .isEqualTo("LOGOUT");
    assertThat(
            jdbcTemplate.queryForObject(
                "select revoked_at is not null from refresh_token where id = ?",
                Boolean.class,
                logoutTokenId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "select revoked_at is null from refresh_token where id = ?",
                Boolean.class,
                otherTokenId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_public_id = ?
                  and event_type = 'LOGOUT'
                  and result = 'SUCCESS'
                  and metadata = '{}'::jsonb
                """,
                Integer.class,
                publicId))
        .isOne();
  }

  private Long createAccount(UUID publicId, String email, String status) {
    return jdbcTemplate.queryForObject(
        """
        insert into account (public_id, email, email_normalized, status)
        values (?, ?, ?, ?)
        returning id
        """,
        Long.class,
        publicId,
        email,
        email,
        status);
  }

  private Long createRefreshToken(UUID accountPublicId, String tokenHash, UUID familyId) {
    return jdbcTemplate.queryForObject(
        """
        insert into refresh_token (account_public_id, token_hash, family_id, issued_at, expires_at)
        values (?, ?, ?, ?, ?)
        returning id
        """,
        Long.class,
        accountPublicId,
        tokenHash,
        familyId,
        Timestamp.from(NOW.minusSeconds(60)),
        Timestamp.from(NOW.plusSeconds(3600)));
  }

  private void markRotated(Long oldTokenId, Long replacementTokenId) {
    jdbcTemplate.update(
        """
        update refresh_token
        set revoked_at = ?,
            revoked_reason = 'ROTATED',
            replaced_by_token_id = ?
        where id = ?
        """,
        Timestamp.from(NOW.minusSeconds(30)),
        replacementTokenId,
        oldTokenId);
  }
}
