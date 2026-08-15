package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.AcceptAdminInvitationCommand;
import com.ronams.carmunity.account.internal.application.port.out.AcceptedAdminAccountStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.AccountUniquenessPort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationAcceptancePort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationForAcceptance;
import com.ronams.carmunity.account.internal.application.port.out.PasswordHasherPort;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 관리자 초대 수락 use case가 public token 검증과 account 생성 경계를 조율하는지 검증합니다. */
class AcceptAdminInvitationServiceTest {

  private static final UUID INVITATION_PUBLIC_ID =
      UUID.fromString("7c24c7cb-b2d2-4d35-8a3d-49a2b71bdb8a");
  private static final UUID ACCOUNT_PUBLIC_ID =
      UUID.fromString("b73360d8-e38e-4c95-9b1b-3ce7b914c333");
  private static final Instant NOW = Instant.parse("2026-07-06T12:00:00Z");

  private FakeAccountUniquenessPort accountUniquenessPort;
  private FakePasswordHasherPort passwordHasherPort;
  private AdminInvitationTokenHasher tokenHasher;
  private FakeAdminInvitationAcceptancePort acceptancePort;
  private AcceptAdminInvitationService service;

  @BeforeEach
  void setUp() {
    accountUniquenessPort = new FakeAccountUniquenessPort();
    passwordHasherPort = new FakePasswordHasherPort();
    tokenHasher = new AdminInvitationTokenHasher();
    acceptancePort = new FakeAdminInvitationAcceptancePort(tokenHasher);
    service =
        new AcceptAdminInvitationService(
            accountUniquenessPort,
            passwordHasherPort,
            tokenHasher,
            acceptancePort,
            () -> ACCOUNT_PUBLIC_ID,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** 유효한 초대는 ACTIVE 관리자 account graph 생성 요청과 수락 감사 저장으로 이어집니다. */
  @Test
  void acceptsValidInvitation() {
    var result =
        service.execute(
            new AcceptAdminInvitationCommand(
                INVITATION_PUBLIC_ID,
                "raw-admin-invitation-token",
                "Valid-password-1",
                "Valid-password-1",
                "운영 관리자"));

    assertThat(result.accountId()).isEqualTo(ACCOUNT_PUBLIC_ID);
    assertThat(result.email()).isEqualTo("admin@example.com");
    assertThat(result.roleCode()).isEqualTo(RoleCode.ROLE_ADMIN);

    assertThat(passwordHasherPort.hashedPasswords).containsExactly("Valid-password-1");
    assertThat(acceptancePort.acceptedRequests).hasSize(1);
    AcceptedAdminAccountStoreRequest request = acceptancePort.acceptedRequests.getFirst();
    assertThat(request.invitationId()).isEqualTo(10L);
    assertThat(request.invitationPublicId()).isEqualTo(INVITATION_PUBLIC_ID);
    assertThat(request.invitedByAccountId()).isEqualTo(1L);
    assertThat(request.roleId()).isEqualTo(2L);
    assertThat(request.roleCode()).isEqualTo(RoleCode.ROLE_ADMIN);
    assertThat(request.acceptedAt()).isEqualTo(NOW);
    assertThat(request.account().email().value()).isEqualTo("admin@example.com");
    assertThat(request.account().profile().displayName().value()).isEqualTo("운영 관리자");
    assertThat(request.account().passwordCredential().passwordHash())
        .isEqualTo(PasswordHash.from("{argon2}hashed-password"));
  }

  /** path의 invitation id만으로는 수락되지 않고 body token hash가 일치해야 합니다. */
  @Test
  void rejectsTokenMismatch() {
    assertThatThrownBy(
            () ->
                service.execute(
                    new AcceptAdminInvitationCommand(
                        INVITATION_PUBLIC_ID,
                        "wrong-token",
                        "Valid-password-1",
                        "Valid-password-1",
                        "운영 관리자")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.ADMIN_INVITATION_TOKEN_INVALID));

    assertThat(passwordHasherPort.hashedPasswords).isEmpty();
    assertThat(acceptancePort.acceptedRequests).isEmpty();
  }

  /** 만료된 초대는 account 생성 전 거부합니다. */
  @Test
  void rejectsExpiredInvitation() {
    acceptancePort.invitation = acceptancePort.invitation.withExpiresAt(NOW.minusSeconds(1));

    assertThatThrownBy(
            () ->
                service.execute(
                    new AcceptAdminInvitationCommand(
                        INVITATION_PUBLIC_ID,
                        "raw-admin-invitation-token",
                        "Valid-password-1",
                        "Valid-password-1",
                        "운영 관리자")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.ADMIN_INVITATION_TOKEN_EXPIRED));

    assertThat(passwordHasherPort.hashedPasswords).isEmpty();
    assertThat(acceptancePort.acceptedRequests).isEmpty();
  }

  /** 이미 수락/회수된 초대는 account 생성 전 conflict로 종료합니다. */
  @Test
  void rejectsClosedInvitation() {
    acceptancePort.invitation = acceptancePort.invitation.withClosed(true);

    assertThatThrownBy(
            () ->
                service.execute(
                    new AcceptAdminInvitationCommand(
                        INVITATION_PUBLIC_ID,
                        "raw-admin-invitation-token",
                        "Valid-password-1",
                        "Valid-password-1",
                        "운영 관리자")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.ADMIN_INVITATION_ALREADY_CLOSED));

    assertThat(passwordHasherPort.hashedPasswords).isEmpty();
    assertThat(acceptancePort.acceptedRequests).isEmpty();
  }

  /** 초대 email에 기존 account가 있으면 비밀번호 hash 전에 거부합니다. */
  @Test
  void rejectsExistingAccountEmail() {
    accountUniquenessPort.existingEmails.add("admin@example.com");

    assertThatThrownBy(
            () ->
                service.execute(
                    new AcceptAdminInvitationCommand(
                        INVITATION_PUBLIC_ID,
                        "raw-admin-invitation-token",
                        "Valid-password-1",
                        "Valid-password-1",
                        "운영 관리자")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.EMAIL_ALREADY_USED));

    assertThat(passwordHasherPort.hashedPasswords).isEmpty();
    assertThat(acceptancePort.acceptedRequests).isEmpty();
  }

  /** 표시 이름 중복은 기존 자체 가입과 같은 오류 계약으로 거부합니다. */
  @Test
  void rejectsDuplicateDisplayName() {
    accountUniquenessPort.existingDisplayNames.add("운영 관리자");

    assertThatThrownBy(
            () ->
                service.execute(
                    new AcceptAdminInvitationCommand(
                        INVITATION_PUBLIC_ID,
                        "raw-admin-invitation-token",
                        "Valid-password-1",
                        "Valid-password-1",
                        "운영 관리자")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.DISPLAY_NAME_ALREADY_USED));

    assertThat(passwordHasherPort.hashedPasswords).isEmpty();
    assertThat(acceptancePort.acceptedRequests).isEmpty();
  }

  /** 비밀번호나 표시 이름 정책 위반은 저장 부수효과 없이 공통 invalid request로 변환합니다. */
  @Test
  void convertsInvalidInputToInvalidRequest() {
    assertThatThrownBy(
            () ->
                service.execute(
                    new AcceptAdminInvitationCommand(
                        INVITATION_PUBLIC_ID,
                        "raw-admin-invitation-token",
                        "short",
                        "short",
                        "운영 관리자")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(passwordHasherPort.hashedPasswords).isEmpty();
    assertThat(acceptancePort.acceptedRequests).isEmpty();
  }

  /** 비밀번호 확인 불일치는 token이 맞아도 새 account 생성이나 password hash 전에 invalid request로 닫습니다. */
  @Test
  void rejectsPasswordConfirmationMismatch() {
    assertThatThrownBy(
            () ->
                service.execute(
                    new AcceptAdminInvitationCommand(
                        INVITATION_PUBLIC_ID,
                        "raw-admin-invitation-token",
                        "Valid-password-1",
                        "Different-password-1",
                        "운영 관리자")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(passwordHasherPort.hashedPasswords).isEmpty();
    assertThat(acceptancePort.acceptedRequests).isEmpty();
  }

  /** normalized email과 display name 기준 기존 account/profile 여부만 표현하는 in-memory port 대역입니다. */
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

  /** 비밀번호 원문 hash 요청을 기록하는 port 대역입니다. */
  private static class FakePasswordHasherPort implements PasswordHasherPort {

    private final List<String> hashedPasswords = new ArrayList<>();

    @Override
    public PasswordHash hash(String rawPassword) {
      hashedPasswords.add(rawPassword);
      return PasswordHash.from("{argon2}hashed-password");
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
      return false;
    }
  }

  /** 초대 조회와 수락 저장 요청을 기록하는 port 대역입니다. */
  private static class FakeAdminInvitationAcceptancePort implements AdminInvitationAcceptancePort {

    private AdminInvitationForAcceptance invitation;
    private final List<AcceptedAdminAccountStoreRequest> acceptedRequests = new ArrayList<>();

    FakeAdminInvitationAcceptancePort(AdminInvitationTokenHasher tokenHasher) {
      invitation =
          new AdminInvitationForAcceptance(
              10L,
              INVITATION_PUBLIC_ID,
              "admin@example.com",
              2L,
              RoleCode.ROLE_ADMIN,
              tokenHasher.hash("raw-admin-invitation-token"),
              1L,
              NOW.plusSeconds(86_400),
              false);
    }

    @Override
    public Optional<AdminInvitationForAcceptance> findForAcceptance(UUID invitationPublicId) {
      if (!INVITATION_PUBLIC_ID.equals(invitationPublicId)) {
        return Optional.empty();
      }
      return Optional.of(invitation);
    }

    @Override
    public void accept(AcceptedAdminAccountStoreRequest request) {
      acceptedRequests.add(request);
    }
  }
}
