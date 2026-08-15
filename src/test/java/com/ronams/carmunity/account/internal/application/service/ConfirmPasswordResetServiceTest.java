package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ConfirmPasswordResetCommand;
import com.ronams.carmunity.account.internal.application.port.out.PasswordHasherPort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetCredentialStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.StoredPasswordResetToken;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 비밀번호 재설정 완료 usecase가 token 검증과 credential 갱신 경계를 조율하는지 검증합니다. */
class ConfirmPasswordResetServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-06T00:00:00Z");

  private PasswordResetTokenHasher tokenHasher;
  private FakePasswordResetTokenStorePort tokenStorePort;
  private FakePasswordResetCredentialStorePort credentialStorePort;
  private FakePasswordHasherPort passwordHasherPort;
  private ConfirmPasswordResetService service;

  @BeforeEach
  void setUp() {
    tokenHasher = new PasswordResetTokenHasher();
    tokenStorePort = new FakePasswordResetTokenStorePort();
    credentialStorePort = new FakePasswordResetCredentialStorePort();
    passwordHasherPort = new FakePasswordHasherPort();
    service =
        new ConfirmPasswordResetService(
            tokenHasher,
            tokenStorePort,
            credentialStorePort,
            passwordHasherPort,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** valid token은 token 소비, 새 password hash 저장, 완료 감사를 모두 호출합니다. */
  @Test
  void confirmsValidTokenAndResetsPassword() {
    String rawToken = "valid-reset-token";
    tokenStorePort.token =
        Optional.of(openToken(10L, 1L, tokenHasher.hash(rawToken), NOW.plusSeconds(60)));

    service.execute(command(rawToken, "new correct horse battery staple"));

    assertThat(passwordHasherPort.lastRawPassword).isEqualTo("new correct horse battery staple");
    assertThat(tokenStorePort.consumedTokens).containsExactly(new TokenConsumeRecord(10L, NOW));
    assertThat(credentialStorePort.resetRequests)
        .containsExactly(new PasswordResetRecord(1L, "{argon2}new-password-hash", NOW));
  }

  /** 알 수 없는 token은 password를 바꾸지 않고 reset token invalid 오류로 닫습니다. */
  @Test
  void rejectsUnknownToken() {
    assertThatThrownBy(
            () -> service.execute(command("unknown-token", "new correct horse battery staple")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.PASSWORD_RESET_TOKEN_INVALID));

    assertThat(credentialStorePort.resetRequests).isEmpty();
    assertThat(tokenStorePort.consumedTokens).isEmpty();
  }

  /** 만료 token은 expired 오류로 응답하고 token 소비와 password 갱신을 하지 않습니다. */
  @Test
  void rejectsExpiredTokenWithoutConsuming() {
    String rawToken = "expired-reset-token";
    tokenStorePort.token =
        Optional.of(openToken(10L, 1L, tokenHasher.hash(rawToken), NOW.minusSeconds(1)));

    assertThatThrownBy(() -> service.execute(command(rawToken, "new correct horse battery staple")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.PASSWORD_RESET_TOKEN_EXPIRED));

    assertThat(credentialStorePort.resetRequests).isEmpty();
    assertThat(tokenStorePort.consumedTokens).isEmpty();
  }

  /** 이미 소비된 token은 single-use 원칙에 따라 invalid로 닫습니다. */
  @Test
  void rejectsConsumedToken() {
    String rawToken = "consumed-reset-token";
    tokenStorePort.token =
        Optional.of(
            new StoredPasswordResetToken(
                10L,
                1L,
                "member@example.com",
                tokenHasher.hash(rawToken),
                NOW.minusSeconds(120),
                NOW.plusSeconds(60),
                NOW.minusSeconds(120),
                Optional.of(NOW.minusSeconds(1)),
                Optional.empty()));

    assertThatThrownBy(() -> service.execute(command(rawToken, "new correct horse battery staple")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.PASSWORD_RESET_TOKEN_INVALID));

    assertThat(credentialStorePort.resetRequests).isEmpty();
    assertThat(tokenStorePort.consumedTokens).isEmpty();
  }

  /** 새 비밀번호 확인이 다르면 token 조회 전에 INVALID_REQUEST로 닫습니다. */
  @Test
  void rejectsPasswordConfirmationMismatchBeforeTokenLookup() {
    assertThatThrownBy(
            () ->
                service.execute(
                    new ConfirmPasswordResetCommand(
                        "raw-token",
                        "new correct horse battery staple",
                        "different correct horse battery staple")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(tokenStorePort.lookupTokenHashes).isEmpty();
    assertThat(passwordHasherPort.lastRawPassword).isNull();
  }

  /** blank token은 hash 계산 단계에서 INVALID_REQUEST로 변환하고 저장소를 조회하지 않습니다. */
  @Test
  void rejectsBlankTokenAsInvalidRequest() {
    assertThatThrownBy(() -> service.execute(command(" ", "new correct horse battery staple")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(tokenStorePort.lookupTokenHashes).isEmpty();
  }

  private ConfirmPasswordResetCommand command(String token, String newPassword) {
    return new ConfirmPasswordResetCommand(token, newPassword, newPassword);
  }

  private StoredPasswordResetToken openToken(
      Long tokenId, Long accountId, PasswordResetTokenHash tokenHash, Instant expiresAt) {
    return new StoredPasswordResetToken(
        tokenId,
        accountId,
        "member@example.com",
        tokenHash,
        NOW.minusSeconds(120),
        expiresAt,
        NOW.minusSeconds(120),
        Optional.empty(),
        Optional.empty());
  }

  private static class FakePasswordResetTokenStorePort implements PasswordResetTokenStorePort {

    private Optional<StoredPasswordResetToken> token = Optional.empty();
    private final List<PasswordResetTokenHash> lookupTokenHashes = new ArrayList<>();
    private final List<TokenConsumeRecord> consumedTokens = new ArrayList<>();

    @Override
    public void save(PasswordResetTokenStoreRequest token) {
      throw new AssertionError("이 테스트 fake는 reset 완료 흐름에서 token 저장 호출을 허용하지 않습니다.");
    }

    @Override
    public int revokeOpenPasswordResetTokens(Long accountId, Instant revokedAt) {
      return 0;
    }

    @Override
    public Optional<StoredPasswordResetToken> findByHash(PasswordResetTokenHash tokenHash) {
      lookupTokenHashes.add(tokenHash);
      return token;
    }

    @Override
    public Optional<StoredPasswordResetToken> findOpenPasswordResetToken(Long accountId) {
      return Optional.empty();
    }

    @Override
    public void consume(Long tokenId, Instant consumedAt) {
      consumedTokens.add(new TokenConsumeRecord(tokenId, consumedAt));
    }
  }

  private static class FakePasswordResetCredentialStorePort
      implements PasswordResetCredentialStorePort {

    private final List<PasswordResetRecord> resetRequests = new ArrayList<>();

    @Override
    public void resetPassword(Long accountId, PasswordHash passwordHash, Instant changedAt) {
      resetRequests.add(new PasswordResetRecord(accountId, passwordHash.value(), changedAt));
    }
  }

  private static class FakePasswordHasherPort implements PasswordHasherPort {

    private String lastRawPassword;

    @Override
    public PasswordHash hash(String rawPassword) {
      lastRawPassword = rawPassword;
      return PasswordHash.from("{argon2}new-password-hash");
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
      return false;
    }
  }

  private record TokenConsumeRecord(Long tokenId, Instant consumedAt) {}

  private record PasswordResetRecord(Long accountId, String passwordHash, Instant changedAt) {}
}
