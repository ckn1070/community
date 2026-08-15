package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.RevokeAdminInvitationCommand;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationRevocationPort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationRevokeRequest;
import com.ronams.carmunity.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 관리자 초대 회수 use case가 open 초대만 terminal state로 전환하는지 검증합니다. */
class RevokeAdminInvitationServiceTest {

  private static final UUID INVITATION_PUBLIC_ID =
      UUID.fromString("7c24c7cb-b2d2-4d35-8a3d-49a2b71bdb8a");
  private static final UUID REVOKER_PUBLIC_ID =
      UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final Instant NOW = Instant.parse("2026-07-06T12:00:00Z");

  private FakeAdminInvitationRevocationPort revocationPort;
  private RevokeAdminInvitationService service;

  @BeforeEach
  void setUp() {
    revocationPort = new FakeAdminInvitationRevocationPort();
    service = new RevokeAdminInvitationService(revocationPort, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** open 초대 회수는 회수자, 초대 id, 시각을 persistence port에 전달합니다. */
  @Test
  void revokesOpenInvitation() {
    service.execute(new RevokeAdminInvitationCommand(REVOKER_PUBLIC_ID, INVITATION_PUBLIC_ID));

    assertThat(revocationPort.requests)
        .containsExactly(
            new AdminInvitationRevokeRequest(REVOKER_PUBLIC_ID, INVITATION_PUBLIC_ID, NOW));
  }

  /** 이미 수락/회수된 초대는 service 경계에서 conflict를 그대로 전파합니다. */
  @Test
  void propagatesClosedInvitationConflict() {
    revocationPort.failure = new ApiException(AccountErrorCode.ADMIN_INVITATION_ALREADY_CLOSED);

    assertThatThrownBy(
            () ->
                service.execute(
                    new RevokeAdminInvitationCommand(REVOKER_PUBLIC_ID, INVITATION_PUBLIC_ID)))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.ADMIN_INVITATION_ALREADY_CLOSED));
  }

  /** 회수 요청을 기록하고 테스트가 지정한 업무 예외를 그대로 던지는 port 대역입니다. */
  private static class FakeAdminInvitationRevocationPort implements AdminInvitationRevocationPort {

    private final List<AdminInvitationRevokeRequest> requests = new ArrayList<>();
    private ApiException failure;

    @Override
    public void revoke(AdminInvitationRevokeRequest request) {
      if (failure != null) {
        throw failure;
      }
      requests.add(request);
    }
  }
}
