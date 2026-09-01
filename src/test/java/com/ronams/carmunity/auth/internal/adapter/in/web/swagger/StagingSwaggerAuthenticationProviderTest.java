package com.ronams.carmunity.auth.internal.adapter.in.web.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ronams.carmunity.account.api.AccountAuthStatus;
import com.ronams.carmunity.account.api.PasswordLoginAccount;
import com.ronams.carmunity.account.api.PasswordLoginFailureReason;
import com.ronams.carmunity.account.api.PasswordLoginResultRecorder;
import com.ronams.carmunity.auth.internal.application.service.AuthenticatePasswordService;
import com.ronams.carmunity.auth.internal.application.service.AuthenticatedPasswordAccount;
import com.ronams.carmunity.auth.internal.application.service.SessionAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/** staging Swagger Form Login provider의 기존 관리자 정책과 token 없는 성공 기록을 검증합니다. */
class StagingSwaggerAuthenticationProviderTest {

  private static final UUID ACCOUNT_ID = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

  private AuthenticatePasswordService passwordAuthentication;
  private FakeResultRecorder resultRecorder;
  private StagingSwaggerAuthenticationProvider provider;

  @BeforeEach
  void setUp() {
    passwordAuthentication = mock(AuthenticatePasswordService.class);
    resultRecorder = new FakeResultRecorder();
    provider =
        new StagingSwaggerAuthenticationProvider(
            passwordAuthentication, resultRecorder, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** ROLE_ADMIN 계정은 full·비밀번호 변경 완료 authority를 가진 session 인증을 만듭니다. */
  @Test
  void authenticatesAdminWithoutRefreshToken() {
    stubAuthentication(List.of("ROLE_ADMIN"), false, SessionAccess.FULL);

    var authenticated = provider.authenticate(token());

    assertThat(authenticated.isAuthenticated()).isTrue();
    assertThat(authenticated.getPrincipal()).isEqualTo(ACCOUNT_ID.toString());
    assertThat(authenticated.getCredentials()).isNull();
    assertThat(authenticated.getAuthorities())
        .extracting("authority")
        .containsExactlyInAnyOrder("ROLE_ADMIN", "SESSION_FULL", "PASSWORD_CHANGE_NOT_REQUIRED");
    assertThat(resultRecorder.interactiveSuccessAccountId).isEqualTo(ACCOUNT_ID);
    assertThat(resultRecorder.occurredAt).isEqualTo(NOW);
    assertThat(resultRecorder.tokenSuccessAccountPublicId).isNull();
  }

  /** ROLE_SUPER_ADMIN도 관리자 이상 권한으로 Swagger session을 만들 수 있습니다. */
  @Test
  void authenticatesSuperAdmin() {
    stubAuthentication(List.of("ROLE_SUPER_ADMIN"), false, SessionAccess.FULL);

    var authenticated = provider.authenticate(token());

    assertThat(authenticated.getAuthorities()).extracting("authority").contains("ROLE_SUPER_ADMIN");
    assertThat(resultRecorder.interactiveSuccessAccountId).isEqualTo(ACCOUNT_ID);
  }

  /** 일반 회원은 password가 맞아도 Swagger 인증 session을 만들 수 없습니다. */
  @Test
  void rejectsMemberAccount() {
    stubAuthentication(List.of("ROLE_MEMBER"), false, SessionAccess.FULL);
    var authentication = token();

    assertThatThrownBy(() -> provider.authenticate(authentication))
        .isInstanceOf(BadCredentialsException.class);

    assertThat(resultRecorder.interactiveSuccessAccountId).isNull();
  }

  /** 초기 비밀번호 변경이 남은 최고 관리자는 변경 완료 전 Swagger에 접근할 수 없습니다. */
  @Test
  void rejectsAdminWhoMustChangePassword() {
    stubAuthentication(List.of("ROLE_SUPER_ADMIN"), true, SessionAccess.FULL);
    var authentication = token();

    assertThatThrownBy(() -> provider.authenticate(authentication))
        .isInstanceOf(BadCredentialsException.class);

    assertThat(resultRecorder.interactiveSuccessAccountId).isNull();
  }

  /** PENDING 같은 limited session account는 관리자 role이 있어도 Swagger에 접근할 수 없습니다. */
  @Test
  void rejectsLimitedSessionAccount() {
    stubAuthentication(List.of("ROLE_ADMIN"), false, SessionAccess.LIMITED);
    var authentication = token();

    assertThatThrownBy(() -> provider.authenticate(authentication))
        .isInstanceOf(BadCredentialsException.class);

    assertThat(resultRecorder.interactiveSuccessAccountId).isNull();
  }

  private void stubAuthentication(
      List<String> roles, boolean passwordChangeRequired, SessionAccess sessionAccess) {
    var account =
        new PasswordLoginAccount(
            ACCOUNT_ID, AccountAuthStatus.ACTIVE, "{argon2}hash", roles, passwordChangeRequired);
    when(passwordAuthentication.authenticate("admin@example.com", "correct-password"))
        .thenReturn(new AuthenticatedPasswordAccount(account, sessionAccess));
  }

  private UsernamePasswordAuthenticationToken token() {
    return UsernamePasswordAuthenticationToken.unauthenticated(
        "admin@example.com", "correct-password");
  }

  private static class FakeResultRecorder implements PasswordLoginResultRecorder {

    private UUID tokenSuccessAccountPublicId;
    private UUID interactiveSuccessAccountId;
    private Instant occurredAt;

    @Override
    public void recordSuccess(UUID accountPublicId, Instant occurredAt) {
      tokenSuccessAccountPublicId = accountPublicId;
    }

    @Override
    public void recordInteractiveSuccess(UUID accountPublicId, Instant occurredAt) {
      interactiveSuccessAccountId = accountPublicId;
      this.occurredAt = occurredAt;
    }

    @Override
    public void recordKnownFailure(
        UUID accountPublicId,
        String identifierHash,
        PasswordLoginFailureReason reason,
        Instant occurredAt) {
      // 이 provider 검증에서는 password 인증 실패 기록을 사용하지 않습니다.
    }

    @Override
    public void recordUnknownFailure(String identifierHash, Instant occurredAt) {
      // 이 provider 검증에서는 unknown account 실패 기록을 사용하지 않습니다.
    }
  }
}
