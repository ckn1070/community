package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 인증된 API 요청의 공개 account 식별자가 완료 로그 상관관계로 전달되는지 검증합니다. */
class ApiRequestActorContextInterceptorTest {

  private final ApiRequestActorContextInterceptor interceptor =
      new ApiRequestActorContextInterceptor();

  /** JWT subject인 account public id는 현재 요청의 actor id로 보존해야 합니다. */
  @Test
  void bindsUuidPrincipalNameAsActorId() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/protected");
    UUID actorId = UUID.fromString("b1fb8101-e7a2-4115-8f3a-2d513d3688eb");
    request.setUserPrincipal(principal(actorId.toString()));

    interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(ApiRequestLogContext.actorId(request)).contains(actorId.toString());
  }

  /** email 같은 principal 이름은 개인정보가 될 수 있으므로 명시적 공개 id 계약 없이 로그에 넣지 않습니다. */
  @Test
  void ignoresNonUuidPrincipalName() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/protected");
    request.setUserPrincipal(principal("member@example.com"));

    interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(ApiRequestLogContext.actorId(request)).isEmpty();
  }

  private Principal principal(String name) {
    return () -> name;
  }
}
