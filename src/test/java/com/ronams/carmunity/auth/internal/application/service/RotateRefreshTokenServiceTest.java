package com.ronams.carmunity.auth.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.api.AccountAuthStatus;
import com.ronams.carmunity.account.api.AccountAuthSubject;
import com.ronams.carmunity.account.api.AccountAuthSubjectLookup;
import com.ronams.carmunity.auth.internal.application.AuthErrorCode;
import com.ronams.carmunity.auth.internal.application.command.RotateRefreshTokenCommand;
import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRecord;
import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRejectionReason;
import com.ronams.carmunity.auth.internal.application.port.out.AccessTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenHashPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenLifecycleStorePort;
import com.ronams.carmunity.auth.internal.application.result.IssuedAccessToken;
import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import com.ronams.carmunity.auth.internal.application.result.RotatedRefreshTokenResult;
import com.ronams.carmunity.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * refresh token rotation 유스케이스의 보안 정책을 Spring 없이 검증합니다.
 *
 * <p>이 테스트는 token 원문을 hash 조회에만 쓰고, reuse/account 상태 실패가 모두 같은 API 오류로 닫히는 application 결정을 고정합니다.
 */
class RotateRefreshTokenServiceTest {

  private static final Instant NOW = Instant.parse("2099-06-23T00:00:00Z");
  private static final UUID ACCOUNT_PUBLIC_ID =
      UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final UUID TOKEN_FAMILY_ID =
      UUID.fromString("6e74f192-9256-4569-8579-6b8f557d9cb1");
  private static final RefreshTokenRecord ACTIVE_REFRESH_TOKEN =
      new RefreshTokenRecord(
          1L,
          10L,
          ACCOUNT_PUBLIC_ID,
          TOKEN_FAMILY_ID,
          NOW.minusSeconds(60),
          NOW.plusSeconds(3600),
          null,
          null,
          null);

  private FakeRefreshTokenHashPort hashPort;
  private FakeRefreshTokenLifecycleStorePort lifecycleStorePort;
  private FakeAccountAuthSubjectLookup accountLookup;
  private FakeAccessTokenIssuerPort accessTokenIssuerPort;
  private FakeRefreshTokenIssuerPort refreshTokenIssuerPort;
  private RotateRefreshTokenService service;

