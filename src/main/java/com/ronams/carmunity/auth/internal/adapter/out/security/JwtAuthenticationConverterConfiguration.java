package com.ronams.carmunity.auth.internal.adapter.out.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/** Resource Server가 JWT 인증 결과를 프로젝트 authority 계약으로 변환하도록 조립합니다. */
@Configuration
public class JwtAuthenticationConverterConfiguration {

  /**
   * JWT `roles` claim을 `ROLE_*` authority로 사용합니다.
   *
   * <p>기본 scope 변환을 쓰면 `SCOPE_` prefix가 붙어 role authorization과 어긋나므로 명시적으로 교체합니다.
   */
  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter(
      JwtRoleAuthoritiesConverter authoritiesConverter) {
    JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
    authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
    return authenticationConverter;
  }
}
