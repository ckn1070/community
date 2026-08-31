package com.ronams.carmunity.account.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.account.internal.application.service.AdminInvitationTokenHasher;
import com.ronams.carmunity.notification.api.AdminInvitationNotificationRequest;
import com.ronams.carmunity.notification.api.AdminInvitationNotificationSender;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 초대 API의 Web, Security, application, persistence, PostgreSQL wiring을 실제 Spring context에서
 * 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminInvitationApiIntegrationTest extends PostgreSqlIntegrationTestSupport {

  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AdminInvitationTokenHasher tokenHasher;
  @MockitoBean private AdminInvitationNotificationSender notificationSender;

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
    UUID firstInvitationPublicId =
        jdbcTemplate.queryForObject(
            "select public_id from admin_invitation where id = ?", UUID.class, firstInvitationId);
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
    assertCreatedEventMetadata(
        internalAccountId(inviterPublicId),
        firstInvitationPublicId,
        "new-admin@example.com",
        false);

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
    UUID latestInvitationPublicId =
        jdbcTemplate.queryForObject(
            """
            select public_id
            from admin_invitation
            where email_normalized = ?
            order by id desc
            limit 1
            """,
            UUID.class,
            "new-admin@example.com");
    assertCreatedEventMetadata(
        internalAccountId(inviterPublicId),
        latestInvitationPublicId,
        "new-admin@example.com",
        true);
  }

  /** provider 실패는 API 성공과 이미 commit된 초대·생성 감사 이벤트를 바꾸지 않습니다. */
  @Test
  void postAdminInvitationsReturnsCreatedWhenNotificationFails() throws Exception {
    UUID inviterPublicId =
        createAccount("provider-failure-super@example.com", "provider-failure-super@example.com");
    doThrow(new IllegalStateException("provider failure"))
        .when(notificationSender)
        .send(any(AdminInvitationNotificationRequest.class));

    mockMvc
        .perform(
            post("/api/v1/admin/invitations")
                .with(superAdminFullSessionJwt(inviterPublicId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "provider-failure-admin@example.com",
                      "roleCode": "ROLE_ADMIN"
                    }
                    """))
        .andExpect(status().isCreated());

    UUID invitationPublicId =
        jdbcTemplate.queryForObject(
            "select public_id from admin_invitation where email_normalized = ?",
            UUID.class,
            "provider-failure-admin@example.com");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_id = ?
                  and event_type = 'ADMIN_INVITATION_CREATED'
                """,
                Integer.class,
                internalAccountId(inviterPublicId)))
        .isOne();
    assertCreatedEventMetadata(
        internalAccountId(inviterPublicId),
        invitationPublicId,
        "provider-failure-admin@example.com",
        false);
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
    Map<String, Object> acceptedEvent =
        jdbcTemplate.queryForMap(
            """
            select (select count(*) from jsonb_object_keys(metadata)) as key_count,
                   metadata ->> 'invitationPublicId' as invitation_public_id,
                   metadata ->> 'roleCode' as role_code,
                   metadata ->> 'invitedByAccountPublicId' as invited_by_account_public_id
            from auth_event
            where account_id = ?
              and event_type = 'ADMIN_INVITATION_ACCEPTED'
            order by id desc
            limit 1
            """,
            acceptedAccountId);
    assertThat(acceptedEvent)
        .containsEntry("key_count", 3L)
        .containsEntry("invitation_public_id", invitationPublicId.toString())
        .containsEntry("role_code", "ROLE_ADMIN")
        .containsEntry("invited_by_account_public_id", inviterPublicId.toString());
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
    Map<String, Object> revokedEvent =
        jdbcTemplate.queryForMap(
            """
            select (select count(*) from jsonb_object_keys(metadata)) as key_count,
                   metadata ->> 'invitationPublicId' as invitation_public_id,
                   metadata ->> 'targetEmailHash' as target_email_hash,
                   metadata ->> 'reasonCode' as reason_code
            from auth_event
            where account_id = ?
              and event_type = 'ADMIN_INVITATION_REVOKED'
            order by id desc
            limit 1
            """,
            revokerAccountId);
    assertThat(revokedEvent)
        .containsEntry("key_count", 3L)
        .containsEntry("invitation_public_id", invitationPublicId.toString())
        .containsEntry("target_email_hash", sha256Hex("revoked-admin@example.com"))
        .containsEntry("reason_code", "ADMIN_REVOKED");

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

  /** 같은 초대의 동시 수락은 row lock 뒤 terminal state를 재확인해 account graph 하나만 만듭니다. */
  @Test
  void concurrentAcceptancesCreateOneAdminAccountGraph() throws Exception {
    UUID inviterPublicId =
        createAccount("concurrent-accept-super@example.com", "concurrent-accept-super@example.com");
    UUID invitationPublicId =
        createInvitation(
            internalAccountId(inviterPublicId),
            "concurrent-accepted-admin@example.com",
            tokenHasher.hash("concurrent-accept-token"),
            futureExpiresAt());

    List<ConcurrentResponse> responses =
        runConcurrently(
            () ->
                acceptanceStatus(
                    invitationPublicId, "concurrent-accept-token", "Concurrent Accepted Admin A"),
            () ->
                acceptanceStatus(
                    invitationPublicId, "concurrent-accept-token", "Concurrent Accepted Admin B"));

    assertConcurrentWinnerAndClosedLoser(responses, 201);
    assertAcceptedAccountGraph("concurrent-accepted-admin@example.com", 1);
    assertThat(terminalState(invitationPublicId))
        .containsEntry("accepted", true)
        .containsEntry("revoked", false);
  }

  /** 수락과 회수가 경쟁하면 한 transaction만 terminal state와 그에 대응하는 graph·event를 남깁니다. */
  @Test
  void concurrentAcceptanceAndRevocationLeaveOneTerminalState() throws Exception {
    UUID inviterPublicId =
        createAccount("accept-revoke-super@example.com", "accept-revoke-super@example.com");
    Long inviterAccountId = internalAccountId(inviterPublicId);
    UUID invitationPublicId =
        createInvitation(
            inviterAccountId,
            "accept-revoke-admin@example.com",
            tokenHasher.hash("accept-revoke-token"),
            futureExpiresAt());

    List<ConcurrentResponse> responses =
        runConcurrently(
            () ->
                acceptanceStatus(invitationPublicId, "accept-revoke-token", "Accept Revoke Admin"),
            () -> revocationStatus(invitationPublicId, inviterPublicId));

    assertThat(responses).extracting(ConcurrentResponse::status).contains(409);
    assertThat(responses.stream().filter(response -> response.status() != 409).toList())
        .singleElement()
        .extracting(ConcurrentResponse::status)
        .isIn(201, 204);
    assertThat(responses.stream().filter(response -> response.status() == 409).toList())
        .singleElement()
        .extracting(ConcurrentResponse::errorCode)
        .isEqualTo("ADMIN_INVITATION_ALREADY_CLOSED");

    Map<String, Object> terminalState = terminalState(invitationPublicId);
    boolean accepted = (boolean) terminalState.get("accepted");
    boolean revoked = (boolean) terminalState.get("revoked");
    assertThat(accepted).isNotEqualTo(revoked);

    int revokedEventCount =
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from auth_event
            where account_id = ?
              and event_type = 'ADMIN_INVITATION_REVOKED'
            """,
            Integer.class,
            inviterAccountId);

    assertAcceptedAccountGraph("accept-revoke-admin@example.com", accepted ? 1 : 0);
    assertThat(revokedEventCount).isEqualTo(revoked ? 1 : 0);
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

  private ConcurrentResponse acceptanceStatus(
      UUID invitationPublicId, String token, String displayName) throws Exception {
    var response =
        mockMvc
            .perform(
                post("/api/v1/admin/invitations/{invitationId}/acceptances", invitationPublicId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "token": "%s",
                      "password": "correct horse battery staple",
                      "passwordConfirm": "correct horse battery staple",
                      "displayName": "%s"
                    }
                    """
                            .formatted(token, displayName)))
            .andReturn()
            .getResponse();
    return concurrentResponse(response.getStatus(), response.getContentAsString());
  }

  private ConcurrentResponse revocationStatus(UUID invitationPublicId, UUID revokerPublicId)
      throws Exception {
    var response =
        mockMvc
            .perform(
                delete("/api/v1/admin/invitations/{invitationId}", invitationPublicId)
                    .with(superAdminFullSessionJwt(revokerPublicId)))
            .andReturn()
            .getResponse();
    return concurrentResponse(response.getStatus(), response.getContentAsString());
  }

  private List<ConcurrentResponse> runConcurrently(
      Callable<ConcurrentResponse> first, Callable<ConcurrentResponse> second) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<ConcurrentResponse> firstResult = executor.submit(awaitStart(ready, start, first));
      Future<ConcurrentResponse> secondResult = executor.submit(awaitStart(ready, start, second));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  private Callable<ConcurrentResponse> awaitStart(
      CountDownLatch ready, CountDownLatch start, Callable<ConcurrentResponse> request) {
    return () -> {
      ready.countDown();
      if (!start.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("동시 요청 시작 신호를 기다리지 못했습니다.");
      }
      return request.call();
    };
  }

  private Map<String, Object> terminalState(UUID invitationPublicId) {
    return jdbcTemplate.queryForMap(
        """
        select accepted_at is not null as accepted,
               revoked_at is not null as revoked
        from admin_invitation
        where public_id = ?
        """,
        invitationPublicId);
  }

  private ConcurrentResponse concurrentResponse(int status, String body) throws Exception {
    if (status < 400) {
      return new ConcurrentResponse(status, null);
    }
    return new ConcurrentResponse(status, JSON_MAPPER.readTree(body).get("code").stringValue());
  }

  private void assertConcurrentWinnerAndClosedLoser(
      List<ConcurrentResponse> responses, int winnerStatus) {
    assertThat(responses)
        .extracting(ConcurrentResponse::status)
        .containsExactlyInAnyOrder(winnerStatus, 409);
    assertThat(responses.stream().filter(response -> response.status() == 409).toList())
        .singleElement()
        .extracting(ConcurrentResponse::errorCode)
        .isEqualTo("ADMIN_INVITATION_ALREADY_CLOSED");
  }

  private void assertAcceptedAccountGraph(String emailNormalized, int expectedCount) {
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from account where email_normalized = ?",
                Integer.class,
                emailNormalized))
        .isEqualTo(expectedCount);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from account_profile profile
                join account on account.id = profile.account_id
                where account.email_normalized = ?
                """,
                Integer.class,
                emailNormalized))
        .isEqualTo(expectedCount);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from account_password_credential credential
                join account on account.id = credential.account_id
                where account.email_normalized = ?
                """,
                Integer.class,
                emailNormalized))
        .isEqualTo(expectedCount);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from account_role_assignment assignment
                join account on account.id = assignment.account_id
                where account.email_normalized = ?
                """,
                Integer.class,
                emailNormalized))
        .isEqualTo(expectedCount);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event event
                join account on account.id = event.account_id
                where account.email_normalized = ?
                  and event.event_type = 'ADMIN_INVITATION_ACCEPTED'
                """,
                Integer.class,
                emailNormalized))
        .isEqualTo(expectedCount);
  }

  private record ConcurrentResponse(int status, String errorCode) {}

  private void assertCreatedEventMetadata(
      Long inviterAccountId,
      UUID invitationPublicId,
      String targetEmailNormalized,
      boolean previousInvitationRevoked) {
    Map<String, Object> createdEvent =
        jdbcTemplate.queryForMap(
            """
            select (select count(*) from jsonb_object_keys(metadata)) as key_count,
                   metadata ->> 'invitationPublicId' as invitation_public_id,
                   metadata ->> 'targetEmailHash' as target_email_hash,
                   metadata ->> 'roleCode' as role_code,
                   metadata ->> 'previousInvitationRevoked' as previous_invitation_revoked
            from auth_event
            where account_id = ?
              and event_type = 'ADMIN_INVITATION_CREATED'
            order by id desc
            limit 1
            """,
            inviterAccountId);

    assertThat(createdEvent)
        .containsEntry("key_count", 4L)
        .containsEntry("invitation_public_id", invitationPublicId.toString())
        .containsEntry("target_email_hash", sha256Hex(targetEmailNormalized))
        .containsEntry("role_code", "ROLE_ADMIN")
        .containsEntry("previous_invitation_revoked", Boolean.toString(previousInvitationRevoked));
  }

  private String sha256Hex(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JVM이 SHA-256을 제공해야 합니다.", exception);
    }
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
