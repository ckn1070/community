package com.ronams.carmunity.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenHashPort;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제 Spring MVC, Security allow-list, refresh application service, JPA adapter, PostgreSQL schema를
 * 함께 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenReissueApiIntegrationTest extends PostgreSqlIntegrationTestSupport {

  private static final Instant ISSUED_AT = Instant.parse("2026-06-23T00:00:00Z");
  private static final Instant EXPIRED_AT = Instant.parse("2026-06-24T00:00:00Z");
  private static final Instant EXPIRES_AT = Instant.parse("2099-06-23T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private RefreshTokenHashPort refreshTokenHashPort;

  /** refresh 요청 하나가 기존 token 폐기, 새 token 저장, 감사 이벤트 기록까지 같은 흐름으로 완료되는지 검증합니다. */
  @Test
  void postRefreshRotatesRefreshTokenAndReturnsNewTokenPair() throws Exception {
    UUID memberId = UUID.fromString("7f92f33b-c4e5-4656-bd41-cabf0846f101");
    Long accountId = createAccount(memberId, "refresh-api-success@example.com", "ACTIVE");
    grantMemberRole(accountId);
    UUID familyId = UUID.fromString("33621ec2-1662-40c9-ad0d-31a1f5eaad2e");
    Long oldTokenId = createRefreshToken(accountId, "seed-refresh-token", familyId);

    mockMvc
        .perform(
            post("/api/v1/auth/sessions/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "seed-refresh-token"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").value(not("seed-refresh-token")))
        .andExpect(jsonPath("$.memberId").value(memberId.toString()))
        .andExpect(jsonPath("$.roles[0]").value("ROLE_MEMBER"));

    Long newTokenId =
        jdbcTemplate.queryForObject(
            """
            select replaced_by_token_id
            from refresh_token
            where id = ?
            """,
            Long.class,
            oldTokenId);

    assertThat(newTokenId).isNotNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "select revoked_reason from refresh_token where id = ?", String.class, oldTokenId))
        .isEqualTo("ROTATED");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from refresh_token
                where family_id = ? and revoked_at is null
                """,
                Integer.class,
                familyId))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_public_id = (select public_id from account where id = ?)
                  and event_type = 'TOKEN_REFRESH' and result = 'SUCCESS'
                """,
                Integer.class,
                accountId))
        .isOne();
  }

  /** 이미 rotation된 token을 다시 제출하면 같은 family의 활성 token을 모두 재사용 탐지 상태로 폐기합니다. */
  @Test
  void postRefreshRevokesTokenFamilyWhenRotatedTokenIsReused() throws Exception {
    UUID memberId = UUID.fromString("ef2b6bb3-eb78-4e29-b2d7-63a8644a8577");
    Long accountId = createAccount(memberId, "refresh-api-reuse@example.com", "ACTIVE");
    grantMemberRole(accountId);
    UUID familyId = UUID.fromString("90b3ce7d-aac2-4429-8350-f8a4f4fbe5b9");
    createRefreshToken(accountId, "reused-refresh-token", familyId);

    postRefresh("reused-refresh-token").andExpect(status().isOk());

    postRefresh("reused-refresh-token")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from refresh_token
                where family_id = ? and revoked_at is null
                """,
                Integer.class,
                familyId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from refresh_token
                where family_id = ? and revoked_reason = 'REUSE_DETECTED'
                """,
                Integer.class,
                familyId))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_public_id = (select public_id from account where id = ?)
                  and event_type = 'TOKEN_REFRESH'
                  and result = 'FAILURE'
                  and reason_code = 'TOKEN_REUSE_DETECTED'
                """,
                Integer.class,
                accountId))
        .isOne();
  }

  /** rotation된 원 token이 만료된 뒤 재사용돼도 활성 replacement와 감사 결과를 함께 폐기·기록합니다. */
  @Test
  void postRefreshRevokesActiveReplacementWhenReusedTokenIsExpired() throws Exception {
    UUID memberId = UUID.fromString("5e98905f-7c0a-4d42-9212-70d8ecb8d688");
    Long accountId = createAccount(memberId, "refresh-api-expired-reuse@example.com", "ACTIVE");
    grantMemberRole(accountId);
    UUID familyId = UUID.fromString("055ada58-eea1-4bf3-a14a-c6943aa63922");
    Long oldTokenId = createRefreshToken(accountId, "expired-reused-refresh-token", familyId);

    postRefresh("expired-reused-refresh-token").andExpect(status().isOk());
    expireRefreshToken(oldTokenId);

    postRefresh("expired-reused-refresh-token")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where family_id = ? and revoked_at is null",
                Integer.class,
                familyId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from refresh_token
                where family_id = ? and revoked_reason = 'REUSE_DETECTED'
                """,
                Integer.class,
                familyId))
        .isOne();
    assertThat(reuseFailureEventCount(accountId)).isOne();
  }

  /** 폐기·교체 이력이 없는 단순 만료 token은 family 폐기나 reuse 감사 이벤트를 만들지 않습니다. */
  @Test
  void postRefreshDoesNotRecordReuseForSimplyExpiredToken() throws Exception {
    UUID memberId = UUID.fromString("6f7147dc-c5bb-4f9a-bc32-77a7c5e2e22d");
    Long accountId = createAccount(memberId, "refresh-api-expired-only@example.com", "ACTIVE");
    grantMemberRole(accountId);
    UUID familyId = UUID.fromString("71e53efe-81b2-4c0a-849a-8a8f8c4924a6");
    Long tokenId = createRefreshToken(accountId, "expired-only-refresh-token", familyId);
    expireRefreshToken(tokenId);

    postRefresh("expired-only-refresh-token")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where id = ? and revoked_at is null",
                Integer.class,
                tokenId))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from refresh_token
                where family_id = ? and revoked_reason = 'REUSE_DETECTED'
                """,
                Integer.class,
                familyId))
        .isZero();
    assertThat(reuseFailureEventCount(accountId)).isZero();
  }

  /** 같은 refresh token이 동시에 제출되어도 중복 rotation을 만들지 않고 재사용 탐지 정책으로 닫습니다. */
  @Test
  void postRefreshAllowsOnlyOneConcurrentRotationForSameRefreshToken() throws Exception {
    UUID memberId = UUID.fromString("256ea606-262d-4d91-a48e-9892ca8125b2");
    Long accountId = createAccount(memberId, "refresh-api-concurrent@example.com", "ACTIVE");
    grantMemberRole(accountId);
    UUID familyId = UUID.fromString("95d98eca-1100-4a8c-90c0-6fad47765476");
    createRefreshToken(accountId, "concurrent-refresh-token", familyId);
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try {
      Future<Integer> firstStatus =
          executorService.submit(() -> postRefreshStatus("concurrent-refresh-token", ready, start));
      Future<Integer> secondStatus =
          executorService.submit(() -> postRefreshStatus("concurrent-refresh-token", ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

      start.countDown();

      assertThat(
              List.of(firstStatus.get(5, TimeUnit.SECONDS), secondStatus.get(5, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 401);
    } finally {
      executorService.shutdownNow();
    }

    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from refresh_token
                where family_id = ? and revoked_reason = 'ROTATED'
                """,
                Integer.class,
                familyId))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from refresh_token
                where family_id = ? and revoked_reason = 'REUSE_DETECTED'
                """,
                Integer.class,
                familyId))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from refresh_token
                where family_id = ? and revoked_at is null
                """,
                Integer.class,
                familyId))
        .isZero();
  }

  /** refresh 시점에 계정 상태가 거부 대상이면 새 token을 만들지 않고 family를 폐기합니다. */
  @Test
  void postRefreshRejectsSuspendedAccountAndRevokesTokenFamily() throws Exception {
    UUID memberId = UUID.fromString("34d659bd-4153-44c0-b93b-d40e241948cc");
    Long accountId = createAccount(memberId, "refresh-api-suspended@example.com", "SUSPENDED");
    grantMemberRole(accountId);
    UUID familyId = UUID.fromString("c4c18ddb-262e-4a98-b23d-70ebd99ae8ce");
    Long tokenId = createRefreshToken(accountId, "suspended-refresh-token", familyId);

    postRefresh("suspended-refresh-token")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    assertThat(
            jdbcTemplate.queryForObject(
                "select revoked_reason from refresh_token where id = ?", String.class, tokenId))
        .isEqualTo("ACCOUNT_SUSPENDED");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_public_id = (select public_id from account where id = ?)
                  and event_type = 'TOKEN_REFRESH'
                  and result = 'FAILURE'
                  and reason_code = 'ACCOUNT_SUSPENDED'
                """,
                Integer.class,
                accountId))
        .isOne();
  }

  private org.springframework.test.web.servlet.ResultActions postRefresh(String refreshToken)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/sessions/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "refreshToken": "%s"
                }
                """
                    .formatted(refreshToken)));
  }

  private int postRefreshStatus(String refreshToken, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    ready.countDown();
    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
    return postRefresh(refreshToken).andReturn().getResponse().getStatus();
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

  private void grantMemberRole(Long accountId) {
    jdbcTemplate.update(
        """
        insert into account_role_assignment (account_id, role_id)
        select ?, id
        from auth_role
        where code = 'ROLE_MEMBER'
        """,
        accountId);
  }

  private Long createRefreshToken(Long accountId, String rawRefreshToken, UUID familyId) {
    return jdbcTemplate.queryForObject(
        """
        insert into refresh_token (account_public_id, token_hash, family_id, issued_at, expires_at)
        values ((select public_id from account where id = ?), ?, ?, ?, ?)
        returning id
        """,
        Long.class,
        accountId,
        refreshTokenHashPort.hash(rawRefreshToken),
        familyId,
        Timestamp.from(ISSUED_AT),
        Timestamp.from(EXPIRES_AT));
  }

  private void expireRefreshToken(Long tokenId) {
    jdbcTemplate.update(
        "update refresh_token set expires_at = ? where id = ?",
        Timestamp.from(EXPIRED_AT),
        tokenId);
  }

  private Integer reuseFailureEventCount(Long accountId) {
    return jdbcTemplate.queryForObject(
        """
        select count(*)
        from auth_event
        where account_public_id = (select public_id from account where id = ?)
          and event_type = 'TOKEN_REFRESH'
          and result = 'FAILURE'
          and reason_code = 'TOKEN_REUSE_DETECTED'
        """,
        Integer.class,
        accountId);
  }
}