  @BeforeEach
  void setUp() {
    hashPort = new FakeRefreshTokenHashPort();
    lifecycleStorePort = new FakeRefreshTokenLifecycleStorePort();
    accountLookup = new FakeAccountAuthSubjectLookup();
    accessTokenIssuerPort = new FakeAccessTokenIssuerPort();
    refreshTokenIssuerPort = new FakeRefreshTokenIssuerPort();
    service =
        new RotateRefreshTokenService(
            hashPort,
            lifecycleStorePort,
            accountLookup,
            accessTokenIssuerPort,
            refreshTokenIssuerPort,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** 활성 refresh token은 기존 token 폐기, 새 refresh token 연결, access token 발급을 한 흐름으로 수행합니다. */
  @Test
  void rotatesActiveRefreshTokenAndReturnsNewTokens() {
    lifecycleStorePort.storedToken = ACTIVE_REFRESH_TOKEN;
    accountLookup.subject =
        new AccountAuthSubject(
            ACCOUNT_PUBLIC_ID, AccountAuthStatus.ACTIVE, List.of("ROLE_MEMBER"), false);

    RotatedRefreshTokenResult result =
        service.execute(new RotateRefreshTokenCommand("raw-refresh-token"));

    assertThat(hashPort.hashedToken).isEqualTo("raw-refresh-token");
    assertThat(accessTokenIssuerPort.issuedSubject).isEqualTo(accountLookup.subject);
    assertThat(accessTokenIssuerPort.issuedSessionAccess).isEqualTo(SessionAccess.FULL);
    assertThat(lifecycleStorePort.rotatedToken).isEqualTo(ACTIVE_REFRESH_TOKEN);
    assertThat(lifecycleStorePort.replacementToken).isEqualTo(refreshTokenIssuerPort.issuedToken);
    assertThat(lifecycleStorePort.rotatedAt).isEqualTo(NOW);
    assertThat(result.accessToken()).isEqualTo("new-access-token");
    assertThat(result.accessTokenExpiresAt()).isEqualTo(NOW.plusSeconds(900));
    assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    assertThat(result.refreshTokenExpiresAt()).isEqualTo(NOW.plusSeconds(2_592_000));
    assertThat(result.memberId()).isEqualTo(ACCOUNT_PUBLIC_ID);
    assertThat(result.roles()).containsExactly("ROLE_MEMBER");
  }

  /** PENDING 계정은 refresh rotation 자체는 허용하지만 일반 보호 API용 full session은 받지 않습니다. */
  @Test
  void rotatesPendingAccountRefreshTokenWithLimitedSessionAccess() {
    lifecycleStorePort.storedToken = ACTIVE_REFRESH_TOKEN;
    accountLookup.subject =
        new AccountAuthSubject(
            ACCOUNT_PUBLIC_ID, AccountAuthStatus.PENDING, List.of("ROLE_MEMBER"), false);

    service.execute(new RotateRefreshTokenCommand("pending-refresh-token"));

    assertThat(accessTokenIssuerPort.issuedSessionAccess).isEqualTo(SessionAccess.LIMITED);
    assertThat(lifecycleStorePort.rotatedToken).isEqualTo(ACTIVE_REFRESH_TOKEN);
  }

  /** 비밀번호 변경 필요 subject는 refresh rotation과 access token 발급 없이 INVALID_REFRESH_TOKEN으로 실패합니다. */
  @Test
  void rejectsRefreshRotationWhenPasswordChangeIsRequired() {
    lifecycleStorePort.storedToken = ACTIVE_REFRESH_TOKEN;
    accountLookup.subject =
        new AccountAuthSubject(
            ACCOUNT_PUBLIC_ID, AccountAuthStatus.ACTIVE, List.of("ROLE_SUPER_ADMIN"), true);

    assertInvalidRefreshToken(
        () -> service.execute(new RotateRefreshTokenCommand("password-change-required")));

    assertThat(lifecycleStorePort.rotatedToken).isNull();
    assertThat(lifecycleStorePort.replacementToken).isNull();
    assertThat(lifecycleStorePort.rejectedAccountFamilyId).isNull();
    assertThat(accessTokenIssuerPort.issuedSubject).isNull();
  }

  /** 만료 token은 refresh rotation 없이 INVALID_REFRESH_TOKEN 오류로 닫습니다. */
  @Test
  void rejectsExpiredRefreshTokenWithoutRotation() {
    lifecycleStorePort.storedToken =
        new RefreshTokenRecord(
            1L,
            10L,
            ACCOUNT_PUBLIC_ID,
            TOKEN_FAMILY_ID,
            NOW.minusSeconds(7200),
            NOW.minusSeconds(1),
            null,
            null,
            null);

    assertInvalidRefreshToken(() -> service.execute(new RotateRefreshTokenCommand("expired")));
    assertThat(lifecycleStorePort.rotatedToken).isNull();
    assertThat(lifecycleStorePort.reusedFamilyId).isNull();
  }

  /** 이미 교체된 token이 다시 들어오면 reuse 후보로 처리하고 token family 폐기를 요청합니다. */
  @Test
  void revokesFamilyWhenReplacedRefreshTokenIsReused() {
    lifecycleStorePort.storedToken =
        new RefreshTokenRecord(
            1L,
            10L,
            ACCOUNT_PUBLIC_ID,
            TOKEN_FAMILY_ID,
            NOW.minusSeconds(60),
            NOW.plusSeconds(3600),
            NOW.minusSeconds(30),
            "ROTATED",
            2L);

    assertInvalidRefreshToken(() -> service.execute(new RotateRefreshTokenCommand("reused")));
    assertThat(lifecycleStorePort.reusedFamilyId).isEqualTo(TOKEN_FAMILY_ID);
    assertThat(lifecycleStorePort.reusedAt).isEqualTo(NOW);
  }

  /** 교체 이력이 있는 token은 이미 만료됐더라도 탈취 신호를 보존해 token family 폐기를 요청합니다. */
  @Test
  void revokesFamilyWhenExpiredReplacedRefreshTokenIsReused() {
    lifecycleStorePort.storedToken =
        new RefreshTokenRecord(
            1L,
            10L,
            ACCOUNT_PUBLIC_ID,
            TOKEN_FAMILY_ID,
            NOW.minusSeconds(7200),
            NOW.minusSeconds(1),
            NOW.minusSeconds(3600),
            "ROTATED",
            2L);

    assertInvalidRefreshToken(
        () -> service.execute(new RotateRefreshTokenCommand("expired-replaced")));
    assertThat(lifecycleStorePort.reusedFamilyId).isEqualTo(TOKEN_FAMILY_ID);
    assertThat(lifecycleStorePort.reusedAt).isEqualTo(NOW);
  }

  /** 폐기 이력이 있는 token은 이미 만료됐더라도 reuse 경로를 선택해 token family 폐기를 요청합니다. */
  @Test
  void revokesFamilyWhenExpiredRevokedRefreshTokenIsReused() {
    lifecycleStorePort.storedToken =
        new RefreshTokenRecord(
            1L,
            10L,
            ACCOUNT_PUBLIC_ID,
            TOKEN_FAMILY_ID,
            NOW.minusSeconds(7200),
            NOW.minusSeconds(1),
            NOW.minusSeconds(3600),
            "LOGOUT",
            null);

    assertInvalidRefreshToken(
        () -> service.execute(new RotateRefreshTokenCommand("expired-revoked")));
    assertThat(lifecycleStorePort.reusedFamilyId).isEqualTo(TOKEN_FAMILY_ID);
    assertThat(lifecycleStorePort.reusedAt).isEqualTo(NOW);
  }

  /** 정지된 account의 refresh 요청은 새 token을 만들지 않고 기존 family를 폐기합니다. */
  @Test
  void revokesFamilyWhenAccountIsSuspended() {
    lifecycleStorePort.storedToken = ACTIVE_REFRESH_TOKEN;
    accountLookup.subject =
        new AccountAuthSubject(
            ACCOUNT_PUBLIC_ID, AccountAuthStatus.SUSPENDED, List.of("ROLE_MEMBER"), false);

    assertInvalidRefreshToken(() -> service.execute(new RotateRefreshTokenCommand("suspended")));
    assertThat(lifecycleStorePort.rejectedAccountFamilyId).isEqualTo(TOKEN_FAMILY_ID);
    assertThat(lifecycleStorePort.rejectedAccountReason)
        .isEqualTo(RefreshTokenRejectionReason.ACCOUNT_SUSPENDED);
    assertThat(lifecycleStorePort.rejectedAccountAt).isEqualTo(NOW);
    assertThat(accessTokenIssuerPort.issuedSubject).isNull();
  }

  private void assertInvalidRefreshToken(ThrowingOperation operation) {
    assertThatThrownBy(operation::execute)
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
  }

  private interface ThrowingOperation {

    void execute();
  }

  private static class FakeRefreshTokenHashPort implements RefreshTokenHashPort {

    private String hashedToken;

    @Override
    public String hash(String refreshToken) {
      hashedToken = refreshToken;
      return "hashed-" + refreshToken;
    }
  }

  private static class FakeRefreshTokenLifecycleStorePort
      implements RefreshTokenLifecycleStorePort {

    private RefreshTokenRecord storedToken;
    private RefreshTokenRecord rotatedToken;
    private IssuedRefreshToken replacementToken;
    private Instant rotatedAt;
    private UUID reusedFamilyId;
    private Instant reusedAt;
    private UUID rejectedAccountFamilyId;
    private RefreshTokenRejectionReason rejectedAccountReason;
    private Instant rejectedAccountAt;

    @Override
    public Optional<RefreshTokenRecord> findByHashForUpdate(String tokenHash) {
      return Optional.ofNullable(storedToken);
    }

    @Override
    public void rotate(
        RefreshTokenRecord currentToken, IssuedRefreshToken replacementToken, Instant rotatedAt) {
      this.rotatedToken = currentToken;
      this.replacementToken = replacementToken;
      this.rotatedAt = rotatedAt;
    }

    @Override
    public void revokeFamilyForReuse(RefreshTokenRecord reusedToken, Instant detectedAt) {
      reusedFamilyId = reusedToken.familyId();
      reusedAt = detectedAt;
    }

    @Override
    public void revokeFamilyForRejectedAccount(
        RefreshTokenRecord currentToken, RefreshTokenRejectionReason reason, Instant rejectedAt) {
      rejectedAccountFamilyId = currentToken.familyId();
      rejectedAccountReason = reason;
      rejectedAccountAt = rejectedAt;
    }

    @Override
    public void revokeForLogout(RefreshTokenRecord currentToken, Instant loggedOutAt) {
      // rotation 유스케이스 테스트에서는 호출되지 않아야 하는 port method입니다.
    }
  }

  private static class FakeAccountAuthSubjectLookup implements AccountAuthSubjectLookup {

    private AccountAuthSubject subject;

    @Override
    public Optional<AccountAuthSubject> findByPublicId(UUID accountPublicId) {
      return Optional.ofNullable(subject);
    }
  }

  private static class FakeAccessTokenIssuerPort implements AccessTokenIssuerPort {

    private AccountAuthSubject issuedSubject;
    private SessionAccess issuedSessionAccess;

    @Override
    public IssuedAccessToken issue(AccountAuthSubject subject, SessionAccess sessionAccess) {
      issuedSubject = subject;
      issuedSessionAccess = sessionAccess;
      return new IssuedAccessToken("new-access-token", NOW.plusSeconds(900), "Bearer");
    }
  }

  private static class FakeRefreshTokenIssuerPort implements RefreshTokenIssuerPort {

    private final IssuedRefreshToken issuedToken =
        new IssuedRefreshToken(
            "new-refresh-token",
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            TOKEN_FAMILY_ID,
            NOW,
            NOW.plusSeconds(2_592_000));

    @Override
    public IssuedRefreshToken issue() {
      return issuedToken;
    }
  }
}
