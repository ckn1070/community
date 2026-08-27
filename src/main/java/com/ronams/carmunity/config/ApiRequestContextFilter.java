package com.ronams.carmunity.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * {@code /api/**} 요청마다 request id와 MDC를 먼저 준비합니다.
 *
 * <p>이 필터는 Security보다 앞에서 request id를 request attribute, response header, MDC에 바인딩해 401/403 같은 필터
 * 단계 오류도 같은 id로 추적합니다.
 */
// Sonar java:S1075는 URI/path 하드코딩을 경고합니다.
// `/api/`는 환경별 설정값이 아니라 request id/MDC를 적용할 HTTP API 경계입니다.
// 이 필터는 API 요청만 선별해야 하므로 prefix를 코드 상수로 둡니다.
@SuppressWarnings("java:S1075")
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRequestContextFilter extends OncePerRequestFilter {

  private static final String API_PATH_PREFIX = "/api/";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(API_PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = ApiRequestIdContext.resolve(request);
    ApiRequestIdContext.bind(request, response, requestId);
    long startedAtNanos = System.nanoTime();
    boolean downstreamFailed = false;

    try {
      filterChain.doFilter(request, response);
    } catch (IOException | ServletException | RuntimeException | Error exception) {
      downstreamFailed = true;
      throw exception;
    } finally {
      // 하위 필터가 오류 응답을 만들더라도 같은 request id가 응답 헤더에 남아야 합니다.
      response.setHeader(ApiRequestIdContext.REQUEST_ID_HEADER, requestId);
      try {
        logCompletion(request, response, requestId, startedAtNanos, downstreamFailed);
      } finally {
        ApiRequestIdContext.clear();
      }
    }
  }

  private void logCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      String requestId,
      long startedAtNanos,
      boolean downstreamFailed) {
    String route = matchedRoute(request);
    String path = request.getRequestURI();
    String operation = request.getMethod() + " " + route;
    int status = downstreamFailed && response.getStatus() < 500 ? 500 : response.getStatus();
    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    String result = resultFor(status);
    String actorId = ApiRequestLogContext.actorId(request).orElse("-");
    String errorCode = ApiRequestLogContext.errorCode(request).orElse("-");

    LoggingEventBuilder event =
        log.atInfo()
            .addKeyValue("event", "api.request.completed")
            .addKeyValue("requestId", requestId)
            .addKeyValue("method", request.getMethod())
            .addKeyValue("path", path)
            .addKeyValue("route", route)
            .addKeyValue("operation", operation)
            .addKeyValue("status", status)
            .addKeyValue("result", result)
            .addKeyValue("durationMs", durationMs);

    ApiRequestLogContext.actorId(request).ifPresent(value -> event.addKeyValue("actorId", value));
    ApiRequestLogContext.errorCode(request)
        .ifPresent(value -> event.addKeyValue("errorCode", value));

    event.log(
        "API 요청 처리를 완료했습니다. event={}, requestId={}, method={}, path={}, route={}, operation={}, status={}, result={}, durationMs={}, actorId={}, errorCode={}",
        "api.request.completed",
        requestId,
        request.getMethod(),
        path,
        route,
        operation,
        status,
        result,
        durationMs,
        actorId,
        errorCode);
  }

  private String matchedRoute(HttpServletRequest request) {
    Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return route instanceof String matchedRoute ? matchedRoute : "unmatched";
  }

  private String resultFor(int status) {
    if (status >= 500) {
      return "failure";
    }
    if (status >= 400) {
      return "rejected";
    }
    return "success";
  }
}
