package com.ronams.carmunity.auth.internal.adapter.in.web.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.auth.internal.application.AuthErrorCode;
import com.ronams.carmunity.auth.internal.application.command.RotateRefreshTokenCommand;
import com.ronams.carmunity.auth.internal.application.port.in.RotateRefreshTokenUseCase;
import com.ronams.carmunity.auth.internal.application.result.RotatedRefreshTokenResult;
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

/** refresh token 재발급 public API의 요청/응답 계약과 공통 오류 응답 연결을 검증합니다. */
@WebMvcTest(controllers = RotateRefreshTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  RotateRefreshTokenControllerTest.TestUseCaseConfig.class
})
class RotateRefreshTokenControllerTest {

  private static final UUID MEMBER_ID = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final Instant ACCESS_TOKEN_EXPIRES_AT = Instant.parse("2099-06-23T00:15:00Z");
  private static final Instant REFRESH_TOKEN_EXPIRES_AT = Instant.parse("2099-07-23T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeRotateRefreshTokenUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase.reset();
  }

  /** controller는 refresh token 원문을 command로 넘기고 rotation 결과를 그대로 외부 응답으로 조립합니다. */
  @Test
  void rotatesRefreshTokenAndReturnsNewTokenPair() throws Exception {
    useCase.nextResult =
        new RotatedRefreshTokenResult(
            "access-token",
            "Bearer",
            ACCESS_TOKEN_EXPIRES_AT,
            "new-refresh-token",
            REFRESH_TOKEN_EXPIRES_AT,
            MEMBER_ID,
            List.of("ROLE_MEMBER"));

    mockMvc
        .perform(
            post("/api/v1/auth/sessions/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "old-refresh-token"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.accessTokenExpiresAt").value("2099-06-23T00:15:00Z"))
        .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
        .andExpect(jsonPath("$.refreshTokenExpiresAt").value("2099-07-23T00:00:00Z"))
        .andExpect(jsonPath("$.memberId").value(MEMBER_ID.toString()))
        .andExpect(jsonPath("$.roles[0]").value("ROLE_MEMBER"));

    assertThat(useCase.lastCommand.refreshToken()).isEqualTo("old-refresh-token");
  }

  /** refresh token 원문이 비어 있으면 application 호출 전에 validation 오류로 닫습니다. */
  @Test
  void returnsInvalidRequestProblemDetailForBlankRefreshToken() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/sessions/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": ""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/sessions/refresh"));

    assertThat(useCase.lastCommand).isNull();
  }

  /** refresh token 검증 실패 세부 사유는 숨기고 공통 invalid refresh token 응답으로만 반환합니다. */
  @Test
  void returnsUnauthorizedProblemDetailForInvalidRefreshToken() throws Exception {
    useCase.nextException = new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN);

    mockMvc
        .perform(
            post("/api/v1/auth/sessions/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "stale-refresh-token"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/sessions/refresh"));
  }

  @TestConfiguration
  static class TestUseCaseConfig {

    @Bean
    FakeRotateRefreshTokenUseCase fakeRotateRefreshTokenUseCase() {
      return new FakeRotateRefreshTokenUseCase();
    }
  }

  /**
   * Web adapter의 command 변환과 오류 응답 연결 검증에 필요한 결과/예외와 입력 command를 기록하는 usecase 대역입니다.
   *
   * <p>token lifecycle 판단은 application/service 테스트가 담당하므로 여기서는 입력 command와 주입 결과/예외만 기록합니다.
   */
  static class FakeRotateRefreshTokenUseCase implements RotateRefreshTokenUseCase {

    private RotatedRefreshTokenResult nextResult;
    private ApiException nextException;
    private RotateRefreshTokenCommand lastCommand;

    private void reset() {
      nextResult = null;
      nextException = null;
      lastCommand = null;
    }

    @Override
    public RotatedRefreshTokenResult execute(RotateRefreshTokenCommand command) {
      lastCommand = command;
      if (nextException != null) {
        throw nextException;
      }
      return nextResult;
    }
  }
}
