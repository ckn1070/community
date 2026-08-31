package com.ronams.carmunity.auth.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.auth.internal.application.command.LogoutCommand;
import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRejectionReason;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenHashPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenLifecycleStorePort;
import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import com.ronams.carmunity.auth.internal.domain.RefreshSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 로그아웃 유스케이스의 refresh token 폐기 정책을 Spring 없이 검증합니다.
 *
 * <p>로그아웃은 token 상태를 외부에 노출하지 않는 멱등 command이며, reuse detection처럼 family 전체를 폐기하지 않습니다.
 */
class LogoutServiceTest {

  private static final Instant NOW = Instant.parse("2099-06-23T00:00:00Z");
  private static final UUID ACCOUNT_PUBLIC_ID =
      UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final UUID TOKEN_FAMILY_ID =
      UUID.fromString("6e74f192-9256-4569-8579-6b8f557d9cb1");
  private static final RefreshSession ACTIVE_REFRESH_TOKEN =
      refreshToken(NOW.plusSeconds(3600), null, null, null);

  private FakeRefreshTokenHashPort hashPort;
  private FakeRefreshTokenLifecycleStorePort lifecycleStorePort;
  private LogoutService service;

  @BeforeEach
  void setUp() {
    hashPort = new FakeRefreshTokenHashPort();
    lifecycleStorePort = new FakeRefreshTokenLifecycleStorePort();
    service = new LogoutService(hashPort, lifecycleStorePort, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** 활성 refresh token은 원문 hash로 조회한 뒤 같은 transaction 안에서 LOGOUT 폐기 대상으로 넘깁니다. */
  @Test
  void revokesActiveRefreshTokenForLogout() {
    lifecycleStorePort.storedToken = ACTIVE_REFRESH_TOKEN;

    service.execute(new LogoutCommand("raw-refresh-token"));

    assertThat(hashPort.hashedToken).isEqualTo("raw-refresh-token");
    assertThat(lifecycleStorePort.lookupHash).isEqualTo("hashed-raw-refresh-token");
    assertThat(lifecycleStorePort.logoutToken).isEqualTo(ACTIVE_REFRESH_TOKEN);
    assertThat(lifecycleStorePort.loggedOutAt).isEqualTo(NOW);
    assertThat(lifecycleStorePort.rotatedToken).isNull();
    assertThat(lifecycleStorePort.reusedToken).isNull();
    assertThat(lifecycleStorePort.rejectedAccountToken).isNull();
  }

  /** 존재하지 않는 token은 상태 추측을 막기 위해 오류 없이 종료하고 저장소 mutation을 호출하지 않습니다. */
  @Test
  void ignoresMissingRefreshToken() {
    service.execute(new LogoutCommand("missing-refresh-token"));

    assertThat(lifecycleStorePort.lookupHash).isEqualTo("hashed-missing-refresh-token");
    assertThat(lifecycleStorePort.logoutToken).isNull();
  }

  /** 만료 token은 이미 로그아웃 목적을 달성한 상태로 보고 추가 폐기나 감사 이벤트 저장을 요청하지 않습니다. */
  @Test
  void ignoresExpiredRefreshToken() {
    lifecycleStorePort.storedToken = refreshToken(NOW.minusSeconds(1), null, null, null);

    service.execute(new LogoutCommand("expired-refresh-token"));

    assertThat(lifecycleStorePort.logoutToken).isNull();
  }

  /** 이미 폐기된 token은 멱등 로그아웃으로 처리하고 reuse detection 경로로 보내지 않습니다. */
  @Test
  void ignoresAlreadyRevokedRefreshTokenWithoutReuseDetection() {
    lifecycleStorePort.storedToken =
        refreshToken(NOW.plusSeconds(3600), NOW.minusSeconds(30), "LOGOUT", null);

    service.execute(new LogoutCommand("revoked-refresh-token"));

    assertThat(lifecycleStorePort.logoutToken).isNull();
    assertThat(lifecycleStorePort.reusedToken).isNull();
  }

  /** rotation으로 교체된 token이 들어와도 로그아웃 API에서는 family 폐기 없이 멱등 성공으로 닫습니다. */
  @Test
  void ignoresReplacedRefreshTokenWithoutFamilyRevocation() {
    lifecycleStorePort.storedToken =
        refreshToken(NOW.plusSeconds(3600), NOW.minusSeconds(30), "ROTATED", 2L);

    service.execute(new LogoutCommand("replaced-refresh-token"));

    assertThat(lifecycleStorePort.logoutToken).isNull();
    assertThat(lifecycleStorePort.reusedToken).isNull();
  }

  private static RefreshSession refreshToken(
      Instant expiresAt, Instant revokedAt, String revokedReason, Long replacedByTokenId) {
    return new RefreshSession(
        1L,
        ACCOUNT_PUBLIC_ID,
        TOKEN_FAMILY_ID,
        NOW.minusSeconds(60),
        expiresAt,
        revokedAt,
        revokedReason,
        replacedByTokenId);
  }

  private static class FakeRefreshTokenHashPort implements RefreshTokenHashPort {

    private String hashedToken;

    @Override
    public String hash(String refreshToken) {
      hashedToken = refreshToken;
      return "hashed-" + refreshToken;
    }
  }

  /**
   * 로그아웃 service가 단일 token 폐기 port만 호출하는지 확인하는 lifecycle 저장소 대역입니다.
   *
   * <p>family 폐기와 rotation 호출 기록을 함께 보관해 로그아웃이 기존 refresh 재발급 정책을 건드리지 않음을 검증합니다.
   */
  private static class FakeRefreshTokenLifecycleStorePort
      implements RefreshTokenLifecycleStorePort {

    private RefreshSession storedToken;
    private String lookupHash;
    private RefreshSession logoutToken;
    private Instant loggedOutAt;
    private RefreshSession rotatedToken;
    private RefreshSession reusedToken;
    private RefreshSession rejectedAccountToken;

    @Override
    public Optional<RefreshSession> findByHashForUpdate(String tokenHash) {
      lookupHash = tokenHash;
      return Optional.ofNullable(storedToken);
    }

    @Override
    public void issue(UUID accountPublicId, IssuedRefreshToken refreshToken) {}

    @Override
    public void rotate(
        RefreshSession currentToken, IssuedRefreshToken replacementToken, Instant rotatedAt) {
      rotatedToken = currentToken;
    }

    @Override
    public void revokeFamilyForReuse(RefreshSession reusedToken, Instant detectedAt) {
      this.reusedToken = reusedToken;
    }

    @Override
    public void revokeFamilyForRejectedAccount(
        RefreshSession currentToken, RefreshTokenRejectionReason reason, Instant rejectedAt) {
      rejectedAccountToken = currentToken;
    }

    @Override
    public void revokeForLogout(RefreshSession currentToken, Instant loggedOutAt) {
      logoutToken = currentToken;
      this.loggedOutAt = loggedOutAt;
    }

    @Override
    public void revokeActiveSessionsForCredentialChange(UUID accountPublicId, Instant changedAt) {}
  }
}
