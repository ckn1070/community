package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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

  private static final class RequestState {
    private String requestIdInAttribute;
    private String requestIdInMdc;
  }
}
