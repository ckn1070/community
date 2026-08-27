package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.auth.internal.adapter.out.security.JwtAuthenticationConverterConfiguration;
import com.ronams.carmunity.auth.internal.adapter.out.security.JwtRoleAuthoritiesConverter;
import com.ronams.carmunity.auth.internal.adapter.out.security.JwtTokenConfiguration;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Security baseline의 public allow-list, 기본 보호 범위, Security 오류 응답 계약을 검증합니다.
 *
 * <p>이 테스트가 깨지면 새 endpoint가 의도치 않게 공개되거나, Security 401/403 응답이 공통 Problem Details schema에서 벗어날 수
 * 있습니다.
 */
@WebMvcTest(controllers = ApiSecurityConfigTest.SecurityTestController.class)
@ExtendWith(OutputCaptureExtension.class)
@Import({
  ApiRequestActorContextInterceptor.class,
  ApiErrorResponseFactory.class,
  ApiExceptionHandler.class,
  ApiRequestContextFilter.class,
  ApiSecurityConfig.class,
  ApiSecurityConfigTest.SecurityTestController.class,
  ApiSecurityProblemHandler.class,
  JwtAuthenticationConverterConfiguration.class,
  JwtRoleAuthoritiesConverter.class,
  JwtTokenConfiguration.class
})
// WebMvcTest slice에서도 Resource Server bean이 로딩되므로 운영값이 아닌 테스트 전용 JWT 설정을 주입합니다.
@TestPropertySource(
    properties = {
      "carmunity.auth.jwt.issuer=carmunity-api-test",
      "carmunity.auth.jwt.access-token-ttl=15m",
      "carmunity.auth.jwt.hmac-secret=test-hmac-secret-must-be-at-least-32-bytes",
      "carmunity.auth.login.identifier-hash.hmac-secret=test-identifier-secret-must-be-at-least-32-bytes"
    })
class ApiSecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  /** 회원가입 API는 인증 전 사용자가 호출해야 하므로 public allow-list에 남아 있어야 합니다. */
  @Test
  void allowsPublicMemberSignUpEndpointWithoutAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/v1/members"))
        .andExpect(status().isCreated())
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())));
  }

  /** 로그인 API는 access token을 발급받기 전 사용자가 호출해야 하므로 public allow-list에 포함합니다. */
  @Test
  void allowsPublicAuthSessionEndpointWithoutAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/sessions"))
        .andExpect(status().isCreated())
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())));
  }

  /** refresh API는 access token 만료 뒤 호출되어야 하므로 public allow-list에 포함합니다. */
  @Test
  void allowsPublicAuthSessionRefreshEndpointWithoutAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/sessions/refresh"))
        .andExpect(status().isOk())
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())));
  }

  /** 이메일 인증 요청/완료 API는 로그인 전 사용자가 호출해야 하므로 POST만 public allow-list에 둡니다. */
  @Test
  void allowsPublicEmailVerificationEndpointsWithoutAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/verifications/email"))
        .andExpect(status().isAccepted())
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())));

    mockMvc
        .perform(post("/api/v1/auth/verifications/email/confirm"))
        .andExpect(status().isNoContent())
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())));
  }

  /** 관리자 초대 수락 API는 초대 token 자체가 검증 수단이므로 public allow-list에 포함합니다. */
  @Test
  void allowsPublicAdminInvitationAcceptanceEndpointWithoutAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/v1/admin/invitations/7c24c7cb-b2d2-4d35-8a3d-49a2b71bdb8a/acceptances"))
        .andExpect(status().isCreated())
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())));
  }

  /** 비밀번호 재설정 요청/완료 API는 로그인 전 복구 흐름이므로 POST만 public allow-list에 둡니다. */
  @Test
  void allowsPublicPasswordResetEndpointsWithoutAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/password-resets"))
        .andExpect(status().isAccepted())
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())));

    mockMvc
        .perform(post("/api/v1/auth/password-resets/confirm"))
        .andExpect(status().isNoContent())
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())));
  }

  /** 로그아웃 API는 access token 만료/폐기 상태에서도 호출되어야 하므로 public allow-list에 포함합니다. */
  @Test
  void allowsPublicAuthLogoutEndpointWithoutAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/logout"))
        .andExpect(status().isNoContent())
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())));
  }

  /** allow-list에 없는 business API는 기본적으로 인증을 요구하고 401 Problem Details로 실패해야 합니다. */
  @Test
  void rejectsOtherApiEndpointWithoutAuthenticationAsProblemDetail() throws Exception {
    mockMvc
        .perform(get("/api/protected"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.instance").value("/api/protected"));
  }

  /** Bearer JWT 인증 요청은 같은 {@code /api/**} 보호 규칙 안에서 Controller까지 도달할 수 있어야 합니다. */
  @Test
  void allowsOtherApiEndpointWithBearerJwtAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/protected").with(fullSessionJwt()))
        .andExpect(status().isOk())
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())));
  }

  /** 인증된 새 API도 별도 Controller 로그 없이 route와 account public id가 공통 완료 로그에 남아야 합니다. */
  @Test
  void logsAuthenticatedApiCompletionWithActorAndRoute(CapturedOutput output) throws Exception {
    mockMvc
        .perform(
            get("/api/protected")
                .header(ApiRequestIdContext.REQUEST_ID_HEADER, "request-observable")
                .with(fullSessionJwt()))
        .andExpect(status().isOk());

    assertThat(output.getOut())
        .contains("event=api.request.completed")
        .contains("requestId=request-observable")
        .contains("path=/api/protected")
        .contains("route=/api/protected")
        .contains("operation=GET /api/protected")
        .contains("status=200")
        .contains("result=success")
        .contains("actorId=0f3445fd-5f22-4f74-9a9d-1ff0c68a924a")
        .contains("durationMs=");
  }

  /** Security가 차단한 API도 공통 완료 로그에서 거부 결과와 공개 error code를 확인할 수 있어야 합니다. */
  @Test
  void logsSecurityRejectionWithErrorCode(CapturedOutput output) throws Exception {
    mockMvc
        .perform(
            get("/api/protected")
                .header(ApiRequestIdContext.REQUEST_ID_HEADER, "request-unauthorized"))
        .andExpect(status().isUnauthorized());

    assertThat(output.getOut())
        .contains("event=api.request.completed")
        .contains("requestId=request-unauthorized")
        .contains("path=/api/protected")
        .contains("route=unmatched")
        .contains("status=401")
        .contains("result=rejected")
        .contains("errorCode=UNAUTHORIZED");
  }

  /** MVC 진입 전에 권한이 거부돼도 인증된 account public id를 완료 로그에 남겨야 합니다. */
  @Test
  void logsAuthenticatedSecurityRejectionWithActor(CapturedOutput output) throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/dashboard")
                .header(ApiRequestIdContext.REQUEST_ID_HEADER, "request-forbidden")
                .with(fullSessionJwt("ROLE_MEMBER", false)))
        .andExpect(status().isForbidden());

    assertThat(output.getOut())
        .contains("event=api.request.completed")
        .contains("requestId=request-forbidden")
        .contains("path=/api/v1/admin/dashboard")
        .contains("status=403")
        .contains("result=rejected")
        .contains("actorId=0f3445fd-5f22-4f74-9a9d-1ff0c68a924a")
        .contains("errorCode=FORBIDDEN");
  }

  /** 비밀번호 변경 필요 계정은 full session이어도 일반 보호 API를 호출할 수 없습니다. */
  @Test
  void rejectsOtherApiEndpointWhenPasswordChangeIsRequired() throws Exception {
    mockMvc
        .perform(get("/api/protected").with(fullSessionJwt("ROLE_MEMBER", true)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  /** 비밀번호 변경 API는 PASSWORD_CHANGE_REQUIRED 계정도 호출할 수 있어야 합니다. */
  @Test
  void allowsPasswordChangeEndpointWithPasswordChangeRequiredFullSession() throws Exception {
    mockMvc
        .perform(put("/api/v1/auth/password").with(fullSessionJwt("ROLE_SUPER_ADMIN", true)))
        .andExpect(status().isNoContent());
  }

  /** 관리자 API는 full session, 비밀번호 변경 완료, 관리자 role을 모두 요구합니다. */
  @Test
  void allowsAdminApiWithAdminRoleAndCompletedPasswordChange() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/dashboard").with(fullSessionJwt("ROLE_ADMIN", false)))
        .andExpect(status().isOk());
  }

  /** 최고 관리자 role도 관리자 API 공통 접근 기준을 만족합니다. */
  @Test
  void allowsAdminApiWithSuperAdminRoleAndCompletedPasswordChange() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/dashboard").with(fullSessionJwt("ROLE_SUPER_ADMIN", false)))
        .andExpect(status().isOk());
  }

  /** 일반 회원 role은 full session과 비밀번호 변경 완료 상태여도 관리자 API에 접근할 수 없습니다. */
  @Test
  void rejectsAdminApiWithoutAdminRole() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/dashboard").with(fullSessionJwt("ROLE_MEMBER", false)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  /** 최고 관리자라도 초기 비밀번호 변경 전에는 관리자 API에 접근할 수 없습니다. */
  @Test
  void rejectsAdminApiWhenPasswordChangeIsRequired() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/dashboard").with(fullSessionJwt("ROLE_SUPER_ADMIN", true)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  /**
   * {@code /api/**} 보호 POST 요청은 쿠키 기반 CSRF token 없이 Bearer JWT 인증만으로 처리되어야 합니다.
   *
   * <p>이 테스트는 CSRF 예외가 공개 엔드포인트 확대가 아니라 무상태 Bearer 인증 정책에만 적용됨을 고정합니다.
   */
  @Test
  void allowsProtectedPostWithBearerJwtWithoutCsrfToken() throws Exception {
    mockMvc
        .perform(post("/api/protected").with(fullSessionJwt()))
        .andExpect(status().isOk())
        .andExpect(header().exists(ApiRequestIdContext.REQUEST_ID_HEADER))
        .andExpect(header().string(ApiRequestIdContext.REQUEST_ID_HEADER, not(blankString())));
  }

  /** 관리자 초대 생성과 회수는 full session과 ROLE_SUPER_ADMIN을 모두 요구합니다. */
  @Test
  void requiresSuperAdminFullSessionForAdminInvitationManagement() throws Exception {
    mockMvc
        .perform(post("/api/v1/admin/invitations").with(fullSessionJwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    mockMvc
        .perform(
            delete("/api/v1/admin/invitations/7c24c7cb-b2d2-4d35-8a3d-49a2b71bdb8a")
                .with(fullSessionJwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    mockMvc
        .perform(post("/api/v1/admin/invitations").with(superAdminFullSessionJwt()))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            delete("/api/v1/admin/invitations/7c24c7cb-b2d2-4d35-8a3d-49a2b71bdb8a")
                .with(superAdminFullSessionJwt()))
        .andExpect(status().isNoContent());
  }

  /** 최고 관리자라도 초기 비밀번호 변경 전에는 관리자 초대 관리 API에 접근할 수 없습니다. */
  @Test
  void rejectsAdminInvitationManagementWhenPasswordChangeIsRequired() throws Exception {
    mockMvc
        .perform(post("/api/v1/admin/invitations").with(fullSessionJwt("ROLE_SUPER_ADMIN", true)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  /** PENDING 계정의 제한 session은 인증은 됐지만 일반 보호 API에는 접근하지 못해야 합니다. */
  @Test
  void rejectsProtectedApiWithLimitedSessionAccessAsForbidden() throws Exception {
    mockMvc
        .perform(
            get("/api/protected")
                .with(
                    jwt()
                        .jwt(
                            jwt ->
                                jwt.subject("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a")
                                    .claim("roles", List.of("ROLE_MEMBER"))
                                    .claim("session_access", "LIMITED"))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_MEMBER"),
                            new SimpleGrantedAuthority("SESSION_LIMITED"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  /** 보호 API 테스트가 공통으로 쓰는 {@code SESSION_FULL} 권한의 JWT fixture입니다. */
  private static RequestPostProcessor fullSessionJwt() {
    return fullSessionJwt("ROLE_MEMBER", false);
  }

  private static RequestPostProcessor fullSessionJwt(String role, boolean passwordChangeRequired) {
    return jwt()
        .jwt(
            jwt ->
                jwt.subject("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a")
                    .claim("roles", List.of(role))
                    .claim("session_access", "FULL")
                    .claim("password_change_required", passwordChangeRequired))
        .authorities(
            new SimpleGrantedAuthority(role),
            new SimpleGrantedAuthority("SESSION_FULL"),
            new SimpleGrantedAuthority(
                passwordChangeRequired
                    ? "PASSWORD_CHANGE_REQUIRED"
                    : "PASSWORD_CHANGE_NOT_REQUIRED"));
  }

  /** 관리자 초대 관리 API 테스트용 최고 관리자 full session JWT fixture입니다. */
  private static RequestPostProcessor superAdminFullSessionJwt() {
    return jwt()
        .jwt(
            jwt ->
                jwt.subject("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a")
                    .claim("roles", List.of("ROLE_SUPER_ADMIN"))
                    .claim("session_access", "FULL")
                    .claim("password_change_required", false))
        .authorities(
            new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"),
            new SimpleGrantedAuthority("SESSION_FULL"),
            new SimpleGrantedAuthority("PASSWORD_CHANGE_NOT_REQUIRED"));
  }

  /**
   * 권한 부족 응답은 MVC exception handler를 거치지 않으므로 Security handler를 직접 검증합니다.
   *
   * <p>role 정책이 아직 없더라도 AccessDeniedHandler의 403 Problem Details 계약은 먼저 고정합니다.
   */
  @Test
  void writesForbiddenProblemDetailForAccessDeniedException() throws Exception {
    ApiErrorResponseFactory responseFactory = new ApiErrorResponseFactory();
    ApiSecurityProblemHandler handler = new ApiSecurityProblemHandler(responseFactory);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/example");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.handle(request, response, new AccessDeniedException("required admin role"));

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(response.getContentType()).contains("application/problem+json");
    assertThat(response.getContentAsString())
        .contains("\"type\":\"https://api.car-community.example/problems/forbidden\"")
        .contains("\"title\":\"Forbidden\"")
        .contains("\"status\":403")
        .contains("\"detail\":\"요청한 리소스에 접근할 권한이 없습니다.\"")
        .contains("\"instance\":\"/api/admin/example\"")
        .contains("\"code\":\"FORBIDDEN\"")
        .doesNotContain("required admin role");
  }

  /** Security matcher 검증용 Controller입니다. 업무 로직 없이 공개/보호 path만 제공합니다. */
  @RestController
  public static class SecurityTestController {

    @PostMapping("/api/v1/members")
    void registerMember(HttpServletResponse response) {
      response.setStatus(HttpServletResponse.SC_CREATED);
    }

    @PostMapping("/api/v1/auth/sessions")
    void createAuthSession(HttpServletResponse response) {
      response.setStatus(HttpServletResponse.SC_CREATED);
    }

    @PostMapping("/api/v1/auth/sessions/refresh")
    String refreshAuthSession() {
      return "ok";
    }

    @PostMapping("/api/v1/auth/logout")
    void logoutAuthSession(HttpServletResponse response) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @GetMapping("/api/protected")
    String protectedApi() {
      return "ok";
    }

    @PostMapping("/api/protected")
    String protectedPostApi() {
      return "ok";
    }

    @PostMapping("/api/v1/auth/verifications/email")
    void requestEmailVerification(HttpServletResponse response) {
      response.setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    @PostMapping("/api/v1/auth/verifications/email/confirm")
    void confirmEmailVerification(HttpServletResponse response) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @PostMapping("/api/v1/auth/password-resets")
    void requestPasswordReset(HttpServletResponse response) {
      response.setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    @PostMapping("/api/v1/auth/password-resets/confirm")
    void confirmPasswordReset(HttpServletResponse response) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @PutMapping("/api/v1/auth/password")
    void changePassword(HttpServletResponse response) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @GetMapping("/api/v1/admin/dashboard")
    String adminDashboard() {
      return "ok";
    }

    @PostMapping("/api/v1/admin/invitations")
    void createAdminInvitation(HttpServletResponse response) {
      response.setStatus(HttpServletResponse.SC_CREATED);
    }

    @PostMapping("/api/v1/admin/invitations/{invitationId}/acceptances")
    void acceptAdminInvitation(HttpServletResponse response) {
      response.setStatus(HttpServletResponse.SC_CREATED);
    }

    @DeleteMapping("/api/v1/admin/invitations/{invitationId}")
    void revokeAdminInvitation(HttpServletResponse response) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
  }
}
