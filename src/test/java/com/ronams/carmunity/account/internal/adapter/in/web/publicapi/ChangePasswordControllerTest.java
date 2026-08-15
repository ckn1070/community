package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ChangePasswordCommand;
import com.ronams.carmunity.account.internal.application.port.in.ChangePasswordUseCase;
import com.ronams.carmunity.config.ApiErrorResponseFactory;
import com.ronams.carmunity.config.ApiExceptionHandler;
import com.ronams.carmunity.shared.error.ApiException;
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

/** 비밀번호 변경 public API의 요청/응답 계약과 공통 오류 응답 연결을 검증합니다. */
@WebMvcTest(controllers = ChangePasswordController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  ChangePasswordControllerTest.TestUseCaseConfig.class
})
class ChangePasswordControllerTest {

  private static final UUID ACCOUNT_PUBLIC_ID =
      UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeChangePasswordUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase.reset();
  }

  /** controller는 인증 principal의 public id와 request body를 command로 변환하고 204만 반환합니다. */
  @Test
  void changesPasswordAndReturnsNoContent() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/auth/password")
                .principal(ACCOUNT_PUBLIC_ID::toString)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currentPassword": "correct horse battery staple",
                      "newPassword": "new correct horse battery staple",
                      "newPasswordConfirmation": "new correct horse battery staple"
                    }
                    """))
        .andExpect(status().isNoContent());

    assertThat(useCase.lastCommand.accountPublicId()).isEqualTo(ACCOUNT_PUBLIC_ID);
    assertThat(useCase.lastCommand.currentPassword()).isEqualTo("correct horse battery staple");
    assertThat(useCase.lastCommand.newPassword()).isEqualTo("new correct horse battery staple");
    assertThat(useCase.lastCommand.newPasswordConfirmation())
        .isEqualTo("new correct horse battery staple");
  }

  /** request validation 실패는 usecase 호출 전 공통 INVALID_REQUEST 응답으로 닫힙니다. */
  @Test
  void returnsInvalidRequestProblemDetailForRequestValidationFailure() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/auth/password")
                .principal(ACCOUNT_PUBLIC_ID::toString)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/password"));

    assertThat(useCase.lastCommand).isNull();
  }

  /** current password 불일치는 공통 Problem Details 응답으로 변환됩니다. */
  @Test
  void returnsUnauthorizedProblemDetailForCurrentPasswordMismatch() throws Exception {
    useCase.nextException = new ApiException(AccountErrorCode.CURRENT_PASSWORD_MISMATCH);

    mockMvc
        .perform(
            put("/api/v1/auth/password")
                .principal(ACCOUNT_PUBLIC_ID::toString)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currentPassword": "wrong current password",
                      "newPassword": "new correct horse battery staple",
                      "newPasswordConfirmation": "new correct horse battery staple"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/password"));
  }

  @TestConfiguration
  static class TestUseCaseConfig {

    @Bean
    FakeChangePasswordUseCase fakeChangePasswordUseCase() {
      return new FakeChangePasswordUseCase();
    }
  }

  /** Web adapter가 principal/request 변환과 오류 응답 연결만 담당하는지 확인하기 위한 usecase 대역입니다. */
  static class FakeChangePasswordUseCase implements ChangePasswordUseCase {

    private ApiException nextException;
    private ChangePasswordCommand lastCommand;

    private void reset() {
      nextException = null;
      lastCommand = null;
    }

    @Override
    public void execute(ChangePasswordCommand command) {
      lastCommand = command;
      if (nextException != null) {
        throw nextException;
      }
    }
  }
}
