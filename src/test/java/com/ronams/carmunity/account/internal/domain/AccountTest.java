package com.ronams.carmunity.account.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Account aggregate가 자체 가입 시 생성해야 하는 내부 graph와 domain event 계약을 검증합니다. */
class AccountTest {

  private static final UUID PUBLIC_ID = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final Instant VERIFIED_AT = Instant.parse("2026-07-06T12:00:00Z");

  /** 자체 가입은 PENDING 계정, profile, password credential, ROLE_MEMBER, 가입 event를 한 번에 만듭니다. */
  @Test
  void registerMemberCreatesPendingAccountWithProfilePasswordCredentialAndMemberRole() {
    Account account =
        Account.registerMember(
            PUBLIC_ID,
            EmailAddress.from("USER@example.com"),
            DisplayName.from("Road Runner"),
            PasswordHash.from("{argon2}hashed-password"));

    assertThat(account.publicId()).isEqualTo(PUBLIC_ID);
    assertThat(account.email().value()).isEqualTo("user@example.com");
    assertThat(account.status()).isEqualTo(AccountStatus.PENDING);
    assertThat(account.profile().displayName().value()).isEqualTo("Road Runner");
    assertThat(account.passwordCredential().passwordHash().value())
        .isEqualTo("{argon2}hashed-password");
    assertThat(account.roleAssignments())
        .extracting(RoleAssignment::roleCode)
        .containsExactly(RoleCode.ROLE_MEMBER);
    assertThat(account.domainEvents()).containsExactly(new AccountRegisteredEvent(PUBLIC_ID));
  }

  /** 최초 최고 관리자는 ACTIVE 상태, SUPER_ADMIN role, 비밀번호 변경 강제 credential을 한 번에 만듭니다. */
  @Test
  void bootstrapSuperAdminCreatesActiveAccountWithSuperAdminRoleAndPasswordChangeRequired() {
    Account account =
        Account.bootstrapSuperAdmin(
            PUBLIC_ID,
            EmailAddress.from("ADMIN@example.com"),
            DisplayName.from("Initial Admin"),
            PasswordHash.from("{argon2}hashed-password"));

    assertThat(account.publicId()).isEqualTo(PUBLIC_ID);
    assertThat(account.email().value()).isEqualTo("admin@example.com");
    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.emailVerifiedAt()).isEmpty();
    assertThat(account.profile().displayName().value()).isEqualTo("Initial Admin");
    assertThat(account.passwordCredential().passwordHash().value())
        .isEqualTo("{argon2}hashed-password");
    assertThat(account.passwordCredential().passwordChangeRequired()).isTrue();
    assertThat(account.roleAssignments())
        .extracting(RoleAssignment::roleCode)
        .containsExactly(RoleCode.ROLE_SUPER_ADMIN);
    assertThat(account.domainEvents()).isEmpty();
  }

  /** 관리자 초대 수락은 이메일 인증을 끝낸 ACTIVE 관리자 account graph를 한 번에 만듭니다. */
  @Test
  void acceptAdminInvitationCreatesActiveVerifiedAdminAccount() {
    Account account =
        Account.acceptAdminInvitation(
            PUBLIC_ID,
            EmailAddress.from("ADMIN@example.com"),
            DisplayName.from("Invited Admin"),
            PasswordHash.from("{argon2}hashed-password"),
            RoleCode.ROLE_ADMIN,
            VERIFIED_AT);

    assertThat(account.publicId()).isEqualTo(PUBLIC_ID);
    assertThat(account.email().value()).isEqualTo("admin@example.com");
    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.emailVerifiedAt()).contains(VERIFIED_AT);
    assertThat(account.passwordCredential().passwordChangeRequired()).isFalse();
    assertThat(account.roleAssignments())
        .extracting(RoleAssignment::roleCode)
        .containsExactly(RoleCode.ROLE_ADMIN);
    assertThat(account.roleAssignments())
        .extracting(RoleAssignment::systemGranted)
        .containsExactly(false);
    assertThat(account.domainEvents()).isEmpty();
  }

  /** 비밀번호 해시는 이미 해시된 저장값만 담아야 하므로 빈 값을 거부합니다. */
  @Test
  void rejectsBlankPasswordHash() {
    assertThatThrownBy(() -> PasswordHash.from(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("password hash must not be blank");
  }
}
