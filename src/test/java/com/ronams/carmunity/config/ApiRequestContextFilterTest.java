package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

/**
 * API 요청 상관관계 filter가 request id와 MDC 수명을 올바르게 관리하는지 검증합니다.
 *
 * <p>이 테스트가 깨지면 요청별 로그 상관관계가 사라지거나, 이전 요청의 MDC 값이 다음 요청에 누출될 수 있습니다.
 */
class ApiRequestContextFilterTest {

  private final ApiRequestContextFilter filter = new ApiRequestContextFilter();

  /** request id가 없으면 서버가 새 값을 만들고, 같은 값이 request attribute, MDC, 응답 header에 연결되어야 합니다. */
  @Test
  void generatesRequestIdAndBindsItToRequestMdcAndResponseHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RequestState requestState = new RequestState();

    FilterChain chain =
        (servletRequest, servletResponse) -> {
          requestState.requestIdInAttribute =
              (String)
                  servletRequest.getAttribute(ApiRequestIdContext.REQUEST_ID_REQUEST_ATTRIBUTE);
          requestState.requestIdInMdc = MDC.get(ApiRequestIdContext.REQUEST_ID_MDC_KEY);
        };

    filter.doFilter(request, response, chain);

    assertThat(requestState.requestIdInAttribute).isNotBlank();
    assertThat(requestState.requestIdInMdc).isEqualTo(requestState.requestIdInAttribute);
    assertThat(response.getHeader(ApiRequestIdContext.REQUEST_ID_HEADER))
        .isEqualTo(requestState.requestIdInAttribute);
    assertThat(MDC.get(ApiRequestIdContext.REQUEST_ID_MDC_KEY)).isNull();
  }

  /** 안전한 inbound `X-Request-Id`는 서버가 바꾸지 않고 같은 값을 그대로 반환해야 합니다. */
  @Test
  void reusesSafeInboundRequestId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader(ApiRequestIdContext.REQUEST_ID_HEADER, "client-request-id_123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RequestState requestState = new RequestState();

    FilterChain chain =
        (servletRequest, servletResponse) -> {
          requestState.requestIdInAttribute =
              (String)
                  servletRequest.getAttribute(ApiRequestIdContext.REQUEST_ID_REQUEST_ATTRIBUTE);
          requestState.requestIdInMdc = MDC.get(ApiRequestIdContext.REQUEST_ID_MDC_KEY);
        };

    filter.doFilter(request, response, chain);

    assertThat(requestState.requestIdInAttribute).isEqualTo("client-request-id_123");
    assertThat(requestState.requestIdInMdc).isEqualTo("client-request-id_123");
    assertThat(response.getHeader(ApiRequestIdContext.REQUEST_ID_HEADER))
        .isEqualTo("client-request-id_123");
    assertThat(MDC.get(ApiRequestIdContext.REQUEST_ID_MDC_KEY)).isNull();
  }

  /** 허용하지 않는 형식의 inbound request id는 신뢰하지 않고 새 서버 생성 값으로 대체해야 합니다. */
  @Test
  void replacesUnsafeInboundRequestIdWithGeneratedValue() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader(ApiRequestIdContext.REQUEST_ID_HEADER, "bad request id");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RequestState requestState = new RequestState();

    FilterChain chain =
        (servletRequest, servletResponse) -> {
          requestState.requestIdInAttribute =
              (String)
                  servletRequest.getAttribute(ApiRequestIdContext.REQUEST_ID_REQUEST_ATTRIBUTE);
        };

    filter.doFilter(request, response, chain);

    assertThat(requestState.requestIdInAttribute).isNotBlank().isNotEqualTo("bad request id");
    assertThat(response.getHeader(ApiRequestIdContext.REQUEST_ID_HEADER))
        .isEqualTo(requestState.requestIdInAttribute);
    assertThat(MDC.get(ApiRequestIdContext.REQUEST_ID_MDC_KEY)).isNull();
  }

  /** 정상 API 요청도 운영자가 고객 문의를 재구성할 수 있도록 완료 결과와 처리 시간을 한 번 기록해야 합니다. */
  @Test
  void logsStructuredCompletionForSuccessfulApiRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/members");
    request.addHeader(ApiRequestIdContext.REQUEST_ID_HEADER, "request-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    ListAppender<ILoggingEvent> appender = attachListAppender();

    FilterChain chain =
        (servletRequest, servletResponse) -> {
          servletRequest.setAttribute(
              HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/members");
          ((MockHttpServletResponse) servletResponse).setStatus(201);
        };

    try {
      filter.doFilter(request, response, chain);
    } finally {
      detachListAppender(appender);
    }

    assertThat(appender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.INFO);
              assertThat(event.getFormattedMessage())
                  .contains("API 요청 처리를 완료했습니다.")
                  .contains("event=api.request.completed")
                  .contains("requestId=request-123")
                  .contains("status=201")
                  .contains("result=success");
              assertThat(keyValues(event))
                  .containsEntry("event", "api.request.completed")
                  .containsEntry("requestId", "request-123")
                  .containsEntry("method", "POST")
                  .containsEntry("path", "/api/v1/members")
                  .containsEntry("route", "/api/v1/members")
                  .containsEntry("operation", "POST /api/v1/members")
                  .containsEntry("status", 201)
                  .containsEntry("result", "success")
                  .containsKey("durationMs");
            });
  }

  /** 업무 거부 응답은 고객과 오류 원인을 연결할 actor id와 공개 error code를 완료 로그에 포함해야 합니다. */
  @Test
  void logsActorAndErrorCodeForRejectedApiRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/protected");
    request.addHeader(ApiRequestIdContext.REQUEST_ID_HEADER, "request-rejected");
    MockHttpServletResponse response = new MockHttpServletResponse();
    ListAppender<ILoggingEvent> appender = attachListAppender();
    UUID actorId = UUID.fromString("b1fb8101-e7a2-4115-8f3a-2d513d3688eb");

    FilterChain chain =
        (servletRequest, servletResponse) -> {
          servletRequest.setAttribute(
              HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/protected");
          ApiRequestLogContext.bindActor(
              (MockHttpServletRequest) servletRequest, actorId.toString());
          ApiRequestLogContext.bindErrorCode(
              (MockHttpServletRequest) servletRequest, "ACCOUNT_NOT_ACTIVE");
          ((MockHttpServletResponse) servletResponse).setStatus(409);
        };

    try {
      filter.doFilter(request, response, chain);
    } finally {
      detachListAppender(appender);
    }

    assertThat(keyValues(appender.list.getFirst()))
        .containsEntry("actorId", actorId.toString())
        .containsEntry("errorCode", "ACCOUNT_NOT_ACTIVE")
        .containsEntry("status", 409)
        .containsEntry("result", "rejected");
  }

  /** 하위 처리에서 예외가 나더라도 응답 header는 남기고 MDC는 반드시 정리해야 합니다. */
  @Test
  void clearsMdcAndKeepsResponseHeaderWhenDownstreamThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    MockHttpServletResponse response = new MockHttpServletResponse();

    FilterChain chain =
        (servletRequest, servletResponse) -> {
          assertThat(MDC.get(ApiRequestIdContext.REQUEST_ID_MDC_KEY)).isNotBlank();
          throw new IllegalStateException("downstream failure");
        };

    assertThatThrownBy(() -> filter.doFilter(request, response, chain))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("downstream failure");
    assertThat(response.getHeader(ApiRequestIdContext.REQUEST_ID_HEADER)).isNotBlank();
    assertThat(MDC.get(ApiRequestIdContext.REQUEST_ID_MDC_KEY)).isNull();
  }

  /** 공통 handler 밖으로 예외가 전파되어도 완료 로그는 성공으로 오분류하지 않고 500 실패로 남겨야 합니다. */
  @Test
  void logsFailureWhenDownstreamExceptionEscapes() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    MockHttpServletResponse response = new MockHttpServletResponse();
    ListAppender<ILoggingEvent> appender = attachListAppender();

    FilterChain chain =
        (servletRequest, servletResponse) -> {
          throw new IllegalStateException("downstream failure");
        };

    try {
      assertThatThrownBy(() -> filter.doFilter(request, response, chain))
          .isInstanceOf(IllegalStateException.class);
    } finally {
      detachListAppender(appender);
    }

    assertThat(keyValues(appender.list.getFirst()))
        .containsEntry("status", 500)
        .containsEntry("result", "failure");
  }

  private ListAppender<ILoggingEvent> attachListAppender() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger().addAppender(appender);
    return appender;
  }

  private void detachListAppender(ListAppender<ILoggingEvent> appender) {
    logger().detachAppender(appender);
    appender.stop();
  }

  private Logger logger() {
    return (Logger) LoggerFactory.getLogger(ApiRequestContextFilter.class);
  }

  private Map<String, Object> keyValues(ILoggingEvent event) {
    return event.getKeyValuePairs().stream()
        .collect(Collectors.toMap(keyValue -> keyValue.key, keyValue -> keyValue.value));
  }

  private static final class RequestState {
    private String requestIdInAttribute;
    private String requestIdInMdc;
  }
}
