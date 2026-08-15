package com.ronams.carmunity.auth.internal.adapter.out.security;

import com.ronams.carmunity.account.api.AccountAuthSubject;
import com.ronams.carmunity.auth.internal.application.port.out.AccessTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.result.IssuedAccessToken;
import com.ronams.carmunity.auth.internal.application.service.SessionAccess;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Spring Security JWT encoder를 사용하는 access token 발급 adapter입니다. */
@RequiredArgsConstructor
@Component
public class JwtAccessTokenIssuerAdapter implements AccessTokenIssuerPort {

  private static final String ROLES_CLAIM = "roles";
  private static final String SESSION_ACCESS_CLAIM = "session_access";
  private static final String ACCOUNT_STATUS_CLAIM = "account_status";
  private static final String PASSWORD_CHANGE_REQUIRED_CLAIM = "password_change_required";
  private static final String BEARER_TOKEN_TYPE = "Bearer";

  private final JwtEncoder jwtEncoder;
  private final JwtTokenProperties properties;
  private final Clock clock;

  /**
   * account public id, role, session access, account status, 비밀번호 변경 flag만 담은 짧은 수명의 access token을
   * 발급합니다.
   *
   * <p>email, display name, 내부 DB id는 token 탈취 시 노출 정보와 stale claim 범위를 줄이기 위해 넣지 않습니다.
   */
  @Override
  public IssuedAccessToken issue(AccountAuthSubject subject, SessionAccess sessionAccess) {
    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(subject.publicId().toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim(ROLES_CLAIM, subject.roleCodes())
            .claim(SESSION_ACCESS_CLAIM, sessionAccess.name())
            .claim(ACCOUNT_STATUS_CLAIM, subject.status().name())
            .claim(PASSWORD_CHANGE_REQUIRED_CLAIM, subject.passwordChangeRequired())
            .build();
    JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();

    String tokenValue =
        jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    return new IssuedAccessToken(tokenValue, expiresAt, BEARER_TOKEN_TYPE);
  }
}
