package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.RegisterMemberAccountCommand;
import com.ronams.carmunity.account.internal.application.port.in.RegisterMemberAccountUseCase;
import com.ronams.carmunity.account.internal.application.result.RegisterMemberAccountResult;
import com.ronams.carmunity.account.internal.domain.AccountStatus;
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

/** 회원가입 public API의 요청/응답 계약과 공통 오류 응답 연결을 검증합니다. */
@WebMvcTest(controllers = RegisterMemberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  RegisterMemberControllerTest.TestUseCaseConfig.class
})
class RegisterMemberControllerTest {

  private static final UUID MEMBER_ID = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");

  @Autowired private MockMvc mockMvc;
  @Autowired private FakeRegisterMemberAccountUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase.reset();
  }

  /** controller는 request를 command로 넘기고 생성된 resource 표현과 Location header만 조립합니다. */
  @Test
  void createsMemberAndReturnsCreatedResourceRepresentation() throws Exception {
    useCase.nextResult =
        new RegisterMemberAccountResult(
            MEMBER_ID, "user@example.com", "Road Runner", AccountStatus.PENDING);

    mockMvc
        .perform(
            post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "USER@example.com",
                      "password": "passphrase",
                      "displayName": "Road Runner"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/members/" + MEMBER_ID))
        .andExpect(jsonPath("$.memberId").value(MEMBER_ID.toString()))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.displayName").value("Road Runner"))
        .andExpect(jsonPath("$.status").value("PENDING"));

    assertThat(useCase.lastCommand.email()).isEqualTo("USER@example.com");
    assertThat(useCase.lastCommand.password()).isEqualTo("passphrase");
    assertThat(useCase.lastCommand.displayName()).isEqualTo("Road Runner");
  }

  /** request validation 실패는 usecase 호출 전 공통 Problem Details 응답으로 닫혀야 합니다. */
  @Test
  void returnsInvalidRequestProblemDetailForRequestValidationFailure() throws Exception {
    mockMvc
        .perform(post("/api/v1/members").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.instance").value("/api/v1/members"));

    assertThat(useCase.lastCommand).isNull();
  }

  /** application 예외는 controller 밖 공통 handler를 통해 동일한 conflict schema로 매핑합니다. */
  @Test
  void returnsConflictProblemDetailForDuplicatedEmail() throws Exception {
    useCase.nextException = new ApiException(AccountErrorCode.EMAIL_ALREADY_USED);

    mockMvc
        .perform(
            post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "used@example.com",
                      "password": "correct horse battery staple",
                      "displayName": "Road Runner"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"))
        .andExpect(jsonPath("$.instance").value("/api/v1/members"));
  }

  @TestConfiguration
  static class TestUseCaseConfig {

    @Bean
    FakeRegisterMemberAccountUseCase fakeRegisterMemberAccountUseCase() {
      return new FakeRegisterMemberAccountUseCase();
    }
  }

  /**
   * Web adapter의 command 변환과 오류 응답 연결 검증에 필요한 결과/예외와 입력 command를 기록하는 usecase 대역입니다.
   *
   * <p>domain/application 검증은 다른 테스트가 담당하므로 여기서는 입력 command와 주입된 결과/예외만 기록합니다.
   */
  static class FakeRegisterMemberAccountUseCase implements RegisterMemberAccountUseCase {

    private RegisterMemberAccountResult nextResult;
    private ApiException nextException;
    private RegisterMemberAccountCommand lastCommand;

    private void reset() {
      nextResult = null;
      nextException = null;
      lastCommand = null;
    }

    @Override
    public RegisterMemberAccountResult execute(RegisterMemberAccountCommand command) {
      lastCommand = command;
      if (nextException != null) {
        throw nextException;
      }
      return nextResult;
    }
  }
}
