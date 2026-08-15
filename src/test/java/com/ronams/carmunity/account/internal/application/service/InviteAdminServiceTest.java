package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.InviteAdminCommand;
import com.ronams.carmunity.account.internal.application.port.out.AccountUniquenessPort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationRecord;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationSaveRequest;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationStorePort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationTokenGeneratorPort;
import com.ronams.carmunity.account.internal.application.port.out.AuthRoleLookupPort;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import com.ronams.carmunity.notification.api.AdminInvitationNotificationRequest;
import com.ronams.carmunity.notification.api.AdminInvitationNotificationSender;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 관리자 초대 생성 use case가 token, 저장, 발송, 감사 경계를 조율하는지 검증합니다. */
class InviteAdminServiceTest {

  private static final UUID INVITER_PUBLIC_ID =
      UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final UUID INVITATION_PUBLIC_ID =
      UUID.fromString("7c24c7cb-b2d2-4d35-8a3d-49a2b71bdb8a");
  private static final Instant NOW = Instant.parse("2026-07-06T12:00:00Z");

  private FakeAccountUniquenessPort accountUniquenessPort;
  private FakeAuthRoleLookupPort authRoleLookupPort;
  private FakeAdminInvitationTokenGeneratorPort tokenGeneratorPort;
  private AdminInvitationTokenHasher tokenHasher;
  private FakeAdminInvitationStorePort invitationStorePort;
  private FakeAdminInvitationNotificationSender notificationSender;
  private InviteAdminService service;

