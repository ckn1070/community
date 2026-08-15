package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Spring MVC 예외가 공통 Problem Details 응답으로 매핑되는 계약을 검증합니다.
 *
 * <p>테스트 Controller는 실제 API가 아니라 exception handler의 오류 taxonomy를 고정하는 fixture입니다.
 */
@WebMvcTest(controllers = ApiExceptionHandlerTest.ErrorTestController.class)
// Security 필터를 끄고 MVC exception handler의 매핑 정책만 독립적으로 검증합니다.
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(OutputCaptureExtension.class)
@Import({
  ApiExceptionHandlerTest.ErrorTestController.class,
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class
})
class ApiExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  /** DTO validation 실패는 400 INVALID_REQUEST와 field error 목록으로 노출되어야 합니다. */
  @Test
  void returnsProblemDetailForRequestValidationFailure() throws Exception {
    mockMvc
        .perform(
            post("/test/errors/validation").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(
            jsonPath("$.type").value("https://api.car-community.example/problems/invalid-request"))
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("요청 값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.instance").value("/test/errors/validation"))
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("name"))
        .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));
  }

  /** JSON body를 읽을 수 없는 경우도 클라이언트 요청 형식 오류이므로 400 INVALID_REQUEST로 고정합니다. */
  @Test
  void returnsProblemDetailForJsonParseFailure() throws Exception {
    mockMvc
        .perform(
            post("/test/errors/validation").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  /** 지원하지 않는 Content-Type은 요청 body validation과 구분해 415로 응답해야 합니다. */
  @Test
  void returnsProblemDetailForUnsupportedContentType() throws Exception {
    mockMvc
        .perform(post("/test/errors/validation").contentType(MediaType.TEXT_PLAIN).content("name"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(415))
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
        .andExpect(jsonPath("$.instance").value("/test/errors/validation"));
  }

  /** 업무 경계에서 던진 ApiException은 각 ErrorCode의 status/code/detail을 그대로 응답해야 합니다. */
  @Test
  void returnsProblemDetailForApiException() throws Exception {
    mockMvc
        .perform(get("/test/errors/conflict"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(
            jsonPath("$.type")
                .value("https://api.car-community.example/problems/email-already-used"))
        .andExpect(jsonPath("$.title").value("Email already used"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.instance").value("/test/errors/conflict"))
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"))
        .andExpect(jsonPath("$.detail").value("이미 사용 중인 이메일입니다."));
  }

  /** 미분류 예외는 500 INTERNAL_ERROR로 축약하고 내부 예외 메시지를 클라이언트에 노출하지 않습니다. */
  @Test
  void returnsInternalErrorWithoutLeakingExceptionDetails() throws Exception {
    mockMvc
        .perform(get("/test/errors/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.detail").value("예상하지 못한 서버 오류입니다."))
        .andExpect(jsonPath("$.detail").value(not(containsString("database-password-secret"))))
        .andExpect(content().string(not(containsString("database-password-secret"))));
  }

  /** 미분류 예외 로그에는 request id와 request 정보만 남기고, 예외 메시지 원문 같은 민감정보는 남기지 않아야 합니다. */
  @Test
  void logsUnexpectedExceptionWithRequestContextWithoutLeakingSecret(CapturedOutput output) {
    ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(new ApiErrorResponseFactory());
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/test/errors/unexpected");
    request.setAttribute(ApiRequestIdContext.REQUEST_ID_REQUEST_ATTRIBUTE, "request-123");

    exceptionHandler.handleUnexpectedException(
        new IllegalStateException("database-password-secret"), new ServletWebRequest(request));

    assertThat(output.getOut()).contains("request-123");
    assertThat(output.getOut()).contains("method=GET");
    assertThat(output.getOut()).contains("path=/api/test/errors/unexpected");
    assertThat(output.getOut()).contains("errorCode=INTERNAL_ERROR");
    assertThat(output.getOut()).doesNotContain("database-password-secret");
  }

  /** exception handler 매핑만 검증하는 fixture Controller입니다. 실제 API endpoint가 아닙니다. */
  @RestController
  public static class ErrorTestController {

    @PostMapping("/test/errors/validation")
    TestRequest validate(@Valid @RequestBody TestRequest request) {
      return request;
    }

    @GetMapping("/test/errors/conflict")
    void conflict() {
      throw new ApiException(TestErrorCode.EMAIL_ALREADY_USED);
    }

    @GetMapping("/test/errors/unexpected")
    void unexpected() {
      throw new IllegalStateException("database-password-secret");
    }
  }

  record TestRequest(@NotBlank String name) {}

  /** 업무 모듈이 제공할 ErrorCode를 대신하는 테스트 전용 오류 코드입니다. */
  enum TestErrorCode implements ErrorCode {
    EMAIL_ALREADY_USED;

    @Override
    public String code() {
      return name();
    }

    @Override
    public HttpStatus status() {
      return HttpStatus.CONFLICT;
    }

    @Override
    public String title() {
      return "Email already used";
    }

    @Override
    public String detail() {
      return "이미 사용 중인 이메일입니다.";
    }
  }
}
