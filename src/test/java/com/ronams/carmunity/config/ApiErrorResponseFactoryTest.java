package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.shared.error.CommonErrorCode;
import com.ronams.carmunity.shared.error.FieldErrorDetail;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/** 공통 오류 응답 factory가 handler들에 제공하는 Problem Details 계약을 검증합니다. */
class ApiErrorResponseFactoryTest {

  private final ApiErrorResponseFactory responseFactory = new ApiErrorResponseFactory();

  /** 공통 오류 응답이 RFC 9457 기본 필드와 프로젝트 `code` extension을 포함하는지 검증합니다. */
  @Test
  void createsProblemDetailWithCommonFields() {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/v1/members");
    WebRequest request = new ServletWebRequest(servletRequest);

    var problemDetail = responseFactory.create(CommonErrorCode.INVALID_REQUEST, request);

    assertThat(problemDetail.getType())
        .hasToString("https://api.car-community.example/problems/invalid-request");
    assertThat(problemDetail.getTitle()).isEqualTo("Invalid request");
    assertThat(problemDetail.getStatus()).isEqualTo(400);
    assertThat(problemDetail.getDetail()).isEqualTo("요청 값이 올바르지 않습니다.");
    assertThat(problemDetail.getInstance()).hasToString("/api/v1/members");
    assertThat(problemDetail.getProperties()).containsEntry("code", "INVALID_REQUEST");
    assertThat(ApiRequestLogContext.errorCode(servletRequest)).contains("INVALID_REQUEST");
  }

  /** validation 오류는 클라이언트가 수정할 수 있는 필드 목록만 `errors` extension으로 노출합니다. */
  @Test
  void createsProblemDetailWithFieldErrors() {
    WebRequest request =
        new ServletWebRequest(new MockHttpServletRequest("POST", "/api/v1/members"));
    List<FieldErrorDetail> fieldErrors =
        List.of(new FieldErrorDetail("email", "NotBlank", "비어 있을 수 없습니다."));

    var problemDetail =
        responseFactory.create(CommonErrorCode.INVALID_REQUEST, request, fieldErrors);

    assertThat(problemDetail.getProperties())
        .containsEntry("code", "INVALID_REQUEST")
        .containsEntry("errors", fieldErrors);
  }

  /** Security filter 단계에서도 servlet request 경로를 Problem Details `instance`에 보존해야 합니다. */
  @Test
  void createsProblemDetailFromServletRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/example");

    var problemDetail = responseFactory.create(CommonErrorCode.FORBIDDEN, request);

    assertThat(problemDetail.getStatus()).isEqualTo(403);
    assertThat(problemDetail.getProperties()).containsEntry("code", "FORBIDDEN");
    assertThat(problemDetail.getInstance()).hasToString("/api/admin/example");
    assertThat(ApiRequestLogContext.errorCode(request)).contains("FORBIDDEN");
  }
}
