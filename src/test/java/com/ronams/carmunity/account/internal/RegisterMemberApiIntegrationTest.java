package com.ronams.carmunity.account.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제 Spring MVC, Security allow-list, application service, JPA adapter, PostgreSQL schema를 함께
 * 검증합니다.
 *
 * <p>이 테스트는 slice 테스트가 놓칠 수 있는 Bean wiring, transaction rollback, Flyway schema mapping을 회원가입 happy
 * path와 충돌 path에서 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegisterMemberApiIntegrationTest extends PostgreSqlIntegrationTestSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** 회원가입 요청은 계정 graph와 가입 감사 이벤트만 저장하고, 인증 메일 요청은 자동 실행하지 않습니다. */
  @Test
  void postMembersCreatesPendingMemberWithoutRequestingEmailVerification() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "API-USER@example.com",
                      "password": "correct horse battery staple",
                      "displayName": "Api   User"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.memberId").exists())
        .andExpect(jsonPath("$.email").value("api-user@example.com"))
        .andExpect(jsonPath("$.displayName").value("Api User"))
        .andExpect(jsonPath("$.status").value("PENDING"));

    Long accountId =
        jdbcTemplate.queryForObject(
            "select id from account where email_normalized = ?",
            Long.class,
            "api-user@example.com");

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from account where id = ?", String.class, accountId))
        .isEqualTo("PENDING");
    assertThat(
            jdbcTemplate.queryForObject(
                "select display_name_normalized from account_profile where account_id = ?",
                String.class,
                accountId))
        .isEqualTo("api user");
    assertThat(
            jdbcTemplate.queryForObject(
                "select password_hash from account_password_credential where account_id = ?",
                String.class,
                accountId))
        .startsWith("{argon2}$argon2id$")
        .doesNotContain("correct horse battery staple");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select r.code
                from account_role_assignment a
                join auth_role r on r.id = a.role_id
                where a.account_id = ?
                """,
                String.class,
                accountId))
        .isEqualTo("ROLE_MEMBER");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_public_id = (select public_id from account where id = ?)
                  and event_type = 'ACCOUNT_SIGN_UP' and result = 'SUCCESS'
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
                  and purpose = 'EMAIL_VERIFICATION'
                  and delivery_channel = 'EMAIL'
                  and consumed_at is null
                  and revoked_at is null
                """,
                Integer.class,
                accountId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_public_id = (select public_id from account where id = ?)
                  and event_type = 'EMAIL_VERIFICATION_REQUEST'
                """,
                Integer.class,
                accountId))
        .isZero();
  }

  /** normalized email 충돌은 409로 노출되고 transaction rollback으로 두 번째 계정이 남지 않아야 합니다. */
  @Test
  void postMembersReturnsConflictForDuplicateNormalizedEmailWithoutCreatingSecondAccount()
      throws Exception {
    postValidMember("DUPLICATE-API@example.com", "Duplicate Api A");

    mockMvc
        .perform(
            post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "duplicate-api@example.com",
                      "password": "correct horse battery staple",
                      "displayName": "Duplicate Api B"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"))
        .andExpect(jsonPath("$.instance").value("/api/v1/members"));

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from account where email_normalized = ?",
                Integer.class,
                "duplicate-api@example.com"))
        .isOne();
  }

  /** 중복 시나리오의 선행 계정을 실제 API 경로로 만들어 wiring 차이를 숨기지 않습니다. */
  private void postValidMember(String email, String displayName) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "password": "correct horse battery staple",
                      "displayName": "%s"
                    }
                    """
                        .formatted(email, displayName)))
        .andExpect(status().isCreated());
  }
}
