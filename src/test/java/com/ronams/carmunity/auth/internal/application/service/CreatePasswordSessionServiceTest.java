package com.ronams.carmunity.auth.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.api.AccountAuthStatus;
import com.ronams.carmunity.account.api.AccountAuthSubject;
import com.ronams.carmunity.account.api.PasswordLoginAccount;
import com.ronams.carmunity.account.api.PasswordLoginAccountLookup;
import com.ronams.carmunity.account.api.PasswordLoginFailureReason;
import com.ronams.carmunity.account.api.PasswordLoginResultRecorder;
import com.ronams.carmunity.account.api.PasswordLoginSuccess;
import com.ronams.carmunity.auth.internal.application.AuthErrorCode;
import com.ronams.carmunity.auth.internal.application.command.CreatePasswordSessionCommand;
import com.ronams.carmunity.auth.internal.application.port.out.AccessTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.port.out.IdentifierHasherPort;
import com.ronams.carmunity.auth.internal.application.port.out.PasswordVerifierPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.result.IssuedAccessToken;
import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import com.ronams.carmunity.shared.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 비밀번호 로그인 usecase가 credential 검증, session access 결정, token 저장 요청, 감사 이벤트를 조율하는지 검증합니다.
 *
 * <p>fake port는 DB와 JWT 구현을 배제하고, password/token 원문이 저장 경로로 넘어가는지 호출 기록만 남깁니다.
 */
class CreatePasswordSessionServiceTest {

  private static final UUID ACCOUNT_PUBLIC_ID =
      UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final UUID REFRESH_TOKEN_FAMILY_ID =
      UUID.fromString("b43236f5-92d7-495e-aebc-2a3a66d7b21d");
  private static final String REFRESH_TOKEN_HASH =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final Instant NOW = Instant.parse("2026-06-22T00:00:00Z");
  private static final Instant ACCESS_EXPIRES_AT = NOW.plus(Duration.ofMinutes(15));
  private static final Instant REFRESH_EXPIRES_AT = NOW.plus(Duration.ofDays(30));

  private FakePasswordLoginAccountLookup accountLookup;
  private FakePasswordVerifierPort passwordVerifier;
  private FakeAccessTokenIssuerPort accessTokenIssuer;
  private FakeRefreshTokenIssuerPort refreshTokenIssuer;
  private FakeIdentifierHasherPort identifierHasher;
  private FakePasswordLoginResultRecorder resultRecorder;
  private CreatePasswordSessionService service;

