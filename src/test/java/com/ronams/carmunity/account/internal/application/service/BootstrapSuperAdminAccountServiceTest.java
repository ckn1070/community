package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.BootstrapSuperAdminAccountCommand;
import com.ronams.carmunity.account.internal.application.port.out.AuthRoleLookupPort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordHasherPort;
import com.ronams.carmunity.account.internal.application.port.out.SuperAdminBootstrapStorePort;
import com.ronams.carmunity.account.internal.application.result.BootstrapSuperAdminAccountResult;
import com.ronams.carmunity.account.internal.domain.Account;
import com.ronams.carmunity.account.internal.domain.AccountStatus;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import com.ronams.carmunity.account.internal.domain.RoleAssignment;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 최초 최고 관리자 bootstrap application service의 생성, no-op, 실패 정책을 검증합니다. */
class BootstrapSuperAdminAccountServiceTest {

  private static final String VALID_PASSWORD = "correct horse battery staple";

  private FakeAuthRoleLookupPort authRoleLookupPort;
  private FakePasswordHasherPort passwordHasherPort;
  private FakeSuperAdminBootstrapStorePort superAdminBootstrapStorePort;
  private BootstrapSuperAdminAccountService service;

  @BeforeEach
  void setUp() {
    authRoleLookupPort = new FakeAuthRoleLookupPort();
    passwordHasherPort = new FakePasswordHasherPort();
    superAdminBootstrapStorePort = new FakeSuperAdminBootstrapStorePort();
    service =
        new BootstrapSuperAdminAccountService(
            authRoleLookupPort, passwordHasherPort, superAdminBootstrapStorePort);
  }

  /** 최초 실행은 ACTIVE 최고 관리자와 강제 비밀번호 변경 credential을 저장하고 CREATED를 반환합니다. */
  @Test
  void createsInitialSuperAdminWhenNoActiveSuperAdminExists() {
    BootstrapSuperAdminAccountResult result =
        service.execute(command("  ADMIN@Example.COM  ", "  Initial   Admin  ", VALID_PASSWORD));

    Account storedAccount = superAdminBootstrapStorePort.storedAccount;
    assertThat(result.status()).isEqualTo(BootstrapSuperAdminAccountResult.Status.CREATED);
    assertThat(result.accountPublicId()).contains(storedAccount.publicId());
    assertThat(result.email()).contains("admin@example.com");
    assertThat(result.displayName()).contains("Initial Admin");

    assertThat(storedAccount.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(storedAccount.email().value()).isEqualTo("admin@example.com");
    assertThat(storedAccount.profile().displayName().value()).isEqualTo("Initial Admin");
    assertThat(storedAccount.passwordCredential().passwordHash().value())
        .isEqualTo("{argon2}hashed-password");
    assertThat(storedAccount.passwordCredential().passwordChangeRequired()).isTrue();
    assertThat(storedAccount.roleAssignments())
        .extracting(RoleAssignment::roleCode)
        .containsExactly(RoleCode.ROLE_SUPER_ADMIN);
    assertThat(passwordHasherPort.called).isTrue();
  }

  /** 이미 활성 최고 관리자가 있으면 hashing과 저장 없이 ALREADY_BOOTSTRAPPED로 종료합니다. */
  @Test
  void returnsAlreadyBootstrappedWithoutHashingWhenActiveSuperAdminExists() {
    superAdminBootstrapStorePort.activeSuperAdminExists = true;

    BootstrapSuperAdminAccountResult result =
        service.execute(command("admin@example.com", "Initial Admin", VALID_PASSWORD));

    assertThat(result.status())
        .isEqualTo(BootstrapSuperAdminAccountResult.Status.ALREADY_BOOTSTRAPPED);
    assertThat(result.accountPublicId()).isEmpty();
    assertThat(result.email()).isEmpty();
    assertThat(result.displayName()).isEmpty();
    assertThat(passwordHasherPort.called).isFalse();
    assertThat(superAdminBootstrapStorePort.storedAccount).isNull();
  }

  /** ROLE_SUPER_ADMIN seed가 없으면 운영 설정 오류로 보고 저장 전에 중단합니다. */
  @Test
  void rejectsMissingSuperAdminRoleSeedBeforeSaving() {
    authRoleLookupPort.superAdminRoleExists = false;

    assertThatThrownBy(
            () -> service.execute(command("admin@example.com", "Initial Admin", VALID_PASSWORD)))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.AUTH_ROLE_NOT_CONFIGURED));

    assertThat(passwordHasherPort.called).isFalse();
    assertThat(superAdminBootstrapStorePort.storedAccount).isNull();
  }

  /** 입력 validation 실패는 외부 의존 호출 없이 public API용 INVALID_REQUEST 코드로 변환합니다. */
  @Test
  void convertsInputValidationFailureToInvalidRequestCode() {
    assertThatThrownBy(() -> service.execute(command("not-email", "Initial Admin", VALID_PASSWORD)))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(passwordHasherPort.called).isFalse();
    assertThat(superAdminBootstrapStorePort.storedAccount).isNull();
  }

  private BootstrapSuperAdminAccountCommand command(
      String email, String displayName, String password) {
    return new BootstrapSuperAdminAccountCommand(email, displayName, password);
  }

  /** ROLE_SUPER_ADMIN seed 누락을 저장 전 실패로 고정하기 위한 role lookup 대역입니다. */
  private static class FakeAuthRoleLookupPort implements AuthRoleLookupPort {

    private boolean superAdminRoleExists = true;

    @Override
    public boolean existsByCode(RoleCode roleCode) {
      return superAdminRoleExists && roleCode == RoleCode.ROLE_SUPER_ADMIN;
    }
  }

  /** 비밀번호 원문이 role/idempotency 검증을 통과한 뒤에만 hashing되는지 관찰합니다. */
  private static class FakePasswordHasherPort implements PasswordHasherPort {

    private boolean called;

    @Override
    public PasswordHash hash(String rawPassword) {
      called = true;
      return PasswordHash.from("{argon2}hashed-password");
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
      return false;
    }
  }

  /** 활성 최고 관리자 확인과 생성 저장 단위만 표현하는 in-memory port 대역입니다. */
  private static class FakeSuperAdminBootstrapStorePort implements SuperAdminBootstrapStorePort {

    private boolean activeSuperAdminExists;
    private Account storedAccount;

    @Override
    public boolean activeSuperAdminExists() {
      return activeSuperAdminExists;
    }

    @Override
    public Optional<Account> saveIfNoActiveSuperAdmin(Account account) {
      if (activeSuperAdminExists) {
        return Optional.empty();
      }
      storedAccount = account;
      activeSuperAdminExists = true;
      return Optional.of(account);
    }
  }
}
