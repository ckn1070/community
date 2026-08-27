package com.ronams.carmunity.config;

import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * API 요청의 기본 보안 경계를 조립합니다.
 *
 * <p>새 공개 업무 API는 기본 보호 규칙보다 먼저 허용 목록에 명시해야 합니다.
 */
// Sonar java:S1075는 URI/path 하드코딩을 경고합니다.
// 아래 경로는 환경별 설정값이 아니라 클라이언트와 약속한 고정 HTTP API 계약입니다.
// Security allow-list가 공개 endpoint를 명시해야 하므로 코드 상수로 둡니다.
@SuppressWarnings("java:S1075")
@Configuration
public class ApiSecurityConfig {

  private static final String API_PATTERN = "/api/**";
  private static final String ADMIN_INVITATIONS_PATH = "/api/v1/admin/invitations";
  private static final String ADMIN_INVITATION_DETAIL_PATH = "/api/v1/admin/invitations/*";
  private static final String AUTH_PASSWORD_PATH = "/api/v1/auth/password";
  private static final String ADMIN_API_PATTERN = "/api/v1/admin/**";
  private static final String FULL_SESSION_AUTHORITY = "SESSION_FULL";
  private static final String PASSWORD_CHANGE_NOT_REQUIRED_AUTHORITY =
      "PASSWORD_CHANGE_NOT_REQUIRED";
  private static final String ROLE_ADMIN_AUTHORITY = "ROLE_ADMIN";
  private static final String ROLE_SUPER_ADMIN_AUTHORITY = "ROLE_SUPER_ADMIN";

  /**
   * 현재 API 보안 기준을 적용하는 SecurityFilterChain입니다.
   *
   * <p>업무 API는 기본적으로 Bearer JWT 인증을 요구하고, 회원가입과 로그인처럼 공개되어야 하는 엔드포인트만 허용 목록으로 먼저 엽니다.
   *
   * <p>현재 {@code /api/**}는 쿠키나 서버 세션 인증을 사용하지 않는 무상태 JSON API입니다. CSRF 보호는 브라우저가 자동으로 첨부하는 인증 정보에
   * 맞춘 방어이므로, 쿠키 인증을 쓰지 않는 현재 API 경계에서는 제외합니다.
   */
  @Bean
  @Order(2)
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      ApiSecurityProblemHandler securityProblemHandler,
      JwtAuthenticationConverter jwtAuthenticationConverter) {
    return http
        // 현재 /api/**는 쿠키 인증을 쓰지 않습니다. 쿠키 기반 세션 API가 생기면 CSRF 예외 범위를 분리합니다.
        .csrf(csrf -> csrf.ignoringRequestMatchers(API_PATTERN))
        .sessionManagement(
            sessionManagement ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // 공개 업무 API는 이 허용 목록에서만 열고, 그 외 /api/**는 Bearer JWT 인증을 요구합니다.
        .authorizeHttpRequests(
            authorize -> {
              ApiEndpointAccessPolicy.publicOperations()
                  .forEach(
                      operation ->
                          authorize
                              .requestMatchers(operation.method(), operation.securityPath())
                              .permitAll());
              authorize
                  .requestMatchers(HttpMethod.PUT, AUTH_PASSWORD_PATH)
                  .hasAuthority(FULL_SESSION_AUTHORITY)
                  .requestMatchers(HttpMethod.POST, ADMIN_INVITATIONS_PATH)
                  .access(ApiSecurityConfig::hasSuperAdminApiAccess)
                  .requestMatchers(HttpMethod.DELETE, ADMIN_INVITATION_DETAIL_PATH)
                  .access(ApiSecurityConfig::hasSuperAdminApiAccess)
                  .requestMatchers(ADMIN_API_PATTERN)
                  .access(ApiSecurityConfig::hasAdminApiAccess)
                  .requestMatchers(API_PATTERN)
                  .access(ApiSecurityConfig::hasDefaultApiAccess)
                  .anyRequest()
                  .permitAll();
            })
        // Security 필터 단계의 401/403도 MVC 예외와 같은 Problem Details schema로 응답합니다.
        .exceptionHandling(
            exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint(securityProblemHandler)
                    .accessDeniedHandler(securityProblemHandler))
        // 보호 API는 access token 원문을 DB에 저장하지 않는 Bearer JWT Resource Server 방식으로 인증합니다.
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(securityProblemHandler)
                    .accessDeniedHandler(securityProblemHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .httpBasic(AbstractHttpConfigurer::disable)
        // JSON API 기준에서는 브라우저용 form login/logout 엔드포인트를 열지 않습니다.
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .build();
  }

  /** 기본 보호 API는 full session이면서 비밀번호 변경 강제 상태가 아닐 때만 접근시킵니다. */
  private static AuthorizationDecision hasDefaultApiAccess(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    Authentication current = authentication.get();
    return new AuthorizationDecision(
        hasAuthority(current, FULL_SESSION_AUTHORITY)
            && hasAuthority(current, PASSWORD_CHANGE_NOT_REQUIRED_AUTHORITY));
  }

  /** 관리자 API는 기본 보호 조건에 관리자 role을 추가로 요구합니다. */
  private static AuthorizationDecision hasAdminApiAccess(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    Authentication current = authentication.get();
    return new AuthorizationDecision(
        hasAuthority(current, FULL_SESSION_AUTHORITY)
            && hasAuthority(current, PASSWORD_CHANGE_NOT_REQUIRED_AUTHORITY)
            && (hasAuthority(current, ROLE_ADMIN_AUTHORITY)
                || hasAuthority(current, ROLE_SUPER_ADMIN_AUTHORITY)));
  }

  /** 관리자 초대 관리 API는 최고 관리자 role과 기본 보호 조건을 함께 요구합니다. */
  private static AuthorizationDecision hasSuperAdminApiAccess(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    Authentication current = authentication.get();
    return new AuthorizationDecision(
        hasAuthority(current, FULL_SESSION_AUTHORITY)
            && hasAuthority(current, PASSWORD_CHANGE_NOT_REQUIRED_AUTHORITY)
            && hasAuthority(current, ROLE_SUPER_ADMIN_AUTHORITY));
  }

  /** anonymous 또는 인증 실패 객체는 권한 없음으로 처리하고, authority 문자열만 비교합니다. */
  private static boolean hasAuthority(Authentication authentication, String authority) {
    return authentication != null
        && authentication.isAuthenticated()
        && authentication.getAuthorities().stream()
            .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
  }
}
