package com.ronams.carmunity.config;

import com.ronams.carmunity.shared.error.CommonErrorCode;
import com.ronams.carmunity.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Security 필터 단계의 인증/인가 실패를 공통 Problem Details 응답으로 기록합니다.
 *
 * <p>Security 예외는 MVC {@code @ExceptionHandler} 흐름에 도달하지 않으므로 여기서 같은 schema를 맞춥니다.
 */
@RequiredArgsConstructor
@Component
public class ApiSecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final ApiErrorResponseFactory responseFactory;

  /** 인증되지 않은 보호 API 요청을 401 Problem Details 응답으로 종료합니다. */
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    write(request, response, CommonErrorCode.UNAUTHORIZED);
  }

  /** 인증은 됐지만 권한이 부족한 API 요청을 403 Problem Details 응답으로 종료합니다. */
  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      ApiRequestLogContext.bindActor(request, authentication.getName());
    }
    write(request, response, CommonErrorCode.FORBIDDEN);
  }

  // Security filter 단계는 MVC message converter를 거치지 않으므로 HTTP 상태와 JSON media type을 직접 고정합니다.
  private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
      throws IOException {
    ProblemDetail problemDetail = responseFactory.create(errorCode, request);
    response.setStatus(problemDetail.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(), toBody(problemDetail));
  }

  // Security filter에서는 MVC message converter가 동작하지 않으므로 extension field까지 직접 직렬화합니다.
  private Map<String, Object> toBody(ProblemDetail problemDetail) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", problemDetail.getType().toString());
    body.put("title", problemDetail.getTitle());
    body.put("status", problemDetail.getStatus());
    body.put("detail", problemDetail.getDetail());
    body.put("instance", problemDetail.getInstance().toString());
    body.putAll(problemDetail.getProperties());
    return body;
  }
}
