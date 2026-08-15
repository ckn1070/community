package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.RegisterMemberAccountCommand;
import com.ronams.carmunity.account.internal.application.port.out.AccountUniquenessPort;
import com.ronams.carmunity.account.internal.application.port.out.AuthRoleLookupPort;
import com.ronams.carmunity.account.internal.application.port.out.MemberAccountStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordHasherPort;
import com.ronams.carmunity.account.internal.domain.Account;
import com.ronams.carmunity.account.internal.domain.AccountStatus;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import com.ronams.carmunity.account.internal.domain.RoleAssignment;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 가입 application service의 orchestration과 port 호출 순서를 검증합니다.
 *
 * <p>fake port는 외부 기술을 배제하고, 중복/seed 실패 시 hashing과 저장이 호출되지 않는 정책을 관찰하기 위해 사용합니다.
 */
class RegisterMemberAccountServiceTest {

  private static final String VALID_PASSWORD = "correct horse battery staple";

  private FakeAccountUniquenessPort accountUniquenessPort;
  private FakeAuthRoleLookupPort authRoleLookupPort;
  private FakePasswordHasherPort passwordHasherPort;
  private FakeMemberAccountStorePort memberAccountStorePort;
  private RegisterMemberAccountService service;

  @BeforeEach
  void setUp() {
    accountUniquenessPort = new FakeAccountUniquenessPort();
    authRoleLookupPort = new FakeAuthRoleLookupPort();
    passwordHasherPort = new FakePasswordHasherPort();
    memberAccountStorePort = new FakeMemberAccountStorePort();
    service =
        new RegisterMemberAccountService(
            accountUniquenessPort, authRoleLookupPort, passwordHasherPort, memberAccountStorePort);
  }

  /** 가입 성공 시 application service가 입력 정규화, PENDING 상태, 기본 역할, 가입 이벤트를 조합합니다. */
  @Test
  void registersPendingMemberWithNormalizedInputsAndMemberRole() {
    var result =
        service.execute(command("  USER@Example.COM  ", VALID_PASSWORD, "  Road   Runner  "));

    Account storedAccount = memberAccountStorePort.storedAccount;
    assertThat(result.accountPublicId()).isEqualTo(storedAccount.publicId());
    assertThat(result.email()).isEqualTo("user@example.com");
    assertThat(result.displayName()).isEqualTo("Road Runner");
    assertThat(result.status()).isEqualTo(AccountStatus.PENDING);

    assertThat(storedAccount.publicId()).isNotNull();
    assertThat(storedAccount.email().value()).isEqualTo("user@example.com");
    assertThat(storedAccount.email().normalized()).isEqualTo("user@example.com");
    assertThat(storedAccount.profile().displayName().value()).isEqualTo("Road Runner");
    assertThat(storedAccount.profile().displayName().normalized()).isEqualTo("road runner");
    assertThat(storedAccount.status()).isEqualTo(AccountStatus.PENDING);
    assertThat(storedAccount.passwordCredential().passwordHash().value())
        .isEqualTo("{argon2}hashed-password");
    assertThat(storedAccount.roleAssignments())
        .extracting(RoleAssignment::roleCode)
        .containsExactly(RoleCode.ROLE_MEMBER);
    assertThat(storedAccount.domainEvents())
        .extracting(event -> event.accountPublicId())
        .containsExactly(storedAccount.publicId());
  }

  /** email 중복 pre-check 실패는 raw password hashing과 저장 port 호출 전에 중단되어야 합니다. */
  @Test
  void rejectsDuplicatedEmailBeforeHashingAndSaving() {
    accountUniquenessPort.existingEmails.add("user@example.com");

    assertThatThrownBy(() -> service.execute(command("USER@example.com", VALID_PASSWORD, "User")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.EMAIL_ALREADY_USED));

    assertThat(passwordHasherPort.called).isFalse();
    assertThat(memberAccountStorePort.storedAccount).isNull();
  }

  /** displayName 중복 pre-check 실패도 저장 전 정책 위반으로 닫고 password 처리로 넘어가지 않습니다. */
  @Test
  void rejectsDuplicatedDisplayNameBeforeHashingAndSaving() {
    accountUniquenessPort.existingDisplayNames.add("road runner");

    assertThatThrownBy(
            () -> service.execute(command("user@example.com", VALID_PASSWORD, "Road Runner")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.DISPLAY_NAME_ALREADY_USED));

    assertThat(passwordHasherPort.called).isFalse();
    assertThat(memberAccountStorePort.storedAccount).isNull();
  }

  /** ROLE_MEMBER seed가 없으면 잘못된 운영 설정으로 보고 account graph를 만들기 전에 중단합니다. */
  @Test
  void rejectsMissingMemberRoleSeedBeforeSaving() {
    authRoleLookupPort.memberRoleExists = false;

    assertThatThrownBy(
            () -> service.execute(command("user@example.com", VALID_PASSWORD, "Road Runner")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.AUTH_ROLE_NOT_CONFIGURED));

    assertThat(passwordHasherPort.called).isFalse();
    assertThat(memberAccountStorePort.storedAccount).isNull();
  }

  /** domain validation 실패는 외부 의존 호출 없이 public API용 INVALID_REQUEST 코드로 변환합니다. */
  @Test
  void convertsInputValidationFailureToInvalidRequestCode() {
    assertThatThrownBy(() -> service.execute(command("user@example.com", "short", "Road Runner")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(passwordHasherPort.called).isFalse();
    assertThat(memberAccountStorePort.storedAccount).isNull();
  }

  private RegisterMemberAccountCommand command(String email, String password, String displayName) {
    return new RegisterMemberAccountCommand(email, password, displayName);
  }

  /** normalized 값 기준의 중복 pre-check만 표현하는 in-memory port 대역입니다. */
  private static class FakeAccountUniquenessPort implements AccountUniquenessPort {

    private final List<String> existingEmails = new ArrayList<>();
    private final List<String> existingDisplayNames = new ArrayList<>();

    @Override
    public boolean existsByNormalizedEmail(String normalizedEmail) {
      return existingEmails.contains(normalizedEmail);
    }

    @Override
    public boolean existsByNormalizedDisplayName(String normalizedDisplayName) {
      return existingDisplayNames.contains(normalizedDisplayName);
    }
  }

  /** ROLE_MEMBER seed 존재 여부를 테스트가 제어하는 role lookup 대역입니다. */
  private static class FakeAuthRoleLookupPort implements AuthRoleLookupPort {

    private boolean memberRoleExists = true;

    @Override
    public boolean existsByCode(RoleCode roleCode) {
      return memberRoleExists && roleCode == RoleCode.ROLE_MEMBER;
    }
  }

  /** 비밀번호 원문이 중복/seed 검증을 통과한 뒤에만 hashing되는지 관찰합니다. */
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

  /** 저장 port 호출 여부와 전달된 aggregate graph를 기록하는 대역입니다. */
  private static class FakeMemberAccountStorePort implements MemberAccountStorePort {

    private Account storedAccount;

    @Override
    public void save(Account account) {
      storedAccount = account;
    }
  }
}
