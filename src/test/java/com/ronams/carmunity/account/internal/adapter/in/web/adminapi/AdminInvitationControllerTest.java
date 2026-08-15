package com.ronams.carmunity.account.internal.adapter.in.web.adminapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.AcceptAdminInvitationCommand;
import com.ronams.carmunity.account.internal.application.command.InviteAdminCommand;
import com.ronams.carmunity.account.internal.application.command.RevokeAdminInvitationCommand;
import com.ronams.carmunity.account.internal.application.port.in.AcceptAdminInvitationUseCase;
import com.ronams.carmunity.account.internal.application.port.in.InviteAdminUseCase;
import com.ronams.carmunity.account.internal.application.port.in.RevokeAdminInvitationUseCase;
import com.ronams.carmunity.account.internal.application.result.AcceptedAdminInvitationResult;
import com.ronams.carmunity.account.internal.application.result.AdminInvitationResult;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import com.ronams.carmunity.config.ApiErrorResponseFactory;
import com.ronams.carmunity.config.ApiExceptionHandler;
import com.ronams.carmunity.shared.error.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 관리자 초대 web API의 request/response와 공통 오류 응답 연결을 검증합니다. */
@WebMvcTest(controllers = AdminInvitationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  AdminInvitationControllerTest.TestUseCaseConfig.class
})
class AdminInvitationControllerTest {

  private static final UUID INVITER_ID = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final UUID INVITATION_ID = UUID.fromString("7c24c7cb-b2d2-4d35-8a3d-49a2b71bdb8a");
  private static final UUID ACCOUNT_ID = UUID.fromString("b73360d8-e38e-4c95-9b1b-3ce7b914c333");
  private static final Instant EXPIRES_AT = Instant.parse("2026-07-07T12:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeInviteAdminUseCase inviteUseCase;
  @Autowired private FakeAcceptAdminInvitationUseCase acceptUseCase;
  @Autowired private FakeRevokeAdminInvitationUseCase revokeUseCase;

  @BeforeEach
  void setUp() {
    inviteUseCase.reset();
    acceptUseCase.reset();
    revokeUseCase.reset();
  }

  /** 생성 API는 인증 principal public id를 command에 싣고 201 resource 표현을 반환합니다. */
  @Test
  void createsAdminInvitation() throws Exception {
    inviteUseCase.nextResult =
        new AdminInvitationResult(
            INVITATION_ID, "admin@example.com", RoleCode.ROLE_ADMIN, EXPIRES_AT);

    mockMvc
        .perform(
            post("/api/v1/admin/invitations")
                .principal(INVITER_ID::toString)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "ADMIN@example.com",
                      "roleCode": "ROLE_ADMIN"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/admin/invitations/" + INVITATION_ID))
        .andExpect(jsonPath("$.invitationId").value(INVITATION_ID.toString()))
        .andExpect(jsonPath("$.email").value("admin@example.com"))
        .andExpect(jsonPath("$.roleCode").value("ROLE_ADMIN"))
        .andExpect(jsonPath("$.expiresAt").value("2026-07-07T12:00:00Z"));

    assertThat(inviteUseCase.lastCommand)
        .isEqualTo(new InviteAdminCommand(INVITER_ID, "ADMIN@example.com", "ROLE_ADMIN"));
  }

  /** 수락 API는 인증 없이 path invitation id와 body token/password/displayName을 command로 변환합니다. */
  @Test
  void acceptsAdminInvitation() throws Exception {
    acceptUseCase.nextResult =
        new AcceptedAdminInvitationResult(ACCOUNT_ID, "admin@example.com", RoleCode.ROLE_ADMIN);

    mockMvc
        .perform(
            post("/api/v1/admin/invitations/{invitationId}/acceptances", INVITATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "raw-admin-invitation-token",
                      "password": "Valid-password-1",
                      "passwordConfirm": "Valid-password-1",
                      "displayName": "운영 관리자"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/accounts/" + ACCOUNT_ID))
        .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID.toString()))
        .andExpect(jsonPath("$.email").value("admin@example.com"))
        .andExpect(jsonPath("$.roleCode").value("ROLE_ADMIN"));

    assertThat(acceptUseCase.lastCommand)
        .isEqualTo(
            new AcceptAdminInvitationCommand(
                INVITATION_ID,
                "raw-admin-invitation-token",
                "Valid-password-1",
                "Valid-password-1",
                "운영 관리자"));
  }

  /** 수락 request는 새 비밀번호 최초 설정 흐름이므로 확인 입력 없이는 usecase로 넘기지 않습니다. */
  @Test
  void rejectsAcceptRequestWithoutPasswordConfirm() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/invitations/{invitationId}/acceptances", INVITATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "raw-admin-invitation-token",
                      "password": "Valid-password-1",
                      "displayName": "운영 관리자"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(
            jsonPath("$.instance")
                .value("/api/v1/admin/invitations/" + INVITATION_ID + "/acceptances"));

    assertThat(acceptUseCase.lastCommand).isNull();
  }

  /** 회수 API는 인증 principal public id와 path invitation id를 command로 전달하고 204로 응답합니다. */
  @Test
  void revokesAdminInvitation() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/admin/invitations/{invitationId}", INVITATION_ID)
                .principal(INVITER_ID::toString))
        .andExpect(status().isNoContent());

