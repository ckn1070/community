package com.ronams.carmunity.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.auth.internal.adapter.in.web.swagger.StagingSwaggerAuthenticationProvider;
import com.ronams.carmunity.auth.internal.adapter.in.web.swagger.StagingSwaggerSecurityConfig;
import com.ronams.carmunity.auth.internal.adapter.out.security.JwtAuthenticationConverterConfiguration;
import com.ronams.carmunity.auth.internal.adapter.out.security.JwtRoleAuthoritiesConverter;
import com.ronams.carmunity.auth.internal.adapter.out.security.JwtTokenConfiguration;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** staging Swagger 전용 browser session 체인과 무상태 API 체인의 분리를 검증합니다. */
@ActiveProfiles("staging")
@TestPropertySource(
    properties = {
      "carmunity.auth.jwt.issuer=carmunity-api-test",
      "carmunity.auth.jwt.access-token-ttl=15m",
      "carmunity.auth.jwt.hmac-secret=test-hmac-secret-must-be-at-least-32-bytes",
      "carmunity.auth.login.identifier-hash.hmac-secret=test-identifier-secret-must-be-at-least-32-bytes"
    })
@WebMvcTest(controllers = StagingSwaggerSecurityConfigTest.SecurityTestController.class)
@Import({
  ApiRequestActorContextInterceptor.class,
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  ApiRequestContextFilter.class,
  ApiSecurityConfig.class,
  ApiSecurityProblemHandler.class,
  JwtAuthenticationConverterConfiguration.class,
  JwtRoleAuthoritiesConverter.class,
  JwtTokenConfiguration.class,
  StagingSwaggerSecurityConfig.class,
  StagingSwaggerSecurityConfigTest.SecurityTestController.class
})
class StagingSwaggerSecurityConfigTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private StagingSwaggerAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.supports(any())).thenReturn(true);
    when(authenticationProvider.authenticate(any()))
        .thenReturn(
            UsernamePasswordAuthenticationToken.authenticated(
                "0f3445fd-5f22-4f74-9a9d-1ff0c68a924a",
                null,
                java.util.List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("SESSION_FULL"),
                    new SimpleGrantedAuthority("PASSWORD_CHANGE_NOT_REQUIRED"))));
  }

  /** 비인증 Swagger resource 요청은 Spring Security 기본 로그인 화면으로 이동합니다. */
  @Test
  void redirectsAnonymousSwaggerRequestToLogin() throws Exception {
    mockMvc
        .perform(get("/swagger-ui/test-resource"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/login"));
    mockMvc
        .perform(get("/v3/api-docs.yaml"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/login"));

    mockMvc.perform(get("/login")).andExpect(status().isOk());
  }

  /** 관리자와 최고 관리자는 full·비밀번호 변경 완료 조건에서 Swagger resource를 볼 수 있습니다. */
  @Test
  void allowsAdminAndSuperAdmin() throws Exception {
    mockMvc
        .perform(get("/swagger-ui/test-resource").with(swaggerUser("ROLE_ADMIN")))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/swagger-ui/test-resource").with(swaggerUser("ROLE_SUPER_ADMIN")))
        .andExpect(status().isOk());
  }

  /** 일반 회원과 비밀번호 변경 필요 관리자는 인증 객체가 있어도 Swagger resource를 볼 수 없습니다. */
  @Test
  void rejectsMemberAndPasswordChangeRequiredAdmin() throws Exception {
    mockMvc
        .perform(get("/swagger-ui/test-resource").with(swaggerUser("ROLE_MEMBER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/swagger-ui/test-resource")
                .with(
                    user("admin")
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("SESSION_FULL"),
                            new SimpleGrantedAuthority("PASSWORD_CHANGE_REQUIRED"))))
        .andExpect(status().isForbidden());
  }

  /** cookie 기반 logout은 CSRF token 없는 POST를 거부합니다. */
  @Test
  void keepsCsrfProtectionForSwaggerSession() throws Exception {
    mockMvc
        .perform(post("/logout").with(swaggerUser("ROLE_ADMIN")))
        .andExpect(status().isForbidden());
  }

  /** Swagger Form Login session은 무상태 `/api/**` 체인의 인증으로 재사용되지 않습니다. */
  @Test
  void doesNotReuseSwaggerSessionForApiAuthentication() throws Exception {
    MvcResult login =
        mockMvc
            .perform(formLogin().user("admin@example.com").password("correct-password"))
            .andExpect(status().isFound())
            .andReturn();
    HttpSession session = login.getRequest().getSession(false);

    mockMvc
        .perform(
            get("/api/protected").session((org.springframework.mock.web.MockHttpSession) session))
        .andExpect(status().isUnauthorized());
  }

  private RequestPostProcessor swaggerUser(String role) {
    return user("admin")
        .authorities(
            new SimpleGrantedAuthority(role),
            new SimpleGrantedAuthority("SESSION_FULL"),
            new SimpleGrantedAuthority("PASSWORD_CHANGE_NOT_REQUIRED"));
  }

  @RestController
  static class SecurityTestController {

    @GetMapping("/swagger-ui/test-resource")
    String swaggerResource() {
      return "swagger";
    }

    @GetMapping("/api/protected")
    String apiResource() {
      return "api";
    }
  }
}
