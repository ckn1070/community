package com.ronams.carmunity.auth.internal.adapter.in.web.swagger;

import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** staging에서만 Swagger browser session 보안 경계를 조립합니다. */
@Profile("staging")
@Configuration(proxyBeanMethods = false)
public class StagingSwaggerSecurityConfig {

  private static final String FULL_SESSION_AUTHORITY = "SESSION_FULL";
  private static final String PASSWORD_CHANGE_NOT_REQUIRED_AUTHORITY =
      "PASSWORD_CHANGE_NOT_REQUIRED";
  private static final String ROLE_ADMIN_AUTHORITY = "ROLE_ADMIN";
  private static final String ROLE_SUPER_ADMIN_AUTHORITY = "ROLE_SUPER_ADMIN";

  /** Swagger UI와 OpenAPI JSON에만 Form Login과 server session을 적용합니다. */
  @Bean
  @Order(1)
  SecurityFilterChain stagingSwaggerSecurityFilterChain(
      HttpSecurity http, StagingSwaggerAuthenticationProvider authenticationProvider) {
    return http.securityMatcher(
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/login",
            "/logout")
        .authenticationProvider(authenticationProvider)
        .sessionManagement(
            sessionManagement ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/login", "/logout")
                    .permitAll()
                    .anyRequest()
                    .access(StagingSwaggerSecurityConfig::hasSwaggerAccess))
        .formLogin(formLogin -> formLogin.defaultSuccessUrl("/swagger-ui.html", true))
        .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
        .build();
  }

  /** full session과 비밀번호 변경 완료 상태를 갖춘 관리자 이상 account만 허용합니다. */
  private static AuthorizationDecision hasSwaggerAccess(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    Authentication current = authentication.get();
    return new AuthorizationDecision(
        hasAuthority(current, FULL_SESSION_AUTHORITY)
            && hasAuthority(current, PASSWORD_CHANGE_NOT_REQUIRED_AUTHORITY)
            && (hasAuthority(current, ROLE_ADMIN_AUTHORITY)
                || hasAuthority(current, ROLE_SUPER_ADMIN_AUTHORITY)));
  }

  private static boolean hasAuthority(Authentication authentication, String authority) {
    return authentication != null
        && authentication.isAuthenticated()
        && authentication.getAuthorities().stream()
            .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
  }
}
