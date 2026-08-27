package com.ronams.carmunity.config;

import com.ronams.carmunity.shared.error.ErrorCode;
import com.ronams.carmunity.shared.error.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * {@link ErrorCode}를 RFC 9457 Problem Details 표현으로 변환합니다.
 *
 * <p>{@code type} URI와 {@code code} extension은 모든 API 오류 응답에서 유지되는 클라이언트 계약입니다.
 */
@Component
public class ApiErrorResponseFactory {

  private static final String PROBLEM_TYPE_PREFIX = "https://api.car-community.example/problems/";

  /**
   * 필드 오류가 없는 공통 오류 응답을 생성합니다.
   *
   * @param errorCode 클라이언트에 노출할 오류 계약입니다.
   * @param request instance URI를 추출할 현재 HTTP 요청입니다.
   * @return 공통 extension field를 포함한 Problem Details 응답 본문입니다.
   */
  public ProblemDetail create(ErrorCode errorCode, WebRequest request) {
    return create(errorCode, request, List.of());
  }

  /**
   * validation 필드 오류를 포함한 공통 오류 응답을 생성합니다.
   *
   * @param errorCode 클라이언트에 노출할 오류 계약입니다.
   * @param request instance URI를 추출할 현재 HTTP 요청입니다.
   * @param fieldErrors 클라이언트가 수정할 수 있는 request field 오류 목록입니다.
   * @return 공통 extension field와 field error 목록을 포함한 Problem Details 응답 본문입니다.
   */
  public ProblemDetail create(
      ErrorCode errorCode, WebRequest request, List<FieldErrorDetail> fieldErrors) {
    ProblemDetail problemDetail = createBase(errorCode);
    setInstance(problemDetail, request);
    bindErrorCode(errorCode, request);
    setFieldErrors(problemDetail, fieldErrors);
    return problemDetail;
  }

  /**
   * Security filter 단계에서 사용할 공통 오류 응답을 생성합니다.
   *
   * @param errorCode 클라이언트에 노출할 오류 계약입니다.
   * @param request instance URI를 추출할 현재 servlet 요청입니다.
   * @return Security handler가 직접 직렬화할 Problem Details 응답 본문입니다.
   */
  public ProblemDetail create(ErrorCode errorCode, HttpServletRequest request) {
    ProblemDetail problemDetail = createBase(errorCode);
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    ApiRequestLogContext.bindErrorCode(request, errorCode.code());
    return problemDetail;
  }

  private ProblemDetail createBase(ErrorCode errorCode) {
    ProblemDetail problemDetail = ProblemDetail.forStatus(errorCode.status());
    problemDetail.setType(URI.create(PROBLEM_TYPE_PREFIX + toKebabCase(errorCode.code())));
    problemDetail.setTitle(errorCode.title());
    problemDetail.setDetail(errorCode.detail());
    problemDetail.setProperty("code", errorCode.code());
    return problemDetail;
  }

  private void setInstance(ProblemDetail problemDetail, WebRequest request) {
    if (request instanceof ServletWebRequest servletWebRequest) {
      problemDetail.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
    }
  }

  private void bindErrorCode(ErrorCode errorCode, WebRequest request) {
    if (request instanceof ServletWebRequest servletWebRequest) {
      ApiRequestLogContext.bindErrorCode(servletWebRequest.getRequest(), errorCode.code());
    }
  }

  private void setFieldErrors(ProblemDetail problemDetail, List<FieldErrorDetail> fieldErrors) {
    if (!fieldErrors.isEmpty()) {
      problemDetail.setProperty("errors", fieldErrors);
    }
  }

  private String toKebabCase(String code) {
    return code.toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
