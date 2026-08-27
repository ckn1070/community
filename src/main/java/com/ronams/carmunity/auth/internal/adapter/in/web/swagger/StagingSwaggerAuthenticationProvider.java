package com.ronams.carmunity.auth.internal.adapter.in.web.swagger;

import com.ronams.carmunity.account.api.PasswordLoginAccount;
import com.ronams.carmunity.account.api.PasswordLoginResultRecorder;
import com.ronams.carmunity.auth.internal.application.service.AuthenticatePasswordService;
import com.ronams.carmunity.auth.internal.application.service.AuthenticatedPasswordAccount;
import com.ronams.carmunity.auth.internal.application.service.SessionAccess;
import com.ronams.carmunity.shared.error.ApiException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** staging Swagger Form Login을 기존 관리자 account로 인증하는 provider입니다. */
@RequiredArgsConstructor
@Profile("staging")
@Component
public class StagingSwaggerAuthenticationProvider implements AuthenticationProvider {

  private static final String ROLE_ADMIN = "ROLE_ADMIN";
  private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
  private static final String SESSION_FULL = "SESSION_FULL";
  private static final String PASSWORD_CHANGE_NOT_REQUIRED = "PASSWORD_CHANGE_NOT_REQUIRED";

  private final AuthenticatePasswordService passwordAuthentication;
  private final PasswordLoginResultRecorder resultRecorder;
  private final Clock clock;

  /** password와 관리자 접근 조건을 모두 통과한 account만 Swagger용 session 인증으로 변환합니다. */
  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    String email = String.valueOf(authentication.getPrincipal());
    String password = String.valueOf(authentication.getCredentials());
    AuthenticatedPasswordAccount authenticated;
    try {
      authenticated = passwordAuthentication.authenticate(email, password);
    } catch (ApiException exception) {
      throw invalidCredentials(exception);
    }

    PasswordLoginAccount account = authenticated.account();
    if (!hasSwaggerAccess(account, authenticated.sessionAccess())) {
      throw invalidCredentials(null);
    }

    resultRecorder.recordInteractiveSuccess(account.publicId(), clock.instant());
    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
    account.roleCodes().stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);
    authorities.add(new SimpleGrantedAuthority(SESSION_FULL));
    authorities.add(new SimpleGrantedAuthority(PASSWORD_CHANGE_NOT_REQUIRED));

    return UsernamePasswordAuthenticationToken.authenticated(
        account.publicId().toString(), null, authorities);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }

  private boolean hasSwaggerAccess(PasswordLoginAccount account, SessionAccess sessionAccess) {
    return sessionAccess == SessionAccess.FULL
        && !account.passwordChangeRequired()
        && (account.roleCodes().contains(ROLE_ADMIN)
            || account.roleCodes().contains(ROLE_SUPER_ADMIN));
  }

  private BadCredentialsException invalidCredentials(Exception cause) {
    return new BadCredentialsException("Swagger authentication failed", cause);
  }
}
