package com.ronams.carmunity.auth.internal.adapter.out.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * JWT `roles`, `session_access`, `password_change_required` claim을 Spring Security authority로
 * 변환합니다.
 *
 * <p>프로젝트 role code는 이미 `ROLE_` prefix를 포함하므로 scope claim 기본 변환의 `SCOPE_` prefix를 사용하지 않습니다.
 */
@Component
public class JwtRoleAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  private static final String ROLES_CLAIM = "roles";
  private static final String SESSION_ACCESS_CLAIM = "session_access";
  private static final String PASSWORD_CHANGE_REQUIRED_CLAIM = "password_change_required";
  private static final String ROLE_PREFIX = "ROLE_";
  private static final String SESSION_AUTHORITY_PREFIX = "SESSION_";
  private static final String PASSWORD_CHANGE_REQUIRED_AUTHORITY = "PASSWORD_CHANGE_REQUIRED";
  private static final String PASSWORD_CHANGE_NOT_REQUIRED_AUTHORITY =
      "PASSWORD_CHANGE_NOT_REQUIRED";

  /** JWT claim별 필터링을 통과한 authority만 Security authorization 판단에 넘깁니다. */
  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    return Stream.of(roleAuthorities(jwt), sessionAuthority(jwt), passwordChangeAuthority(jwt))
        .flatMap(authorities -> authorities)
        .map(SimpleGrantedAuthority::new)
        .map(GrantedAuthority.class::cast)
        .toList();
  }

  /** role claim은 `ROLE_` prefix가 있는 프로젝트 role code만 authority로 인정합니다. */
  private Stream<String> roleAuthorities(Jwt jwt) {
    Object roles = jwt.getClaims().get(ROLES_CLAIM);
    if (!(roles instanceof Collection<?> roleValues)) {
      return Stream.empty();
    }

    return roleValues.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(role -> !role.isBlank())
        .filter(role -> role.startsWith(ROLE_PREFIX))
        .distinct();
  }

  /** session access claim은 API 보호 규칙이 아는 `FULL`/`LIMITED` 값만 authority로 변환합니다. */
  private Stream<String> sessionAuthority(Jwt jwt) {
    Object sessionAccess = jwt.getClaims().get(SESSION_ACCESS_CLAIM);
    if (!(sessionAccess instanceof String value) || value.isBlank()) {
      return Stream.empty();
    }
    if (!List.of("FULL", "LIMITED").contains(value)) {
      return Stream.empty();
    }
    return Stream.of(SESSION_AUTHORITY_PREFIX + value);
  }

  /** 비밀번호 변경 flag가 없거나 boolean이 아니면 API 접근 제한 판단용 authority를 만들지 않습니다. */
  private Stream<String> passwordChangeAuthority(Jwt jwt) {
    Object passwordChangeRequired = jwt.getClaims().get(PASSWORD_CHANGE_REQUIRED_CLAIM);
    if (!(passwordChangeRequired instanceof Boolean value)) {
      return Stream.empty();
    }
    boolean changeRequired = value;
    return Stream.of(
        changeRequired
            ? PASSWORD_CHANGE_REQUIRED_AUTHORITY
            : PASSWORD_CHANGE_NOT_REQUIRED_AUTHORITY);
  }
}
