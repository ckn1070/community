package com.ronams.carmunity.auth.internal.adapter.out.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** HMAC 기반 JWT 발급과 검증에 필요한 Spring Security bean을 조립합니다. */
@Configuration
@EnableConfigurationProperties({JwtTokenProperties.class, LoginIdentifierHashProperties.class})
public class JwtTokenConfiguration {

  /**
   * HS256 서명에 사용할 secret key입니다.
   *
   * <p>운영 secret 원문은 property source에서만 읽고, 로그나 예외 메시지에 포함하지 않습니다.
   */
  @Bean
  SecretKey jwtHmacSecretKey(JwtTokenProperties properties) {
    return HmacJwtSecretKey.from(properties.hmacSecret());
  }

  /** access token 발급 adapter가 사용하는 HMAC JWT encoder입니다. */
  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtHmacSecretKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtHmacSecretKey));
  }

  /**
   * Resource Server가 Bearer token을 검증할 때 사용하는 HS256 JWT decoder입니다.
   *
   * <p>signature와 만료 시각뿐 아니라, 설정한 issuer가 아닌 token도 같은 인증 실패로 처리합니다.
   */
  @Bean
  JwtDecoder jwtDecoder(SecretKey jwtHmacSecretKey, JwtTokenProperties properties) {
    NimbusJwtDecoder jwtDecoder =
        NimbusJwtDecoder.withSecretKey(jwtHmacSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    jwtDecoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<Jwt>(
            new JwtTimestampValidator(), new JwtIssuerValidator(properties.issuer())));
    return jwtDecoder;
  }

  /** token 발급 코드가 시간 기준을 주입받도록 Clock bean을 노출하고, 테스트는 이 bean을 대체해 발급 시각을 통제합니다. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