  @BeforeEach
  void setUp() {
    accountUniquenessPort = new FakeAccountUniquenessPort();
    authRoleLookupPort = new FakeAuthRoleLookupPort();
    tokenGeneratorPort = new FakeAdminInvitationTokenGeneratorPort();
    tokenHasher = new AdminInvitationTokenHasher();
    invitationStorePort = new FakeAdminInvitationStorePort();
    notificationSender = new FakeAdminInvitationNotificationSender();
    service =
        new InviteAdminService(
            accountUniquenessPort,
            authRoleLookupPort,
            tokenGeneratorPort,
            tokenHasher,
            invitationStorePort,
            notificationSender,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** 초대 생성은 raw token을 저장하지 않고 hash만 저장한 뒤 발송과 성공 감사를 남깁니다. */
  @Test
  void createsAdminInvitationWithHashedTokenAndSendsNotification() {
    var result = service.execute(command("  ADMIN@Example.COM  ", "ROLE_ADMIN"));

    assertThat(result.invitationId()).isEqualTo(INVITATION_PUBLIC_ID);
    assertThat(result.email()).isEqualTo("admin@example.com");
    assertThat(result.roleCode()).isEqualTo(RoleCode.ROLE_ADMIN);
    assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(86_400));

    assertThat(invitationStorePort.savedRequests).hasSize(1);
    AdminInvitationSaveRequest savedRequest = invitationStorePort.savedRequests.getFirst();
    assertThat(savedRequest.inviterPublicId()).isEqualTo(INVITER_PUBLIC_ID);
    assertThat(savedRequest.email().value()).isEqualTo("admin@example.com");
    assertThat(savedRequest.email().normalized()).isEqualTo("admin@example.com");
    assertThat(savedRequest.roleCode()).isEqualTo(RoleCode.ROLE_ADMIN);
    assertThat(savedRequest.tokenHash()).matches("^[0-9a-f]{64}$");
    assertThat(savedRequest.tokenHash()).isNotEqualTo("raw-admin-invitation-token");
    assertThat(savedRequest.issuedAt()).isEqualTo(NOW);
    assertThat(savedRequest.expiresAt()).isEqualTo(NOW.plusSeconds(86_400));

    assertThat(notificationSender.requests)
        .containsExactly(
            new AdminInvitationNotificationRequest(
                "admin@example.com",
                INVITATION_PUBLIC_ID,
                "raw-admin-invitation-token",
                NOW.plusSeconds(86_400)));
    assertThat(invitationStorePort.createdEventResults).containsExactly(true);
  }

  /** 기존 account email은 token 발급이나 저장 전에 거부합니다. */
  @Test
  void rejectsExistingAccountEmailBeforeGeneratingToken() {
    accountUniquenessPort.existingEmails.add("admin@example.com");

    assertThatThrownBy(() -> service.execute(command("ADMIN@example.com", "ROLE_ADMIN")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.EMAIL_ALREADY_USED));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(invitationStorePort.savedRequests).isEmpty();
    assertThat(notificationSender.requests).isEmpty();
  }

  /** 첫 구현은 ROLE_ADMIN만 허용하고 최고 관리자 증식은 별도 정책으로 남깁니다. */
  @Test
  void rejectsRoleOtherThanAdmin() {
    assertThatThrownBy(() -> service.execute(command("admin@example.com", "ROLE_SUPER_ADMIN")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.ADMIN_INVITATION_ROLE_NOT_ALLOWED));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(invitationStorePort.savedRequests).isEmpty();
  }

  /** role seed 누락은 운영 설정 오류이므로 초대 row를 만들기 전에 중단합니다. */
  @Test
  void rejectsMissingAdminRoleSeedBeforeSaving() {
    authRoleLookupPort.adminRoleExists = false;

    assertThatThrownBy(() -> service.execute(command("admin@example.com", "ROLE_ADMIN")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.AUTH_ROLE_NOT_CONFIGURED));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(invitationStorePort.savedRequests).isEmpty();
  }

  /** 발송 실패는 초대 row를 되돌리지 않고 실패 감사 결과로 기록합니다. */
  @Test
  void recordsCreatedEventFailureWhenNotificationFails() {
    notificationSender.failure = new IllegalStateException("provider failure");

    assertThatCode(() -> service.execute(command("admin@example.com", "ROLE_ADMIN")))
        .doesNotThrowAnyException();

    assertThat(invitationStorePort.savedRequests).hasSize(1);
    assertThat(invitationStorePort.createdEventResults).containsExactly(false);
  }

  /** 입력 형식 오류는 외부 부수효과 없이 공통 invalid request로 변환합니다. */
  @Test
  void convertsInvalidInputToInvalidRequest() {
    assertThatThrownBy(() -> service.execute(command("invalid-email", "ROLE_ADMIN")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(invitationStorePort.savedRequests).isEmpty();
  }

  private InviteAdminCommand command(String email, String roleCode) {
    return new InviteAdminCommand(INVITER_PUBLIC_ID, email, roleCode);
  }

  /** normalized email 기준 기존 account 여부만 표현하는 in-memory port 대역입니다. */
  private static class FakeAccountUniquenessPort implements AccountUniquenessPort {

    private final List<String> existingEmails = new ArrayList<>();

    @Override
    public boolean existsByNormalizedEmail(String normalizedEmail) {
      return existingEmails.contains(normalizedEmail);
    }

    @Override
    public boolean existsByNormalizedDisplayName(String normalizedDisplayName) {
      return false;
    }
  }

  /** ROLE_ADMIN seed 존재 여부를 테스트가 제어하는 role lookup 대역입니다. */
  private static class FakeAuthRoleLookupPort implements AuthRoleLookupPort {

    private boolean adminRoleExists = true;

    @Override
    public boolean existsByCode(RoleCode roleCode) {
      return adminRoleExists && roleCode == RoleCode.ROLE_ADMIN;
    }
  }

  /** raw invitation token 값을 테스트가 고정하고 generate 호출 여부를 기록합니다. */
  private static class FakeAdminInvitationTokenGeneratorPort
      implements AdminInvitationTokenGeneratorPort {

    private boolean generated;

    @Override
    public String generate() {
      generated = true;
      return "raw-admin-invitation-token";
    }
  }

  /** 저장 요청과 생성 감사 결과를 기록하는 초대 저장 port 대역입니다. */
  private static class FakeAdminInvitationStorePort implements AdminInvitationStorePort {

    private final List<AdminInvitationSaveRequest> savedRequests = new ArrayList<>();
    private final List<Boolean> createdEventResults = new ArrayList<>();

    @Override
    public AdminInvitationRecord save(AdminInvitationSaveRequest request) {
      savedRequests.add(request);
      return new AdminInvitationRecord(
          10L,
          INVITATION_PUBLIC_ID,
          request.email().value(),
          request.roleCode(),
          1L,
          request.expiresAt());
    }

    @Override
    public void recordCreated(AdminInvitationRecord invitation, boolean notificationSuccessful) {
      createdEventResults.add(notificationSuccessful);
    }
  }

  /** provider 호출 없이 발송 요청을 기록하고, 테스트가 지정한 RuntimeException을 던지는 대역입니다. */
  private static class FakeAdminInvitationNotificationSender
      implements AdminInvitationNotificationSender {

    private final List<AdminInvitationNotificationRequest> requests = new ArrayList<>();
    private RuntimeException failure;

    @Override
    public void send(AdminInvitationNotificationRequest request) {
      if (failure != null) {
        throw failure;
      }
      requests.add(request);
    }
  }
}
