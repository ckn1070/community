package com.ronams.carmunity.auth.internal.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ronams.carmunity.account.api.AccountAuthStatus;
import com.ronams.carmunity.account.api.AccountAuthSubject;
import com.ronams.carmunity.auth.internal.application.port.out.AccessTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.result.IssuedAccessToken;
import com.ronams.carmunity.auth.internal.application.service.SessionAccess;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HMAC 기반 access token 발급 계약을 검증합니다.
 *
 * <p>테스트 secret은 운영 secret이 아니라 HS256 서명과 검증을 재현하는 fixture입니다.
 */
class JwtAccessTokenIssuerAdapterTest {

  /** 운영 secret이 아니라 HS256 encoder/decoder를 같은 key로 재현하는 테스트 fixture입니다. */
  private static final String TEST_SECRET = "test-hmac-secret-must-be-at-least-32-bytes";

  /** 만료 시간과 `iat` claim을 결정론적으로 검증하기 위해 고정한 테스트 시각입니다. */
  private static final Instant NOW = Instant.parse("2099-06-22T00:00:00Z");

  /** access token에 인증과 인가에 필요한 최소 claim만 들어가는지 고정합니다. */
  @Test
  void issuesHmacJwtAccessTokenWithMinimalAccountClaims() {
    JwtTokenProperties properties =
        new JwtTokenProperties("carmunity-api", Duration.ofMinutes(15), TEST_SECRET);
    JwtTokenConfiguration configuration = new JwtTokenConfiguration();
    SecretKey secretKey = HmacJwtSecretKey.from(TEST_SECRET);
    AccessTokenIssuerPort issuer =
        new JwtAccessTokenIssuerAdapter(
            new NimbusJwtEncoder(new ImmutableSecret<>(secretKey)),
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC));
    AccountAuthSubject subject =
        new AccountAuthSubject(
            UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a"),
            AccountAuthStatus.ACTIVE,
            List.of("ROLE_MEMBER"),
            false);

    IssuedAccessToken token = issuer.issue(subject, SessionAccess.FULL);

    Jwt decoded = configuration.jwtDecoder(secretKey, properties).decode(token.value());
    assertThat(token.tokenType()).isEqualTo("Bearer");
    assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(decoded.getSubject()).isEqualTo(subject.publicId().toString());
    assertThat(decoded.getClaimAsString("iss")).isEqualTo("carmunity-api");
    assertThat(decoded.getIssuedAt()).isEqualTo(NOW);
    assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ROLE_MEMBER");
    assertThat(decoded.getClaimAsString("session_access")).isEqualTo("FULL");
    assertThat(decoded.getClaimAsString("account_status")).isEqualTo("ACTIVE");
    assertThat(decoded.getClaimAsBoolean("password_change_required")).isFalse();
    // access token subject와 authorization에 쓰지 않는 개인정보/id claim이 없는지 확인합니다.
    assertThat(decoded.getClaims())
        .doesNotContainKeys(
            "email", "displayName", "display_name", "accountId", "account_id", "accountStatus");
  }

  /** 초기 비밀번호 변경 대상이면 access token claim에 변경 필요 상태를 명시합니다. */
  @Test
  void issuesPasswordChangeRequiredClaimWhenSubjectRequiresPasswordChange() {
    JwtTokenProperties properties =
        new JwtTokenProperties("carmunity-api", Duration.ofMinutes(15), TEST_SECRET);
    JwtTokenConfiguration configuration = new JwtTokenConfiguration();
    SecretKey secretKey = HmacJwtSecretKey.from(TEST_SECRET);
    AccessTokenIssuerPort issuer =
        new JwtAccessTokenIssuerAdapter(
            new NimbusJwtEncoder(new ImmutableSecret<>(secretKey)),
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC));
    AccountAuthSubject subject =
        new AccountAuthSubject(
            UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a"),
            AccountAuthStatus.ACTIVE,
            List.of("ROLE_SUPER_ADMIN"),
            true);

    IssuedAccessToken token = issuer.issue(subject, SessionAccess.FULL);

    Jwt decoded = configuration.jwtDecoder(secretKey, properties).decode(token.value());
    assertThat(decoded.getClaimAsBoolean("password_change_required")).isTrue();
  }

  /** token의 `iss` claim이 decoder 설정의 issuer와 다르면 validation 실패로 처리되는지 검증합니다. */
  @Test
  void rejectsTokenWhenIssuerDoesNotMatchConfiguredIssuer() {
    JwtTokenProperties issuingProperties =
        new JwtTokenProperties("carmunity-api", Duration.ofMinutes(15), TEST_SECRET);
    JwtTokenConfiguration configuration = new JwtTokenConfiguration();
    SecretKey secretKey = HmacJwtSecretKey.from(TEST_SECRET);
    AccessTokenIssuerPort issuer =
        new JwtAccessTokenIssuerAdapter(
            new NimbusJwtEncoder(new ImmutableSecret<>(secretKey)),
            issuingProperties,
            Clock.fixed(NOW, ZoneOffset.UTC));
    JwtDecoder decoder =
        configuration.jwtDecoder(
            secretKey, new JwtTokenProperties("other-issuer", Duration.ofMinutes(15), TEST_SECRET));
    AccountAuthSubject subject =
        new AccountAuthSubject(
            UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a"),
            AccountAuthStatus.ACTIVE,
            List.of("ROLE_MEMBER"),
            false);

    IssuedAccessToken token = issuer.issue(subject, SessionAccess.FULL);
    String tokenValue = token.value();

    assertThatThrownBy(() -> decoder.decode(tokenValue)).isInstanceOf(JwtValidationException.class);
  }
}