    assertThat(revokeUseCase.lastCommand)
        .isEqualTo(new RevokeAdminInvitationCommand(INVITER_ID, INVITATION_ID));
  }

  /** request validation 실패는 usecase 호출 전 공통 Problem Details 응답으로 닫힙니다. */
  @Test
  void rejectsInvalidInviteRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/invitations")
                .principal(INVITER_ID::toString)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.instance").value("/api/v1/admin/invitations"));

    assertThat(inviteUseCase.lastCommand).isNull();
  }

  /** application의 token 오류는 account 전용 Problem Details code로 노출합니다. */
  @Test
  void returnsTokenInvalidProblemDetail() throws Exception {
    acceptUseCase.nextException = new ApiException(AccountErrorCode.ADMIN_INVITATION_TOKEN_INVALID);

    mockMvc
        .perform(
            post("/api/v1/admin/invitations/{invitationId}/acceptances", INVITATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "wrong-token",
                      "password": "Valid-password-1",
                      "passwordConfirm": "Valid-password-1",
                      "displayName": "운영 관리자"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ADMIN_INVITATION_TOKEN_INVALID"))
        .andExpect(
            jsonPath("$.instance")
                .value("/api/v1/admin/invitations/" + INVITATION_ID + "/acceptances"));
  }

  @TestConfiguration
  static class TestUseCaseConfig {

    @Bean
    FakeInviteAdminUseCase fakeInviteAdminUseCase() {
      return new FakeInviteAdminUseCase();
    }

    @Bean
    FakeAcceptAdminInvitationUseCase fakeAcceptAdminInvitationUseCase() {
      return new FakeAcceptAdminInvitationUseCase();
    }

    @Bean
    FakeRevokeAdminInvitationUseCase fakeRevokeAdminInvitationUseCase() {
      return new FakeRevokeAdminInvitationUseCase();
    }
  }

  static class FakeInviteAdminUseCase implements InviteAdminUseCase {

    private AdminInvitationResult nextResult;
    private InviteAdminCommand lastCommand;

    private void reset() {
      nextResult = null;
      lastCommand = null;
    }

    @Override
    public AdminInvitationResult execute(InviteAdminCommand command) {
      lastCommand = command;
      return nextResult;
    }
  }

  static class FakeAcceptAdminInvitationUseCase implements AcceptAdminInvitationUseCase {

    private AcceptedAdminInvitationResult nextResult;
    private ApiException nextException;
    private AcceptAdminInvitationCommand lastCommand;

    private void reset() {
      nextResult = null;
      nextException = null;
      lastCommand = null;
    }

    @Override
    public AcceptedAdminInvitationResult execute(AcceptAdminInvitationCommand command) {
      lastCommand = command;
      if (nextException != null) {
        throw nextException;
      }
      return nextResult;
    }
  }

  static class FakeRevokeAdminInvitationUseCase implements RevokeAdminInvitationUseCase {

    private RevokeAdminInvitationCommand lastCommand;

    private void reset() {
      lastCommand = null;
    }

    @Override
    public void execute(RevokeAdminInvitationCommand command) {
      lastCommand = command;
    }
  }
}
