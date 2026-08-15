package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ConfirmPasswordResetCommand;
import com.ronams.carmunity.account.internal.application.command.RequestPasswordResetCommand;
import com.ronams.carmunity.account.internal.application.port.in.ConfirmPasswordResetUseCase;
import com.ronams.carmunity.account.internal.application.port.in.RequestPasswordResetUseCase;
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

/** 비밀번호 재설정 public API의 request/confirm 계약을 검증합니다. */
@WebMvcTest(controllers = PasswordResetController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  PasswordResetControllerTest.TestUseCaseConfig.class
})
class PasswordResetControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeRequestPasswordResetUseCase requestUseCase;
  @Autowired private FakeConfirmPasswordResetUseCase confirmUseCase;

  @BeforeEach
  void setUp() {
    requestUseCase.reset();
    confirmUseCase.reset();
  }

  /** reset 요청 API는 enumeration 방지를 위해 성공 시 항상 202 empty body로 응답합니다. */
  @Test
  void requestsPasswordResetAndReturnsAccepted() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/password-resets")
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

  /** valid reset 완료 요청은 usecase에 원문 token과 새 password를 넘긴 뒤 204로 응답합니다. */
  @Test
  void confirmsPasswordResetAndReturnsNoContent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/password-resets/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO_",
                      "newPassword": "new correct horse battery staple",
                      "newPasswordConfirmation": "new correct horse battery staple"
                    }
                    """))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertThat(confirmUseCase.lastCommand.token())
        .isEqualTo("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO_");
    assertThat(confirmUseCase.lastCommand.newPassword())
        .isEqualTo("new correct horse battery staple");
  }

  /** 요청 body validation 실패는 usecase 호출 전 공통 INVALID_REQUEST로 닫힙니다. */
  @Test
  void rejectsBlankEmailRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/password-resets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/password-resets"));

    assertThat(requestUseCase.lastCommand).isNull();
  }

  /** token 형식 오류는 confirmation usecase를 호출하지 않고 validation 실패로 응답합니다. */
  @Test
  void rejectsMalformedConfirmationToken() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/password-resets/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "***",
                      "newPassword": "new correct horse battery staple",
                      "newPasswordConfirmation": "new correct horse battery staple"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/password-resets/confirm"));

    assertThat(confirmUseCase.lastCommand).isNull();
  }

  /** application의 만료 reset token 오류는 account 전용 Problem Details code로 노출합니다. */
  @Test
  void returnsExpiredTokenProblemDetail() throws Exception {
    confirmUseCase.nextException = new ApiException(AccountErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);

    mockMvc
        .perform(
            post("/api/v1/auth/password-resets/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token": "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO_",
                      "newPassword": "new correct horse battery staple",
                      "newPasswordConfirmation": "new correct horse battery staple"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_EXPIRED"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/password-resets/confirm"));
  }

  @TestConfiguration
  static class TestUseCaseConfig {

    @Bean
    FakeRequestPasswordResetUseCase fakeRequestPasswordResetUseCase() {
      return new FakeRequestPasswordResetUseCase();
    }

    @Bean
    FakeConfirmPasswordResetUseCase fakeConfirmPasswordResetUseCase() {
      return new FakeConfirmPasswordResetUseCase();
    }
  }

  static class FakeRequestPasswordResetUseCase implements RequestPasswordResetUseCase {

    private RequestPasswordResetCommand lastCommand;

    private void reset() {
      lastCommand = null;
    }

    @Override
    public void execute(RequestPasswordResetCommand command) {
      lastCommand = command;
    }
  }

  static class FakeConfirmPasswordResetUseCase implements ConfirmPasswordResetUseCase {

    private ConfirmPasswordResetCommand lastCommand;
    private ApiException nextException;

    private void reset() {
      lastCommand = null;
      nextException = null;
    }

    @Override
    public void execute(ConfirmPasswordResetCommand command) {
      lastCommand = command;
      if (nextException != null) {
        throw nextException;
      }
    }
  }
}
