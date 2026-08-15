package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ConfirmMemberEmailVerificationCommand;
import com.ronams.carmunity.account.internal.application.command.RequestMemberEmailVerificationCommand;
import com.ronams.carmunity.account.internal.application.port.in.ConfirmMemberEmailVerificationUseCase;
import com.ronams.carmunity.account.internal.application.port.in.RequestMemberEmailVerificationUseCase;
import com.ronams.carmunity.config.ApiErrorResponseFactory;
import com.ronams.carmunity.config.ApiExceptionHandler;
import com.ronams.carmunity.shared.error.ApiException;
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

/** 이메일 인증 public API의 request/confirmation 계약을 검증합니다. */
@WebMvcTest(controllers = MemberEmailVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  MemberEmailVerificationControllerTest.TestUseCaseConfig.class
})
class MemberEmailVerificationControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeRequestMemberEmailVerificationUseCase requestUseCase;
  @Autowired private FakeConfirmMemberEmailVerificationUseCase confirmUseCase;

  @BeforeEach
  void setUp() {
    requestUseCase.reset();
    confirmUseCase.reset();
  }

  /** 이메일 인증 요청 API는 public enumeration 방지를 위해 성공 시 202 empty body로 응답합니다. */
  @Test
  void requestsEmailVerificationAndReturnsAccepted() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/verifications/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "USER@example.com"
                    }
                    """))
        .andExpect(status().isAccepted())
        .andExpect(content().string(""));

    assertThat(requestUseCase.lastCommand.email()).isEqualTo("USER@example.com");
  }

  /** confirmation usecase가 정상 완료되면 API는 body 없이 204로 응답합니다. */
  @Test
  void confirmsEmailVerificationAndReturnsNoContent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/verifications/email/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO_"
                    }
                    """))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertThat(confirmUseCase.lastCommand.token())
        .isEqualTo("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO_");
  }

  /** 요청 body validation 실패는 usecase 호출 전 공통 INVALID_REQUEST로 닫힙니다. */
  @Test
  void rejectsBlankEmailRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/verifications/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/verifications/email"));

    assertThat(requestUseCase.lastCommand).isNull();
  }

  /** token 형식 오류는 confirmation usecase를 호출하지 않고 validation 실패로 응답합니다. */
  @Test
  void rejectsMalformedConfirmationToken() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/verifications/email/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "***"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/verifications/email/confirm"));

    assertThat(confirmUseCase.lastCommand).isNull();
  }

  /** application의 만료 token 오류는 account 전용 Problem Details code로 노출합니다. */
  @Test
  void returnsExpiredTokenProblemDetail() throws Exception {
    confirmUseCase.nextException =
        new ApiException(AccountErrorCode.EMAIL_VERIFICATION_TOKEN_EXPIRED);

    mockMvc
        .perform(
            post("/api/v1/auth/verifications/email/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO_"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOKEN_EXPIRED"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/verifications/email/confirm"));
  }

  @TestConfiguration
  static class TestUseCaseConfig {

    @Bean
    FakeRequestMemberEmailVerificationUseCase fakeRequestMemberEmailVerificationUseCase() {
      return new FakeRequestMemberEmailVerificationUseCase();
    }

    @Bean
    FakeConfirmMemberEmailVerificationUseCase fakeConfirmMemberEmailVerificationUseCase() {
      return new FakeConfirmMemberEmailVerificationUseCase();
    }
  }

  static class FakeRequestMemberEmailVerificationUseCase
      implements RequestMemberEmailVerificationUseCase {

    private RequestMemberEmailVerificationCommand lastCommand;

    private void reset() {
      lastCommand = null;
    }

    @Override
    public void execute(RequestMemberEmailVerificationCommand command) {
      lastCommand = command;
    }
  }

  static class FakeConfirmMemberEmailVerificationUseCase
      implements ConfirmMemberEmailVerificationUseCase {

    private ConfirmMemberEmailVerificationCommand lastCommand;
    private ApiException nextException;

    private void reset() {
      lastCommand = null;
      nextException = null;
    }

    @Override
    public void execute(ConfirmMemberEmailVerificationCommand command) {
      lastCommand = command;
      if (nextException != null) {
        throw nextException;
      }
    }
  }
}