  @BeforeEach
  void setUp() {
    accountLookup = new FakePasswordLoginAccountLookup();
    passwordVerifier = new FakePasswordVerifierPort();
    accessTokenIssuer = new FakeAccessTokenIssuerPort();
    refreshTokenIssuer = new FakeRefreshTokenIssuerPort();
    identifierHasher = new FakeIdentifierHasherPort();
    resultRecorder = new FakePasswordLoginResultRecorder();
    service =
        new CreatePasswordSessionService(
            accountLookup,
            passwordVerifier,
            accessTokenIssuer,
            refreshTokenIssuer,
            identifierHasher,
            resultRecorder,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** ACTIVE 계정은 full session으로 access/refresh token을 받고 실패 횟수 초기화 저장 흐름까지 진행합니다. */
  @Test
  void createsFullSessionForActiveAccount() {
    accountLookup.nextAccount = Optional.of(account(AccountAuthStatus.ACTIVE, false));

    var result = service.execute(command(" USER@example.COM ", "correct horse battery staple"));

    assertThat(result.accountId()).isEqualTo(ACCOUNT_PUBLIC_ID);
    assertThat(result.roles()).containsExactly("ROLE_MEMBER");
    assertThat(result.sessionAccess()).isEqualTo(SessionAccess.FULL);
    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.accessTokenExpiresAt()).isEqualTo(ACCESS_EXPIRES_AT);
    assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");
    assertThat(result.refreshTokenExpiresAt()).isEqualTo(REFRESH_EXPIRES_AT);
    assertThat(accessTokenIssuer.lastSubject)
        .isEqualTo(
            new AccountAuthSubject(
                ACCOUNT_PUBLIC_ID, AccountAuthStatus.ACTIVE, List.of("ROLE_MEMBER"), false));
    assertThat(accessTokenIssuer.lastSessionAccess).isEqualTo(SessionAccess.FULL);
    assertThat(resultRecorder.success.accountPublicId()).isEqualTo(ACCOUNT_PUBLIC_ID);
    assertThat(resultRecorder.success.refreshToken().tokenHash()).isEqualTo(REFRESH_TOKEN_HASH);
    assertThat(resultRecorder.success.refreshToken().familyId()).isEqualTo(REFRESH_TOKEN_FAMILY_ID);
    assertThat(resultRecorder.success.refreshToken().issuedAt()).isEqualTo(NOW);
    assertThat(resultRecorder.success.refreshToken().expiresAt()).isEqualTo(REFRESH_EXPIRES_AT);
  }

  /** 초기 비밀번호 변경 대상 ACTIVE 계정도 로그인은 성공하되 token subject에 변경 필요 flag를 전달합니다. */
  @Test
  void createsFullSessionWithPasswordChangeRequiredSubjectForInitialAdmin() {
    accountLookup.nextAccount = Optional.of(account(AccountAuthStatus.ACTIVE, true));

    var result = service.execute(command("admin@example.com", "correct horse battery staple"));

    assertThat(result.sessionAccess()).isEqualTo(SessionAccess.FULL);
    assertThat(accessTokenIssuer.lastSubject.passwordChangeRequired()).isTrue();
    assertThat(resultRecorder.success).isNotNull();
  }

  /** PENDING 계정은 로그인 자체는 성공하지만 full session과 구분되는 제한 session으로 발급합니다. */
  @Test
  void createsLimitedSessionForPendingAccount() {
    accountLookup.nextAccount = Optional.of(account(AccountAuthStatus.PENDING, false));

    var result = service.execute(command("user@example.com", "correct horse battery staple"));

    assertThat(result.sessionAccess()).isEqualTo(SessionAccess.LIMITED);
    assertThat(accessTokenIssuer.lastSessionAccess).isEqualTo(SessionAccess.LIMITED);
    assertThat(resultRecorder.success).isNotNull();
  }

  /** 존재하지 않는 email은 계정 존재 여부를 숨긴 401로 닫고 원문 대신 identifier hash 감사 이벤트만 요청합니다. */
  @Test
  void rejectsUnknownEmailWithUnauthorizedContractAndRecordsIdentifierHash() {
    accountLookup.nextAccount = Optional.empty();

    assertThatThrownBy(() -> service.execute(command("USER@example.com", "wrong password")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));

    assertThat(resultRecorder.unknownFailureIdentifierHash).isEqualTo("identifier-hash");
    assertThat(resultRecorder.unknownFailureOccurredAt).isEqualTo(NOW);
    assertThat(accessTokenIssuer.called).isFalse();
    assertThat(refreshTokenIssuer.called).isFalse();
    assertThat(resultRecorder.success).isNull();
  }

  /** password 불일치도 같은 401 계약으로 응답하지만, 특정된 account에는 실패 횟수와 감사 이벤트를 남깁니다. */
  @Test
  void rejectsWrongPasswordWithUnauthorizedContractAndRecordsKnownFailure() {
    accountLookup.nextAccount = Optional.of(account(AccountAuthStatus.ACTIVE, false));
    passwordVerifier.matches = false;

    assertThatThrownBy(() -> service.execute(command("user@example.com", "wrong password")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));

    assertThat(resultRecorder.knownFailureAccountPublicId).isEqualTo(ACCOUNT_PUBLIC_ID);
    assertThat(resultRecorder.knownFailureReason)
        .isEqualTo(PasswordLoginFailureReason.PASSWORD_MISMATCH);
    assertThat(resultRecorder.knownFailureIdentifierHash).isEqualTo("identifier-hash");
    assertThat(resultRecorder.knownFailureOccurredAt).isEqualTo(NOW);
    assertThat(accessTokenIssuer.called).isFalse();
    assertThat(refreshTokenIssuer.called).isFalse();
    assertThat(resultRecorder.success).isNull();
  }

  /** SUSPENDED 계정은 password가 맞아도 token을 발급하지 않고 계정 상태 제한 오류로 닫습니다. */
  @Test
  void rejectsSuspendedAccountWithoutIssuingTokens() {
    accountLookup.nextAccount = Optional.of(account(AccountAuthStatus.SUSPENDED, false));

    assertThatThrownBy(
            () -> service.execute(command("user@example.com", "correct horse battery staple")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AuthErrorCode.ACCOUNT_LOGIN_RESTRICTED));

    assertThat(accessTokenIssuer.called).isFalse();
    assertThat(refreshTokenIssuer.called).isFalse();
    assertThat(resultRecorder.success).isNull();
    assertThat(resultRecorder.knownFailureReason)
        .isEqualTo(PasswordLoginFailureReason.ACCOUNT_SUSPENDED);
  }

  private static CreatePasswordSessionCommand command(String email, String password) {
    return new CreatePasswordSessionCommand(email, password);
  }

  private static PasswordLoginAccount account(
      AccountAuthStatus status, boolean passwordChangeRequired) {
    return new PasswordLoginAccount(
        ACCOUNT_PUBLIC_ID,
        status,
        "{argon2}stored-password-hash",
        List.of("ROLE_MEMBER"),
        passwordChangeRequired);
  }

  /** normalized email 기반 조회만 표현해 auth usecase가 account 내부 저장소를 모르게 합니다. */
  private static class FakePasswordLoginAccountLookup implements PasswordLoginAccountLookup {

    private Optional<PasswordLoginAccount> nextAccount = Optional.empty();

    @Override
    public Optional<PasswordLoginAccount> findByNormalizedEmail(String normalizedEmail) {
      return nextAccount;
    }
  }

  /** 비밀번호 검증 결과와 원문 password 전달 여부를 관찰하는 security port 대역입니다. */
  private static class FakePasswordVerifierPort implements PasswordVerifierPort {

    private boolean matches = true;

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
      return matches && "correct horse battery staple".equals(rawPassword);
    }
  }

