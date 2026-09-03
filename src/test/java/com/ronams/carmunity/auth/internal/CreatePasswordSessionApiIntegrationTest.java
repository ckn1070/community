package com.ronams.carmunity.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.account.internal.application.command.RegisterMemberAccountCommand;
import com.ronams.carmunity.account.internal.application.port.in.RegisterMemberAccountUseCase;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 로그인 API의 Web, Security, application, persistence wiring을 실제 Spring context와 PostgreSQL schema로
 * 검증합니다.
 *
 * <p>token 원문은 응답에만 있고 DB에는 hash만 남아야 하므로, slice 테스트가 아니라 통합 흐름에서 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreatePasswordSessionApiIntegrationTest extends PostgreSqlIntegrationTestSupport {

  private static final String PASSWORD = "correct horse battery staple";
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  @Autowired private MockMvc mockMvc;
  @Autowired private RegisterMemberAccountUseCase registerMemberAccountUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** ACTIVE account 로그인은 full session token을 반환하고 refresh token hash와 성공 감사 이벤트를 저장합니다. */
  @Test
  void postAuthSessionsCreatesFullSessionForActiveAccount() throws Exception {
    UUID accountPublicId = registerMember("active-login@example.com", "Active Login");
    activate(accountPublicId);

    JsonNode response =
        postLogin("ACTIVE-LOGIN@example.com", PASSWORD)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memberId").value(accountPublicId.toString()))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.sessionAccess").value("FULL"))
            .andReturnJson();

    Long internalAccountId = internalId(accountPublicId);
    String refreshToken = response.get("refreshToken").asString();

    assertThat(response.get("accessToken").asString()).isNotBlank();
    assertThat(refreshToken).isNotBlank();
    assertThat(
            jdbcTemplate.queryForObject(
                "select token_hash from refresh_token where account_public_id = ?",
                String.class,
                accountPublicId))
        .matches("^[0-9a-f]{64}$")
        .isNotEqualTo(refreshToken);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_public_id = ? and event_type = 'PASSWORD_LOGIN' and result = 'SUCCESS'
                """,
                Integer.class,
                accountPublicId))
        .isOne();
  }

  /** PENDING account는 limited session을 받으며 일반 보호 API authorization에서 403으로 막힙니다. */
  @Test
  void postAuthSessionsCreatesLimitedSessionForPendingAccount() throws Exception {
    UUID accountPublicId = registerMember("pending-login@example.com", "Pending Login");

    JsonNode response =
        postLogin("pending-login@example.com", PASSWORD)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.memberId").value(accountPublicId.toString()))
            .andExpect(jsonPath("$.sessionAccess").value("LIMITED"))
            .andReturnJson();

    mockMvc
        .perform(
            get("/api/not-existing-protected-resource")
                .header(
                    HttpHeaders.AUTHORIZATION, "Bearer " + response.get("accessToken").asString()))
        .andExpect(status().isForbidden());
  }

  /** password 불일치는 계정 존재 여부를 더 드러내지 않는 401로 응답하고 실패 횟수와 실패 이벤트를 기록합니다. */
  @Test
  void postAuthSessionsReturnsUnauthorizedAndRecordsFailureForWrongPassword() throws Exception {
    UUID accountPublicId = registerMember("wrong-password@example.com", "Wrong Password");
    Long internalAccountId = internalId(accountPublicId);

    postLogin("wrong-password@example.com", "wrong password")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

    assertThat(
            jdbcTemplate.queryForObject(
                "select failed_login_count from account_password_credential where account_id = ?",
                Integer.class,
                internalAccountId))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_public_id = ?
                  and event_type = 'PASSWORD_LOGIN'
                  and result = 'FAILURE'
                  and reason_code = 'PASSWORD_MISMATCH'
                """,
                Integer.class,
                accountPublicId))
        .isOne();
  }

  private LoginResultActions postLogin(String email, String password) throws Exception {
    return new LoginResultActions(
        mockMvc.perform(
            post("/api/v1/auth/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """
                        .formatted(email, password))));
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

  private record LoginResultActions(org.springframework.test.web.servlet.ResultActions delegate) {

    LoginResultActions andExpect(org.springframework.test.web.servlet.ResultMatcher matcher)
        throws Exception {
      delegate.andExpect(matcher);
      return this;
    }

    JsonNode andReturnJson() throws Exception {
      return JSON_MAPPER.readTree(delegate.andReturn().getResponse().getContentAsString());
    }
  }
}
