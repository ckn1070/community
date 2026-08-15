package com.ronams.carmunity.auth.internal.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * JWT role/session claim을 Spring Security authorization이 사용하는 authority 계약으로 변환하는지 검증합니다.
 *
 * <p>프로젝트 role은 이미 `ROLE_` prefix를 포함하므로 Spring Security 기본 scope 변환의 `SCOPE_` prefix를 사용하지 않습니다.
 */
class JwtRoleAuthoritiesConverterTest {

  /** 프로젝트 role code는 이미 `ROLE_*` 형태이므로 Security 기본 scope prefix를 덧붙이지 않습니다. */
  @Test
  void mapsRolesClaimToRoleAuthoritiesWithoutScopePrefix() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a")
            .issuedAt(Instant.parse("2026-06-22T00:00:00Z"))
            .expiresAt(Instant.parse("2026-06-22T00:15:00Z"))
            .claim("roles", List.of("ROLE_MEMBER", "ROLE_ADMIN"))
            .claim("session_access", "FULL")
            .claim("password_change_required", false)
            .build();

    assertThat(new JwtRoleAuthoritiesConverter().convert(jwt))
        .extracting("authority")
        .containsExactlyInAnyOrder(
            "ROLE_MEMBER", "ROLE_ADMIN", "SESSION_FULL", "PASSWORD_CHANGE_NOT_REQUIRED");
  }

  /** 비밀번호 변경 필요 claim은 PASSWORD_CHANGE_REQUIRED authority로 변환됩니다. */
  @Test
  void mapsPasswordChangeRequiredClaimToRequiredAuthority() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a")
            .issuedAt(Instant.parse("2026-06-22T00:00:00Z"))
            .expiresAt(Instant.parse("2026-06-22T00:15:00Z"))
            .claim("roles", List.of("ROLE_SUPER_ADMIN"))
            .claim("session_access", "FULL")
            .claim("password_change_required", true)
            .build();

    assertThat(new JwtRoleAuthoritiesConverter().convert(jwt))
        .extracting("authority")
        .containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "SESSION_FULL", "PASSWORD_CHANGE_REQUIRED");
  }

  /** PENDING 제한 session claim은 role과 별도로 SESSION_LIMITED authority로 변환됩니다. */
  @Test
  void mapsLimitedSessionAccessClaimToLimitedSessionAuthority() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a")
            .issuedAt(Instant.parse("2026-06-22T00:00:00Z"))
            .expiresAt(Instant.parse("2026-06-22T00:15:00Z"))
            .claim("roles", List.of("ROLE_MEMBER"))
            .claim("session_access", "LIMITED")
            .claim("password_change_required", false)
            .build();

    assertThat(new JwtRoleAuthoritiesConverter().convert(jwt))
        .extracting("authority")
        .containsExactlyInAnyOrder(
            "ROLE_MEMBER", "SESSION_LIMITED", "PASSWORD_CHANGE_NOT_REQUIRED");
  }

  /** 빈 문자열과 `ROLE_` prefix가 없는 role claim 값은 authority로 만들지 않습니다. */
  @Test
  void ignoresBlankAndNonRoleClaimValues() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a")
            .issuedAt(Instant.parse("2026-06-22T00:00:00Z"))
            .expiresAt(Instant.parse("2026-06-22T00:15:00Z"))
            .claim("roles", List.of("ROLE_MEMBER", "", "MEMBER", 100))
            .claim("session_access", "OWNER")
            .build();

    assertThat(new JwtRoleAuthoritiesConverter().convert(jwt))
        .extracting("authority")
        .containsExactly("ROLE_MEMBER");
  }
}