  /** access token 발급 요청에 사용된 subject와 session access를 기록하는 대역입니다. */
  private static class FakeAccessTokenIssuerPort implements AccessTokenIssuerPort {

    private boolean called;
    private AccountAuthSubject lastSubject;
    private SessionAccess lastSessionAccess;

    @Override
    public IssuedAccessToken issue(AccountAuthSubject subject, SessionAccess sessionAccess) {
      called = true;
      lastSubject = subject;
      lastSessionAccess = sessionAccess;
      return new IssuedAccessToken("access-token", ACCESS_EXPIRES_AT, "Bearer");
    }
  }

  /** refresh token 원문과 저장 hash가 분리되어 usecase에 전달되는지 기록하는 대역입니다. */
  private static class FakeRefreshTokenIssuerPort implements RefreshTokenIssuerPort {

    private boolean called;

    @Override
    public IssuedRefreshToken issue() {
      called = true;
      return new IssuedRefreshToken(
          "raw-refresh-token",
          REFRESH_TOKEN_HASH,
          REFRESH_TOKEN_FAMILY_ID,
          NOW,
          REFRESH_EXPIRES_AT);
    }
  }

  /** normalized identifier를 고정 hash로 바꿔 실패 기록에 email 원문이 들어가지 않는지 관찰하는 대역입니다. */
  private static class FakeIdentifierHasherPort implements IdentifierHasherPort {

    @Override
    public String hash(String normalizedIdentifier) {
      return "identifier-hash";
    }
  }

  /** account 내부 DB id를 auth 모듈에 노출하지 않고 결과 저장 요청만 관찰하는 대역입니다. */
  private static class FakePasswordLoginResultRecorder implements PasswordLoginResultRecorder {

    private PasswordLoginSuccess success;
    private UUID knownFailureAccountPublicId;
    private PasswordLoginFailureReason knownFailureReason;
    private String knownFailureIdentifierHash;
    private Instant knownFailureOccurredAt;
    private String unknownFailureIdentifierHash;
    private Instant unknownFailureOccurredAt;

    @Override
    public void recordSuccess(PasswordLoginSuccess success) {
      this.success = success;
    }

    @Override
    public void recordKnownFailure(
        UUID accountPublicId,
        String identifierHash,
        PasswordLoginFailureReason reason,
        Instant occurredAt) {
      knownFailureAccountPublicId = accountPublicId;
      knownFailureReason = reason;
      knownFailureIdentifierHash = identifierHash;
      knownFailureOccurredAt = occurredAt;
    }

    @Override
    public void recordUnknownFailure(String identifierHash, Instant occurredAt) {
      unknownFailureIdentifierHash = identifierHash;
      unknownFailureOccurredAt = occurredAt;
    }
  }
}
