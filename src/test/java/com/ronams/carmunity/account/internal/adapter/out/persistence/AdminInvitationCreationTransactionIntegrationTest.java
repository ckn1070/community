package com.ronams.carmunity.account.internal.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ronams.carmunity.account.internal.application.command.InviteAdminCommand;
import com.ronams.carmunity.account.internal.application.port.in.InviteAdminUseCase;
import com.ronams.carmunity.account.internal.application.service.AdminInvitationTokenHasher;
import com.ronams.carmunity.notification.api.AdminInvitationNotificationSender;
import com.ronams.carmunity.securityaudit.api.SecurityAuditAppender;
import com.ronams.carmunity.securityaudit.api.SecurityAuditRecord;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 관리자 초대와 생성 감사 이벤트의 PostgreSQL transaction 원자성을 검증합니다. */
@SpringBootTest
class AdminInvitationCreationTransactionIntegrationTest extends PostgreSqlIntegrationTestSupport {

  @Autowired private InviteAdminUseCase inviteAdminUseCase;
  @Autowired private AdminInvitationTokenHasher tokenHasher;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoSpyBean private SecurityAuditAppender securityAuditAppender;
  @MockitoBean private AdminInvitationNotificationSender notificationSender;

  /** 생성 감사 저장이 실패하면 기존 open 초대 회수와 새 초대 저장을 모두 rollback합니다. */
  @Test
  void rollsBackInvitationChangesWhenCreatedEventWriteFails() {
    UUID inviterPublicId = createAccount("atomic-super@example.com");
    Long inviterAccountId = internalAccountId(inviterPublicId);
    UUID previousInvitationPublicId =
        createInvitation(
            inviterAccountId, "atomic-admin@example.com", tokenHasher.hash("previous-raw-token"));
    doThrow(new DataIntegrityViolationException("created event write failure"))
        .when(securityAuditAppender)
        .append(any(SecurityAuditRecord.class));
    InviteAdminCommand command =
        new InviteAdminCommand(inviterPublicId, "atomic-admin@example.com", "ROLE_ADMIN");

    assertThatThrownBy(() -> inviteAdminUseCase.execute(command))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from admin_invitation where email_normalized = ?",
                Integer.class,
                "atomic-admin@example.com"))
        .isOne();
    assertThat(
            jdbcTemplate.queryForObject(
                "select revoked_at is null from admin_invitation where public_id = ?",
                Boolean.class,
                previousInvitationPublicId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from auth_event where account_public_id = (select public_id from account where id = ?) and event_type = 'ADMIN_INVITATION_CREATED'",
                Integer.class,
                inviterAccountId))
        .isZero();
    verifyNoInteractions(notificationSender);
  }

  private UUID createAccount(String email) {
    UUID publicId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into account (public_id, email, email_normalized, status, email_verified_at) values (?, ?, ?, 'ACTIVE', now())",
        publicId,
        email,
        email);
    return publicId;
  }

  private UUID createInvitation(Long inviterAccountId, String email, String tokenHash) {
    UUID publicId = UUID.randomUUID();
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
        publicId,
        email,
        email,
        roleId(),
        tokenHash,
        inviterAccountId,
        Timestamp.from(Instant.now().plusSeconds(86_400)));
    return publicId;
  }

  private Long internalAccountId(UUID publicId) {
    return jdbcTemplate.queryForObject(
        "select id from account where public_id = ?", Long.class, publicId);
  }

  private Long roleId() {
    return jdbcTemplate.queryForObject(
        "select id from auth_role where code = 'ROLE_ADMIN'", Long.class);
  }
}
