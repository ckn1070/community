package com.ronams.carmunity.auth.internal.adapter.in.web.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.auth.internal.application.AuthErrorCode;
import com.ronams.carmunity.auth.internal.application.command.CreatePasswordSessionCommand;
import com.ronams.carmunity.auth.internal.application.port.in.CreatePasswordSessionUseCase;
import com.ronams.carmunity.auth.internal.application.result.CreatedSessionResult;
import com.ronams.carmunity.auth.internal.application.service.SessionAccess;
import com.ronams.carmunity.config.ApiErrorResponseFactory;
import com.ronams.carmunity.config.ApiExceptionHandler;
import com.ronams.carmunity.shared.error.ApiException;
import java.time.Instant;
import java.util.List;
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

/** 로그인 public API의 요청/응답 계약과 공통 오류 응답 연결을 검증합니다. */
@WebMvcTest(controllers = CreatePasswordSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  CreatePasswordSessionControllerTest.TestUseCaseConfig.class
})
class CreatePasswordSessionControllerTest {

  private static final UUID ACCOUNT_ID = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeCreatePasswordSessionUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase.reset();
  }

  /** controller는 request를 command로 넘기고 token 원문을 응답 body에 한 번만 포함합니다. */
  @Test
  void createsPasswordSessionAndReturnsTokenResponse() throws Exception {
    useCase.nextResult =
        new CreatedSessionResult(
            "access-token",
            "Bearer",
            Instant.parse("2026-06-22T00:15:00Z"),
            "refresh-token",
            Instant.parse("2026-07-22T00:00:00Z"),
            ACCOUNT_ID,
            List.of("ROLE_MEMBER"),
            SessionAccess.FULL);

    mockMvc
        .perform(
            post("/api/v1/auth/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "USER@example.com",
                      "password": "correct horse battery staple"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.accessTokenExpiresAt").value("2026-06-22T00:15:00Z"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
        .andExpect(jsonPath("$.refreshTokenExpiresAt").value("2026-07-22T00:00:00Z"))
        .andExpect(jsonPath("$.memberId").value(ACCOUNT_ID.toString()))
        .andExpect(jsonPath("$.roles[0]").value("ROLE_MEMBER"))
        .andExpect(jsonPath("$.sessionAccess").value("FULL"));

    assertThat(useCase.lastCommand.email()).isEqualTo("USER@example.com");
    assertThat(useCase.lastCommand.password()).isEqualTo("correct horse battery staple");
  }

  /** request validation 실패는 password 검증 전 공통 INVALID_REQUEST 응답으로 닫혀야 합니다. */
  @Test
  void returnsInvalidRequestProblemDetailForRequestValidationFailure() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/sessions"));

    assertThat(useCase.lastCommand).isNull();
  }

  /** credential 실패는 account 존재 여부나 password 불일치 이유를 구분하지 않는 401 계약으로 응답합니다. */
  @Test
  void returnsUnauthorizedProblemDetailForInvalidCredentials() throws Exception {
    useCase.nextException = new ApiException(AuthErrorCode.INVALID_CREDENTIALS);

    mockMvc
        .perform(
            post("/api/v1/auth/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "user@example.com",
                      "password": "wrong password"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/sessions"));
  }

  @TestConfiguration
  static class TestUseCaseConfig {

    @Bean
    FakeCreatePasswordSessionUseCase fakeCreatePasswordSessionUseCase() {
      return new FakeCreatePasswordSessionUseCase();
    }
  }

  /** Web adapter의 command 변환과 오류 응답 연결 검증에 필요한 결과/예외와 입력 command를 기록하는 usecase 대역입니다. */
  static class FakeCreatePasswordSessionUseCase implements CreatePasswordSessionUseCase {

    private CreatedSessionResult nextResult;
    private ApiException nextException;
    private CreatePasswordSessionCommand lastCommand;

    private void reset() {
      nextResult = null;
      nextException = null;
      lastCommand = null;
    }

    @Override
    public CreatedSessionResult execute(CreatePasswordSessionCommand command) {
      lastCommand = command;
      if (nextException != null) {
        throw nextException;
      }
      return nextResult;
    }
  }
}
