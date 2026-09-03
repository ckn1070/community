package com.ronams.carmunity.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Flyway account/auth schema가 도메인 불변조건과 운영 조회용 index를 실제 PostgreSQL에서 보장하는지 검증합니다.
 *
 * <p>migration SQL의 constraint 이름과 partial index는 application 예외 매핑과 adapter 조회 경로가 의존하는 계약입니다.
 */
@SpringBootTest
class AccountAuthSchemaMigrationTest extends PostgreSqlIntegrationTestSupport {

  @Autowired private JdbcTemplate jdbcTemplate;

  /** 가입과 권한 부여 흐름이 의존하는 기본 역할 seed와 권한 계층 삽입 순서를 고정합니다. */
  @Test
  void seedsDefaultAuthRoles() {
    List<String> roleCodes =
        jdbcTemplate.queryForList("select code from auth_role order by id", String.class);

    assertEquals(List.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_MEMBER"), roleCodes);
  }

  /** role 참조는 surrogate id로 연결하고, code는 업무 식별자로 unique해야 합니다. */
  @Test
  void usesAuthRoleIdAsPrimaryKeyAndKeepsCodeUnique() {
    List<String> primaryKeyColumns =
        jdbcTemplate.queryForList(
            """
				select kcu.column_name
				from information_schema.table_constraints tc
				join information_schema.key_column_usage kcu
				  on tc.constraint_schema = kcu.constraint_schema
				 and tc.constraint_name = kcu.constraint_name
				where tc.table_schema = 'public'
				  and tc.table_name = 'auth_role'
				  and tc.constraint_type = 'PRIMARY KEY'
				order by kcu.ordinal_position
				""",
            String.class);

    assertEquals(List.of("id"), primaryKeyColumns);
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
						insert into auth_role (code, name)
						values (?, ?)
						""",
                "ROLE_MEMBER",
                "Duplicate member"));
  }

  /** 정규화된 email 기준 중복을 DB constraint가 마지막 방어선으로 거부하는지 확인합니다. */
  @Test
  void rejectsDuplicateNormalizedEmail() {
    createAccount("duplicate-email-a@example.com", "duplicate@example.com");

    assertThrows(
        DataIntegrityViolationException.class,
        () -> createAccount("duplicate-email-b@example.com", "duplicate@example.com"));
  }

  /** domain enum 밖의 계정 상태가 persistence 계층을 통해 유입되지 못하게 고정합니다. */
  @Test
  void rejectsInvalidAccountStatus() {
    UUID publicId = UUID.randomUUID();

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
						insert into account (public_id, email, email_normalized, status)
							values (?, ?, ?, ?)
							""",
                publicId,
                "invalid-status@example.com",
                "invalid-status@example.com",
                "BLOCKED"));
  }

  /** 감사 timestamp는 수정 시각이 생성 시각보다 빠를 수 없다는 불변조건을 가집니다. */
  @Test
  void rejectsUpdatedAtBeforeCreatedAt() {
    UUID publicId = UUID.randomUUID();

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
						insert into account (
						    public_id,
						    email,
						    email_normalized,
						    status,
						    created_at,
						    updated_at
						)
							values (?, ?, ?, 'ACTIVE', now(), now() - interval '1 second')
							""",
                publicId,
                "invalid-updated-at@example.com",
                "invalid-updated-at@example.com"));
  }

  /** active role만 중복 금지하고 revoke 이후 재부여를 허용하는 partial unique index 정책을 검증합니다. */
  @Test
  void preventsDuplicateActiveRoleAssignmentAndAllowsReassignmentAfterRevocation() {
    Long accountId = createAccount("role-assignment@example.com", "role-assignment@example.com");
    Long roleId = findRoleId("ROLE_MEMBER");

    jdbcTemplate.update(
        """
				insert into account_role_assignment (account_id, role_id)
				values (?, ?)
				""",
        accountId,
        roleId);

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
						insert into account_role_assignment (account_id, role_id)
						values (?, ?)
						""",
                accountId,
                roleId));

    jdbcTemplate.update(
        """
				update account_role_assignment
				set revoked_at = now()
				where account_id = ? and role_id = ?
				""",
        accountId,
        roleId);

    int inserted =
        jdbcTemplate.update(
            """
				insert into account_role_assignment (account_id, role_id)
				values (?, ?)
				""",
            accountId,
            roleId);

    assertEquals(1, inserted);
  }

  /** refresh token은 원문이 아니라 hash 기준으로 전역 중복을 막아야 합니다. */
  @Test
  void rejectsDuplicateRefreshTokenHash() {
    Long accountId = createAccount("refresh-token@example.com", "refresh-token@example.com");
    String tokenHash = "a".repeat(64);
    UUID firstFamilyId = UUID.randomUUID();
    UUID secondFamilyId = UUID.randomUUID();
    UUID accountPublicId = publicId(accountId);

    jdbcTemplate.update(
        """
					insert into refresh_token (account_public_id, token_hash, family_id, expires_at)
					values (?, ?, ?, now() + interval '30 days')
					""",
        accountPublicId,
        tokenHash,
        firstFamilyId);

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
						insert into refresh_token (account_public_id, token_hash, family_id, expires_at)
						values (?, ?, ?, now() + interval '30 days')
							""",
                accountPublicId,
                tokenHash,
                secondFamilyId));
  }

  /** account 검증 token 저장소는 purpose/channel 기반 생명주기와 원문 token 미저장을 DB 계약으로 보장합니다. */
  @Test
  void createsAccountVerificationTokenSchema() {
    List<String> columns =
        jdbcTemplate.queryForList(
            """
				select column_name
				from information_schema.columns
				where table_schema = 'public'
				  and table_name = 'account_verification_token'
				order by ordinal_position
				""",
            String.class);

    assertEquals(
        List.of(
            "id",
            "account_id",
            "purpose",
            "delivery_channel",
            "recipient_normalized",
            "token_hash",
            "issued_at",
            "expires_at",
            "consumed_at",
            "revoked_at",
            "last_sent_at",
            "send_count",
            "created_at",
            "updated_at"),
        columns);

    Set<String> constraintNames =
        Set.copyOf(
            jdbcTemplate.queryForList(
                """
				select constraint_name
				from information_schema.table_constraints
				where table_schema = 'public'
				  and table_name = 'account_verification_token'
				""",
                String.class));

    assertTrue(constraintNames.contains("pk_account_verification_token"));
    assertTrue(constraintNames.contains("fk_account_verification_token_account"));
    assertTrue(constraintNames.contains("uk_account_verification_token_hash"));
    assertTrue(constraintNames.contains("chk_account_verification_token_purpose"));
    assertTrue(constraintNames.contains("chk_account_verification_token_delivery_channel"));
    assertTrue(constraintNames.contains("chk_account_verification_token_hash"));
    assertTrue(constraintNames.contains("chk_account_verification_token_expires_at"));
    assertTrue(constraintNames.contains("chk_account_verification_token_last_sent_at"));
    assertTrue(constraintNames.contains("chk_account_verification_token_send_count"));
    assertTrue(constraintNames.contains("chk_account_verification_token_consumed_at"));
    assertTrue(constraintNames.contains("chk_account_verification_token_revoked_at"));
    assertTrue(constraintNames.contains("chk_account_verification_token_terminal_state"));
    assertTrue(constraintNames.contains("chk_account_verification_token_updated_at"));
  }

  /** 관리자 초대 schema는 token 원문 미저장, 역할 FK, terminal state 전이를 DB 계약으로 고정합니다. */
  @Test
  void createsAdminInvitationSchema() {
    List<String> columns =
        jdbcTemplate.queryForList(
            """
				select column_name
				from information_schema.columns
				where table_schema = 'public'
				  and table_name = 'admin_invitation'
				order by ordinal_position
				""",
            String.class);

    assertEquals(
        List.of(
            "id",
            "public_id",
            "email",
            "email_normalized",
            "role_id",
            "token_hash",
            "invited_by_account_id",
            "accepted_account_id",
            "revoked_by_account_id",
            "issued_at",
            "expires_at",
            "accepted_at",
            "revoked_at",
            "created_at",
            "updated_at"),
        columns);

    Set<String> constraintNames =
        Set.copyOf(
            jdbcTemplate.queryForList(
                """
				select constraint_name
				from information_schema.table_constraints
				where table_schema = 'public'
				  and table_name = 'admin_invitation'
				""",
                String.class));

    assertTrue(constraintNames.contains("pk_admin_invitation"));
    assertTrue(constraintNames.contains("uk_admin_invitation_public_id"));
    assertTrue(constraintNames.contains("uk_admin_invitation_token_hash"));
    assertTrue(constraintNames.contains("fk_admin_invitation_role"));
    assertTrue(constraintNames.contains("fk_admin_invitation_invited_by_account"));
    assertTrue(constraintNames.contains("fk_admin_invitation_accepted_account"));
    assertTrue(constraintNames.contains("fk_admin_invitation_revoked_by_account"));
    assertTrue(constraintNames.contains("chk_admin_invitation_token_hash"));
    assertTrue(constraintNames.contains("chk_admin_invitation_expires_at"));
    assertTrue(constraintNames.contains("chk_admin_invitation_accepted_at"));
    assertTrue(constraintNames.contains("chk_admin_invitation_revoked_at"));
    assertTrue(constraintNames.contains("chk_admin_invitation_terminal_state"));
    assertTrue(constraintNames.contains("chk_admin_invitation_accepted_account"));
    assertTrue(constraintNames.contains("chk_admin_invitation_revoked_actor"));
    assertTrue(constraintNames.contains("chk_admin_invitation_updated_at"));
  }

  /** 같은 email의 open 관리자 초대는 하나만 허용하고, 폐기 이후 재초대를 허용합니다. */
  @Test
  void preventsDuplicateOpenAdminInvitationAndAllowsReinviteAfterRevocation() {
    Long inviterId = createAccount("inviter@example.com", "inviter@example.com");
    Long roleId = findRoleId("ROLE_ADMIN");

    createAdminInvitation(inviterId, roleId, "target@example.com", "a".repeat(64));
    String duplicateTokenHash = "b".repeat(64);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> createAdminInvitation(inviterId, roleId, "target@example.com", duplicateTokenHash));

    jdbcTemplate.update(
        """
				update admin_invitation
				set revoked_at = now(), revoked_by_account_id = ?, updated_at = now()
				where email_normalized = ?
				""",
        inviterId,
        "target@example.com");

    int inserted = createAdminInvitation(inviterId, roleId, "target@example.com", "c".repeat(64));

    assertEquals(1, inserted);
  }

  /** open token은 account/purpose/channel 기준 하나만 허용하고, 폐기 후 재발급을 허용해야 합니다. */
  @Test
  void preventsDuplicateOpenAccountVerificationTokenAndAllowsReissueAfterRevocation() {
    Long accountId =
        createAccount("verification-token@example.com", "verification-token@example.com");

    createVerificationToken(accountId, "1".repeat(64));
    String duplicateTokenHash = "2".repeat(64);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> createVerificationToken(accountId, duplicateTokenHash));

    jdbcTemplate.update(
        """
				update account_verification_token
				set revoked_at = now(), updated_at = now()
				where account_id = ?
				  and purpose = 'EMAIL_VERIFICATION'
				  and delivery_channel = 'EMAIL'
				""",
        accountId);

    int inserted = createVerificationToken(accountId, "3".repeat(64));

    assertEquals(1, inserted);
  }

  /** password reset token은 같은 table의 PASSWORD_RESET purpose로 저장할 수 있어야 합니다. */
  @Test
  void allowsPasswordResetTokenPurposeAlongsideEmailVerificationPurpose() {
    Long accountId =
        createAccount("password-reset-token@example.com", "password-reset-token@example.com");

    int emailToken = createVerificationToken(accountId, "4".repeat(64), "EMAIL_VERIFICATION");
    int resetToken = createVerificationToken(accountId, "5".repeat(64), "PASSWORD_RESET");

    assertEquals(1, emailToken);
    assertEquals(1, resetToken);
  }

  /** 이메일 인증 감사 이벤트는 흐름 중심 event type과 result/reason 분리 taxonomy를 따릅니다. */
  @Test
  void allowsEmailVerificationAuthEvents() {
    Long accountId =
        createAccount(
            "email-verification-event@example.com", "email-verification-event@example.com");

    int requested = createAuthEvent(accountId, "EMAIL_VERIFICATION_REQUEST", "SUCCESS");
    int requestFailed = createAuthEvent(accountId, "EMAIL_VERIFICATION_REQUEST", "FAILURE");
    int verified = createAuthEvent(accountId, "EMAIL_VERIFICATION_CONFIRM", "SUCCESS");
    int verifyFailed =
        createAuthEventWithReason(
            accountId, "EMAIL_VERIFICATION_CONFIRM", "FAILURE", "TOKEN_INVALID");

    assertEquals(1, requested);
    assertEquals(1, requestFailed);
    assertEquals(1, verified);
    assertEquals(1, verifyFailed);
  }

  /** auth_event taxonomy는 흐름 이름만 type으로 허용하고, type/reason 중복과 비객체 metadata를 막습니다. */
  @Test
  void enforcesAuthEventTaxonomyConstraints() {
    Long accountId =
        createAccount("auth-event-taxonomy@example.com", "auth-event-taxonomy@example.com");

    int passwordLogin =
        createAuthEventWithReason(accountId, "PASSWORD_LOGIN", "FAILURE", "PASSWORD_MISMATCH");
    int tokenRefresh =
        createAuthEventWithReason(accountId, "TOKEN_REFRESH", "FAILURE", "TOKEN_REUSE_DETECTED");
    UUID accountPublicId = publicId(accountId);

    assertEquals(1, passwordLogin);
    assertEquals(1, tokenRefresh);
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            createAuthEventWithReason(accountId, "LOGIN_FAILURE", "FAILURE", "PASSWORD_MISMATCH"));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> createAuthEventWithReason(accountId, "TOKEN_REFRESH", "FAILURE", "TOKEN_REFRESH"));
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
				insert into auth_event (account_public_id, event_type, result, metadata)
				values (?, 'PASSWORD_LOGIN', 'SUCCESS', '[]'::jsonb)
				""",
                accountPublicId));
  }

  /** 관리자 초대 생성, 수락, 회수는 auth_event taxonomy 안에서 허용되어야 합니다. */
  @Test
  void allowsAdminInvitationAuthEvents() {
    Long accountId =
        createAccount("admin-invitation-event@example.com", "admin-invitation-event@example.com");

    int created = createAuthEvent(accountId, "ADMIN_INVITATION_CREATED", "SUCCESS");
    int accepted = createAuthEvent(accountId, "ADMIN_INVITATION_ACCEPTED", "SUCCESS");
    int revoked = createAuthEvent(accountId, "ADMIN_INVITATION_REVOKED", "SUCCESS");

    assertEquals(1, created);
    assertEquals(1, accepted);
    assertEquals(1, revoked);
  }

  /** 비밀번호 재설정 감사 이벤트는 요청 성공/실패와 완료 성공 event type을 허용해야 합니다. */
  @Test
  void allowsPasswordResetAuthEvents() {
    Long accountId =
        createAccount("password-reset-event@example.com", "password-reset-event@example.com");

    int requested = createAuthEvent(accountId, "PASSWORD_RESET_REQUESTED", "SUCCESS");
    int requestFailed = createAuthEvent(accountId, "PASSWORD_RESET_REQUESTED", "FAILURE");
    int completed = createAuthEvent(accountId, "PASSWORD_RESET_COMPLETED", "SUCCESS");

    assertEquals(1, requested);
    assertEquals(1, requestFailed);
    assertEquals(1, completed);
  }

  /** 권한, token 만료, 감사 이벤트 조회 경로가 기대하는 index 이름을 migration 계약으로 고정합니다. */
  @Test
  void createsExpectedIndexes() {
    Set<String> indexNames =
        Set.copyOf(
            jdbcTemplate.queryForList(
                """
				select indexname
				from pg_indexes
				where schemaname = 'public'
				""",
                String.class));

    assertTrue(indexNames.contains("uk_account_role_assignment_active_account_role"));
    assertTrue(indexNames.contains("idx_account_role_assignment_active_role"));
    assertTrue(indexNames.contains("uk_refresh_token_token_hash"));
    assertTrue(indexNames.contains("idx_refresh_token_active_account_public"));
    assertTrue(indexNames.contains("idx_refresh_token_family_id"));
    assertTrue(indexNames.contains("idx_refresh_token_expires_at"));
    assertTrue(indexNames.contains("idx_auth_event_account_public_occurred_at"));
    assertTrue(indexNames.contains("idx_auth_event_occurred_at"));
    assertTrue(indexNames.contains("idx_auth_event_identifier_occurred_at"));
    assertTrue(indexNames.contains("uk_account_verification_token_hash"));
    assertTrue(indexNames.contains("uk_account_verification_token_open_account_purpose_channel"));
    assertTrue(indexNames.contains("idx_account_verification_token_expires_at"));
    assertTrue(indexNames.contains("uk_admin_invitation_open_email"));
    assertTrue(indexNames.contains("idx_admin_invitation_expires_at"));
    assertTrue(indexNames.contains("idx_admin_invitation_invited_by_issued_at"));
  }

  /** IP 주소 컬럼은 문자열이 아니라 PostgreSQL inet 타입으로 저장해 DB 수준 형식 검증을 사용합니다. */
  @Test
  void usesPostgreSqlInetForIpAddressColumns() {
    List<String> udtNames =
        jdbcTemplate.queryForList(
            """
				select udt_name
				from information_schema.columns
				where table_schema = 'public'
				  and table_name in ('refresh_token', 'auth_event')
				  and column_name = 'ip_address'
				order by table_name
				""",
            String.class);

    assertEquals(List.of("inet", "inet"), udtNames);
  }

  /** 초기 schema는 Context 밖 내부 account id나 임시 호환 trigger 없이 public id 계약만 생성합니다. */
  @Test
  void createsFinalPublicIdentitySchemaWithoutTransitionArtifacts() {
    UUID publicId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into account (public_id, email, email_normalized, status)
        values (?, ?, ?, 'ACTIVE')
        """,
        publicId,
        "identity-final@example.com",
        "identity-final@example.com");

    jdbcTemplate.update(
        "insert into auth_event (account_public_id, event_type, result) values (?, 'PASSWORD_LOGIN', 'SUCCESS')",
        publicId);

    jdbcTemplate.update(
        """
        insert into refresh_token (
            account_public_id, token_hash, family_id, issued_at, expires_at
        ) values (?, ?, ?, now(), now() + interval '1 day')
        """,
        publicId,
        "9".repeat(64),
        UUID.randomUUID());
    List<String> identityColumns =
        jdbcTemplate.queryForList(
            """
            select table_name || '.' || column_name || '.' || is_nullable
            from information_schema.columns
            where table_schema = 'public'
              and table_name in ('auth_event', 'refresh_token')
              and column_name in ('account_id', 'account_public_id')
            order by table_name, column_name
            """,
            String.class);
    assertEquals(
        List.of("auth_event.account_public_id.YES", "refresh_token.account_public_id.NO"),
        identityColumns);

    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.triggers
            where event_object_schema = 'public'
              and event_object_table in ('auth_event', 'refresh_token')
            """,
            Integer.class));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            """
            select count(*)
            from pg_proc
            where proname in (
                'sync_auth_event_account_identity',
                'sync_refresh_token_account_identity'
            )
            """,
            Integer.class));
  }

  private Long createAccount(String email, String normalizedEmail) {
    return jdbcTemplate.queryForObject(
        """
				insert into account (public_id, email, email_normalized, status)
				values (?, ?, ?, 'ACTIVE')
				returning id
				""",
        Long.class,
        UUID.randomUUID(),
        email,
        normalizedEmail);
  }

  private Long findRoleId(String roleCode) {
    return jdbcTemplate.queryForObject(
        "select id from auth_role where code = ?", Long.class, roleCode);
  }

  private int createVerificationToken(Long accountId, String tokenHash) {
    return createVerificationToken(accountId, tokenHash, "EMAIL_VERIFICATION");
  }

  private int createVerificationToken(Long accountId, String tokenHash, String purpose) {
    return jdbcTemplate.update(
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
				values (?, ?, 'EMAIL', ?, ?, now() + interval '1 day', now())
				""",
        accountId,
        purpose,
        "verification-token@example.com",
        tokenHash);
  }

  private int createAuthEvent(Long accountId, String eventType, String result) {
    return jdbcTemplate.update(
        """
				insert into auth_event (account_public_id, event_type, result)
				values (?, ?, ?)
				""",
        publicId(accountId),
        eventType,
        result);
  }

  private int createAuthEventWithReason(
      Long accountId, String eventType, String result, String reasonCode) {
    return jdbcTemplate.update(
        """
				insert into auth_event (account_public_id, event_type, result, reason_code)
				values (?, ?, ?, ?)
				""",
        publicId(accountId),
        eventType,
        result,
        reasonCode);
  }

  private UUID publicId(Long accountId) {
    return jdbcTemplate.queryForObject(
        "select public_id from account where id = ?", UUID.class, accountId);
  }

  private int createAdminInvitation(
      Long invitedByAccountId, Long roleId, String normalizedEmail, String tokenHash) {
    return jdbcTemplate.update(
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
				values (?, ?, ?, ?, ?, ?, now(), now() + interval '1 day')
				""",
        UUID.randomUUID(),
        normalizedEmail,
        normalizedEmail,
        roleId,
        tokenHash,
        invitedByAccountId);
  }
}
