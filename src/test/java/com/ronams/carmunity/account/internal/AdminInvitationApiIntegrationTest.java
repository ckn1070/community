package com.ronams.carmunity.account.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.account.internal.application.service.AdminInvitationTokenHasher;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 관리자 초대 API의 Web, Security, application, persistence, PostgreSQL wiring을 실제 Spring context에서
 * 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminInvitationApiIntegrationTest extends PostgreSqlIntegrationTestSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AdminInvitationTokenHasher tokenHasher;

  /** 최고 관리자 초대 생성은 raw token을 저장하지 않고, 재초대 시 기존 open 초대를 폐기합니다. */
  @Test
  void postAdminInvitationsCreatesInvitationAndRevokesPreviousOpenInvitation() throws Exception {
    UUID inviterPublicId = createAccount("invite-super@example.com", "invite-super@example.com");

    mockMvc
        .perform(
            post("/api/v1/admin/invitations")
                .with(superAdminFullSessionJwt(inviterPublicId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "NEW-ADMIN@example.com",
                      "roleCode": "ROLE_ADMIN"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("new-admin@example.com"))
        .andExpect(jsonPath("$.roleCode").value("ROLE_ADMIN"));

    Long firstInvitationId =
        jdbcTemplate.queryForObject(
            "select id from admin_invitation where email_normalized = ?",
            Long.class,
            "new-admin@example.com");
    assertThat(
            jdbcTemplate.queryForObject(
                "select token_hash from admin_invitation where id = ?",
                String.class,
                firstInvitationId))
        .matches("^[0-9a-f]{64}$");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_id = ?
                  and event_type = 'ADMIN_INVITATION_CREATED'
                  and result = 'SUCCESS'
                """,
                Integer.class,
                internalAccountId(inviterPublicId)))
        .isOne();

    mockMvc
        .perform(
            post("/api/v1/admin/invitations")
                .with(superAdminFullSessionJwt(inviterPublicId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "new-admin@example.com",
                      "roleCode": "ROLE_ADMIN"
                    }
                    """))
        .andExpect(status().isCreated());

    assertThat(
            jdbcTemplate.queryForObject(
                "select revoked_by_account_id from admin_invitation where id = ?",
                Long.class,
                firstInvitationId))
        .isEqualTo(internalAccountId(inviterPublicId));
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from admin_invitation
                where email_normalized = ?
                  and accepted_at is null
                  and revoked_at is null
                """,
                Integer.class,
                "new-admin@example.com"))
        .isOne();
  }

  /** public 초대 수락은 token hash 검증 뒤 ACTIVE 관리자 account graph와 수락 감사 이벤트를 남깁니다. */
  @Test
  void postAdminInvitationAcceptancesCreatesActiveAdminAccount() throws Exception {
    UUID inviterPublicId = createAccount("accept-super@example.com", "accept-super@example.com");
    Long inviterAccountId = internalAccountId(inviterPublicId);
    UUID invitationPublicId =
        createInvitation(
            inviterAccountId,
            "accepted-admin@example.com",
            tokenHasher.hash("raw-admin-invitation-token"),
            futureExpiresAt());

    mockMvc
        .perform(
            post("/api/v1/admin/invitations/{invitationId}/acceptances", invitationPublicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "raw-admin-invitation-token",
                      "password": "correct horse battery staple",
                      "passwordConfirm": "correct horse battery staple",
                      "displayName": "Accepted Admin"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("accepted-admin@example.com"))
        .andExpect(jsonPath("$.roleCode").value("ROLE_ADMIN"));

    Long acceptedAccountId = internalAccountIdByEmail("accepted-admin@example.com");
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from account where id = ?", String.class, acceptedAccountId))
        .isEqualTo("ACTIVE");
    assertThat(
            jdbcTemplate.queryForObject(
                "select email_verified_at is not null from account where id = ?",
                Boolean.class,
                acceptedAccountId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "select password_change_required from account_password_credential where account_id = ?",
                Boolean.class,
                acceptedAccountId))
        .isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select r.code
                from account_role_assignment a
                join auth_role r on r.id = a.role_id
                where a.account_id = ?
                  and a.granted_by_account_id = ?
                """,
                String.class,
                acceptedAccountId,
                inviterAccountId))
        .isEqualTo("ROLE_ADMIN");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from admin_invitation
                where public_id = ?
                  and accepted_account_id = ?
                  and accepted_at is not null
                """,
                Integer.class,
                invitationPublicId,
                acceptedAccountId))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_id = ?
                  and event_type = 'ADMIN_INVITATION_ACCEPTED'
                  and result = 'SUCCESS'
                """,
                Integer.class,
                acceptedAccountId))
        .isOne();
  }

  /** 최고 관리자는 open 초대를 회수할 수 있고, 회수된 초대는 이후 수락될 수 없습니다. */
  @Test
  void deleteAdminInvitationRevokesOpenInvitation() throws Exception {
    UUID revokerPublicId = createAccount("revoke-super@example.com", "revoke-super@example.com");
    Long revokerAccountId = internalAccountId(revokerPublicId);
    UUID invitationPublicId =
        createInvitation(
            revokerAccountId,
            "revoked-admin@example.com",
            tokenHasher.hash("raw-admin-invitation-token-for-revoke"),
            futureExpiresAt());

    mockMvc
        .perform(
            delete("/api/v1/admin/invitations/{invitationId}", invitationPublicId)
                .with(superAdminFullSessionJwt(revokerPublicId)))
        .andExpect(status().isNoContent());

    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from admin_invitation
                where public_id = ?
                  and revoked_by_account_id = ?
                  and revoked_at is not null
                """,
                Integer.class,
                invitationPublicId,
                revokerAccountId))
        .isOne();

    mockMvc
        .perform(
            post("/api/v1/admin/invitations/{invitationId}/acceptances", invitationPublicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "raw-admin-invitation-token",
                      "password": "correct horse battery staple",
                      "passwordConfirm": "correct horse battery staple",
                      "displayName": "Revoked Admin"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ADMIN_INVITATION_ALREADY_CLOSED"));
  }

  private UUID createInvitation(
      Long inviterAccountId, String email, String tokenHash, Instant expiresAt) {
    UUID invitationPublicId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into admin_invitation (
            public_id,
            email,
            email_normalized,
            role_id,
            token_hash,
            invited_by_account_id,
            issued_at,
            expires_at
        )
        values (?, ?, ?, ?, ?, ?, now(), ?)
        """,
        invitationPublicId,
        email,
        email,
        roleId("ROLE_ADMIN"),
        tokenHash,
        inviterAccountId,
        Timestamp.from(expiresAt));
    return invitationPublicId;
  }

  private UUID createAccount(String email, String normalizedEmail) {
    UUID publicId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into account (public_id, email, email_normalized, status, email_verified_at)
        values (?, ?, ?, 'ACTIVE', now())
        """,
        publicId,
        email,
        normalizedEmail);
    return publicId;
  }

  private Long internalAccountId(UUID publicId) {
    return jdbcTemplate.queryForObject(
        "select id from account where public_id = ?", Long.class, publicId);
  }

  private Long internalAccountIdByEmail(String normalizedEmail) {
    return jdbcTemplate.queryForObject(
        "select id from account where email_normalized = ?", Long.class, normalizedEmail);
  }

  private Long roleId(String roleCode) {
    return jdbcTemplate.queryForObject(
        "select id from auth_role where code = ?", Long.class, roleCode);
  }

  private Instant futureExpiresAt() {
    return Instant.now().plusSeconds(86_400);
  }

  private static RequestPostProcessor superAdminFullSessionJwt(UUID subject) {
    return jwt()
        .jwt(
            jwt ->
                jwt.subject(subject.toString())
                    .claim("roles", List.of("ROLE_SUPER_ADMIN"))
                    .claim("session_access", "FULL")
                    .claim("password_change_required", false))
        .authorities(
            new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"),
            new SimpleGrantedAuthority("SESSION_FULL"),
            new SimpleGrantedAuthority("PASSWORD_CHANGE_NOT_REQUIRED"));
  }
}
