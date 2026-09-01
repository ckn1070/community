package com.ronams.carmunity.community.internal.adapter.in.web.protection;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CommunityPublicRateLimitFilterTest {
  @Test
  void ignoresSpoofedForwardedHeaderFromUntrustedPeer() throws Exception {
    var filter = new CommunityPublicRateLimitFilter(new SimpleMeterRegistry(), "10.0.0.0/8");
    MockHttpServletResponse last = null;
    for (int index = 0; index < 21; index++) {
      var request = request("203.0.113.10", "198.51.100." + index);
      last = new MockHttpServletResponse();
      filter.doFilter(request, last, new MockFilterChain());
    }
    assertThat(last.getStatus()).isEqualTo(429);
    assertThat(last.getHeader("Retry-After")).isEqualTo("1");
  }

  @Test
  void trustsForwardedClientOnlyFromConfiguredProxy() throws Exception {
    var filter = new CommunityPublicRateLimitFilter(new SimpleMeterRegistry(), "10.0.0.0/8");
    for (int index = 0; index < 21; index++) {
      var response = new MockHttpServletResponse();
      filter.doFilter(request("10.0.0.4", "198.51.100." + index), response, new MockFilterChain());
      assertThat(response.getStatus()).isLessThan(400);
    }
  }

  @Test
  void usesRightmostUntrustedHopWhenClientPrependsSpoofedAddress() throws Exception {
    var filter = new CommunityPublicRateLimitFilter(new SimpleMeterRegistry(), "10.0.0.0/8");
    MockHttpServletResponse last = null;
    for (int index = 0; index < 21; index++) {
      var request = request("10.0.0.4", "198.51.100." + index + ", 203.0.113.8");
      last = new MockHttpServletResponse();
      filter.doFilter(request, last, new MockFilterChain());
    }
    assertThat(last.getStatus()).isEqualTo(429);
  }

  private MockHttpServletRequest request(String peer, String forwarded) {
    var request = new MockHttpServletRequest("GET", "/api/v1/boards/free/posts");
    request.setRemoteAddr(peer);
    request.addHeader("X-Forwarded-For", forwarded);
    return request;
  }
}
