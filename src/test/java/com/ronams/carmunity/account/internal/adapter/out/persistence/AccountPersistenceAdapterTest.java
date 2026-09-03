package com.ronams.carmunity.account.internal.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.api.AccountAuthStatus;
import com.ronams.carmunity.account.api.AccountAuthSubject;
import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.domain.Account;
import com.ronams.carmunity.account.internal.domain.DisplayName;
import com.ronams.carmunity.account.internal.domain.EmailAddress;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * account persistence adapter가 여러 테이블을 하나의 가입 저장 단위로 다루는지 검증합니다.
 *
 * <p>DB unique constraint는 동시 가입 race condition의 마지막 방어선이므로 application 오류 코드로 변환되어야 합니다.
 */
@SpringBootTest
class AccountPersistenceAdapterTest extends PostgreSqlIntegrationTestSupport {

  private static final Instant NOW = Instant.parse("2099-07-01T00:00:00Z");

  @Autowired private AccountPersistenceAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** Account aggregate 저장은 profile, password credential, role, auth event를 하나의 저장 단위로 묶습니다. */
  @Test
  void savesAccountProfilePasswordCredentialMemberRoleAndSignUpEvent() {
    Account account = account("persistence@example.com", "Persistence User");

    adapter.save(account);

    Long accountId =
        jdbcTemplate.queryForObject(
            "select id from account where public_id = ?", Long.class, account.publicId());

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from account where id = ?", String.class, accountId))
        .isEqualTo("PENDING");
    assertThat(
            jdbcTemplate.queryForObject(
                "select display_name_normalized from account_profile where account_id = ?",
                String.class,
                accountId))
        .isEqualTo("persistence user");
    assertThat(
            jdbcTemplate.queryForObject(
                "select password_hash from account_password_credential where account_id = ?",
                String.class,
                accountId))
        .isEqualTo("{argon2}hashed-password");
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
                where account_public_id = ? and event_type = 'ACCOUNT_SIGN_UP' and result = 'SUCCESS'
                """,
                Integer.class,
                account.publicId()))
        .isOne();
  }

  /** 최초 최고 관리자 저장은 ACTIVE 계정, SUPER_ADMIN role, 강제 비밀번호 변경, 감사 이벤트를 묶습니다. */
  @Test
  void savesInitialSuperAdminWithPasswordChangeRequiredAndBootstrapAuditEvents() {
    Account account = superAdminAccount("bootstrap-admin@example.com", "Bootstrap Admin");

    assertThat(adapter.saveIfNoActiveSuperAdmin(account)).contains(account);

    Long accountId =
        jdbcTemplate.queryForObject(
            "select id from account where public_id = ?", Long.class, account.publicId());

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from account where id = ?", String.class, accountId))
        .isEqualTo("ACTIVE");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select password_change_required
                from account_password_credential
                where account_id = ?
                """,
                Boolean.class,
                accountId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select r.code
                from account_role_assignment a
                join auth_role r on r.id = a.role_id
                where a.account_id = ? and a.revoked_at is null
                """,
                String.class,
                accountId))
        .isEqualTo("ROLE_SUPER_ADMIN");
    assertThat(
            jdbcTemplate.queryForList(
                """
                select event_type
                from auth_event
                where account_public_id = ? and result = 'SUCCESS'
                order by event_type
                """,
                String.class,
                account.publicId()))
        .containsExactly("PASSWORD_CHANGE_REQUIREMENT", "ROLE_CHANGE");
    assertThat(
            jdbcTemplate.queryForList(
                """
                select reason_code
                from auth_event
                where account_public_id = ?
                order by reason_code
                """,
                String.class,
                account.publicId()))
        .containsExactly("INITIAL_ADMIN_BOOTSTRAP", "SUPER_ADMIN_BOOTSTRAP");
    assertThat(adapter.findByPublicId(account.publicId()).orElseThrow().passwordChangeRequired())
        .isTrue();
    assertThat(
            adapter
                .findByNormalizedEmail("bootstrap-admin@example.com")
                .orElseThrow()
                .passwordChangeRequired())
        .isTrue();
  }

  /** 활성 최고 관리자가 있으면 저장 transaction 안의 재확인으로 두 번째 최고 관리자를 만들지 않습니다. */
  @Test
  void returnsEmptyWithoutSavingWhenActiveSuperAdminAlreadyExists() {
    Account first = superAdminAccount("first-super-admin@example.com", "First Super Admin");
    Account second = superAdminAccount("second-super-admin@example.com", "Second Super Admin");
    adapter.saveIfNoActiveSuperAdmin(first);

    assertThat(adapter.activeSuperAdminExists()).isTrue();
    assertThat(adapter.saveIfNoActiveSuperAdmin(second)).isEmpty();

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from account where email_normalized = ?",
                Integer.class,
                "second-super-admin@example.com"))
        .isZero();
  }

  /** 비밀번호 변경 성공은 credential 갱신, 강제 변경 해제, refresh token 폐기, 성공 감사 이벤트를 함께 저장합니다. */
  @Test
  void changesPasswordAndRevokesRefreshTokensWithAuditEvent() {
    Account account = account("change-password@example.com", "Change Password");
    adapter.save(account);
    Long accountId = internalId(account.publicId());
    jdbcTemplate.update(
        "update account_password_credential set password_change_required = true where account_id = ?",
        accountId);
    createRefreshToken(account.publicId(), "a".repeat(64));

    adapter.changePassword(account.publicId(), PasswordHash.from("{argon2}new-password-hash"), NOW);

    assertThat(
            jdbcTemplate.queryForObject(
                "select password_hash from account_password_credential where account_id = ?",
                String.class,
                accountId))
        .isEqualTo("{argon2}new-password-hash");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select password_change_required
                from account_password_credential
                where account_id = ?
                """,
                Boolean.class,
                accountId))
        .isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "select revoked_reason from refresh_token where account_public_id = ?",
                String.class,
                account.publicId()))
        .isEqualTo("PASSWORD_CHANGED");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select reason_code
                from auth_event
                where account_public_id = ?
                  and event_type = 'PASSWORD_CHANGE'
                  and result = 'SUCCESS'
                """,
                String.class,
                account.publicId()))
        .isNull();
  }

  /** 비밀번호 재설정 성공은 PENDING 상태를 유지하면서 credential, refresh token, reset 감사 이벤트를 갱신합니다. */
  @Test
  void resetsPasswordAndKeepsPendingStatusWithAuditEvent() {
    Account account = account("reset-password@example.com", "Reset Password");
    adapter.save(account);
    Long accountId = internalId(account.publicId());
    jdbcTemplate.update(
        "update account_password_credential set password_change_required = true where account_id = ?",
        accountId);
    createRefreshToken(account.publicId(), "b".repeat(64));

    adapter.resetPassword(accountId, PasswordHash.from("{argon2}reset-password-hash"), NOW);

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from account where id = ?", String.class, accountId))
        .isEqualTo("PENDING");
    assertThat(
            jdbcTemplate.queryForObject(
                "select password_hash from account_password_credential where account_id = ?",
                String.class,
                accountId))
        .isEqualTo("{argon2}reset-password-hash");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select password_change_required
                from account_password_credential
                where account_id = ?
                """,
                Boolean.class,
                accountId))
        .isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "select revoked_reason from refresh_token where account_public_id = ?",
                String.class,
                account.publicId()))
        .isEqualTo("PASSWORD_CHANGED");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from auth_event
                where account_public_id = ?
                  and event_type = 'PASSWORD_RESET_COMPLETED'
                  and result = 'SUCCESS'
                """,
                Integer.class,
                account.publicId()))
        .isOne();
  }

  /** current password 불일치는 password hash 변경 없이 실패 감사 이벤트만 남깁니다. */
  @Test
  void recordsPasswordChangeFailureEventForCurrentPasswordMismatch() {
    Account account = account("change-password-fail@example.com", "Change Password Fail");
    adapter.save(account);
    Long accountId = internalId(account.publicId());

    adapter.recordPasswordChangeFailure(account.publicId(), NOW);

    assertThat(
            jdbcTemplate.queryForObject(
                "select password_hash from account_password_credential where account_id = ?",
                String.class,
                accountId))
        .isEqualTo("{argon2}hashed-password");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select reason_code
                from auth_event
                where account_public_id = ?
                  and event_type = 'PASSWORD_CHANGE'
                  and result = 'FAILURE'
                """,
                String.class,
                account.publicId()))
        .isEqualTo("CURRENT_PASSWORD_MISMATCH");
  }

  /** auth 모듈은 account internal 구현 대신 공개 계약으로 상태, 활성 role, 비밀번호 변경 flag를 조회합니다. */
  @Test
  void findsAuthSubjectByPublicIdWithStatusAndActiveRoles() {
    Account account = account("auth-subject@example.com", "Auth Subject");
    adapter.save(account);

    AccountAuthSubject subject = adapter.findByPublicId(account.publicId()).orElseThrow();

    assertThat(subject.publicId()).isEqualTo(account.publicId());
    assertThat(subject.status()).isEqualTo(AccountAuthStatus.PENDING);
    assertThat(subject.roleCodes()).containsExactly("ROLE_MEMBER");
    assertThat(subject.passwordChangeRequired()).isFalse();
  }

  /** DB email unique constraint 이름은 동시 가입 충돌을 EMAIL_ALREADY_USED로 바꾸는 adapter 계약입니다. */
  @Test
  void mapsDuplicateNormalizedEmailConstraintToConflictCode() {
    adapter.save(account("duplicate-email@example.com", "Duplicate Email A"));

    assertThatThrownBy(
            () -> adapter.save(account("DUPLICATE-EMAIL@example.com", "Duplicate Email B")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.EMAIL_ALREADY_USED));
  }

  /** display name constraint 충돌은 오류 코드 매핑과 account insert rollback을 함께 확인합니다. */
  @Test
  void mapsDuplicateDisplayNameConstraintToConflictCodeAndRollsBackAccountInsert() {
    adapter.save(account("duplicate-display-a@example.com", "Same Display"));

    assertThatThrownBy(
            () -> adapter.save(account("duplicate-display-b@example.com", "Same   Display")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.DISPLAY_NAME_ALREADY_USED));

    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from account where email_normalized = ?",
                Integer.class,
                "duplicate-display-b@example.com"))
        .isZero();
  }

  private Account account(String email, String displayName) {
    return Account.registerMember(
        UUID.randomUUID(),
        EmailAddress.from(email),
        DisplayName.from(displayName),
        PasswordHash.from("{argon2}hashed-password"));
  }

  private Account superAdminAccount(String email, String displayName) {
    return Account.bootstrapSuperAdmin(
        UUID.randomUUID(),
        EmailAddress.from(email),
        DisplayName.from(displayName),
        PasswordHash.from("{argon2}hashed-password"));
  }

  private Long internalId(UUID publicId) {
    return jdbcTemplate.queryForObject(
        "select id from account where public_id = ?", Long.class, publicId);
  }

  private void createRefreshToken(UUID accountPublicId, String tokenHash) {
    jdbcTemplate.update(
        """
        insert into refresh_token (account_public_id, token_hash, family_id, issued_at, expires_at)
        values (?, ?, ?::uuid, ?, ?)
        """,
        accountPublicId,
        tokenHash,
        UUID.randomUUID().toString(),
        java.sql.Timestamp.from(NOW.minusSeconds(60)),
        java.sql.Timestamp.from(NOW.plusSeconds(3600)));
  }
}
