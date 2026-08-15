package com.ronams.carmunity.auth.internal.adapter.in.web.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.auth.internal.application.command.LogoutCommand;
import com.ronams.carmunity.auth.internal.application.port.in.LogoutUseCase;
import com.ronams.carmunity.config.ApiErrorResponseFactory;
import com.ronams.carmunity.config.ApiExceptionHandler;
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

/** 로그아웃 public API의 요청 validation과 204 응답 계약을 검증합니다. */
@WebMvcTest(controllers = LogoutController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  LogoutControllerTest.TestUseCaseConfig.class
})
class LogoutControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeLogoutUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase.reset();
  }

  /** controller는 refresh token 원문을 command로 넘기되 응답 body에는 token 정보를 남기지 않습니다. */
  @Test
  void logsOutRefreshTokenAndReturnsNoContent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "raw-refresh-token"
                    }
                    """))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertThat(useCase.lastCommand.refreshToken()).isEqualTo("raw-refresh-token");
  }

  /** blank refresh token은 application 호출 전에 공통 INVALID_REQUEST 응답으로 닫습니다. */
  @Test
  void returnsInvalidRequestProblemDetailForBlankRefreshToken() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": ""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/logout"));

    assertThat(useCase.lastCommand).isNull();
  }

  @TestConfiguration
  static class TestUseCaseConfig {

    @Bean
    FakeLogoutUseCase fakeLogoutUseCase() {
      return new FakeLogoutUseCase();
    }
  }

  /**
   * Web adapter가 command 변환과 204 응답 조립만 담당하는지 확인하기 위한 usecase 대역입니다.
   *
   * <p>token 상태별 멱등 처리와 감사 이벤트 판단은 application/service와 persistence 테스트에서 고정합니다.
   */
  static class FakeLogoutUseCase implements LogoutUseCase {

    private LogoutCommand lastCommand;

    private void reset() {
      lastCommand = null;
    }

    @Override
    public void execute(LogoutCommand command) {
      lastCommand = command;
    }
  }
}
