package com.ronams.carmunity.auth.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.api.AccountAuthStatus;
import com.ronams.carmunity.account.api.PasswordLoginAccount;
import com.ronams.carmunity.account.api.PasswordLoginAccountLookup;
import com.ronams.carmunity.account.api.PasswordLoginFailureReason;
import com.ronams.carmunity.account.api.PasswordLoginResultRecorder;
import com.ronams.carmunity.auth.internal.application.AuthErrorCode;
import com.ronams.carmunity.auth.internal.application.port.out.PasswordVerifierPort;
import com.ronams.carmunity.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** token 발급과 분리된 공통 비밀번호 인증 정책을 검증합니다. */
class AuthenticatePasswordServiceTest {

  private static final UUID ACCOUNT_ID = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

  private FakeAccountLookup accountLookup;
  private FakePasswordVerifier passwordVerifier;
  private FakeResultRecorder resultRecorder;
  private AuthenticatePasswordService service;

  @BeforeEach
  void setUp() {
    accountLookup = new FakeAccountLookup();
    passwordVerifier = new FakePasswordVerifier();
    resultRecorder = new FakeResultRecorder();
    service =
        new AuthenticatePasswordService(
            accountLookup,
            passwordVerifier,
            normalizedIdentifier -> "identifier-hash",
            resultRecorder,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** ACTIVE 계정은 정규화된 email로 조회하고 full session 인증 결과를 반환합니다. */
  @Test
  void authenticatesActiveAccountWithoutIssuingTokens() {
    accountLookup.account = Optional.of(account(AccountAuthStatus.ACTIVE));

    var result = service.authenticate(" ADMIN@example.COM ", "correct-password");

    assertThat(accountLookup.normalizedEmail).isEqualTo("admin@example.com");
    assertThat(result.account()).isEqualTo(accountLookup.account.orElseThrow());
    assertThat(result.sessionAccess()).isEqualTo(SessionAccess.FULL);
    assertThat(resultRecorder.successAccountPublicId).isNull();
  }

  /** 존재하지 않는 email은 원문 대신 hash를 기록하고 API와 같은 인증 오류로 닫습니다. */
  @Test
  void rejectsUnknownAccountAndRecordsHashedIdentifier() {
    assertThatThrownBy(() -> service.authenticate("unknown@example.com", "wrong-password"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));

    assertThat(resultRecorder.unknownIdentifierHash).isEqualTo("identifier-hash");
    assertThat(resultRecorder.occurredAt).isEqualTo(NOW);
  }

  /** password 불일치는 알려진 account의 실패 횟수와 감사 사유를 기존 정책대로 기록합니다. */
  @Test
  void rejectsPasswordMismatchAndRecordsKnownFailure() {
    accountLookup.account = Optional.of(account(AccountAuthStatus.ACTIVE));
    passwordVerifier.matches = false;

    assertThatThrownBy(() -> service.authenticate("admin@example.com", "wrong-password"))
        .isInstanceOf(ApiException.class);

    assertThat(resultRecorder.knownAccountId).isEqualTo(ACCOUNT_ID);
    assertThat(resultRecorder.failureReason)
        .isEqualTo(PasswordLoginFailureReason.PASSWORD_MISMATCH);
    assertThat(resultRecorder.occurredAt).isEqualTo(NOW);
  }

  /** SUSPENDED 계정은 password가 맞아도 제한 사유를 기록하고 session 인증을 만들지 않습니다. */
  @Test
  void rejectsSuspendedAccount() {
    accountLookup.account = Optional.of(account(AccountAuthStatus.SUSPENDED));

    assertThatThrownBy(() -> service.authenticate("admin@example.com", "correct-password"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AuthErrorCode.ACCOUNT_LOGIN_RESTRICTED));

    assertThat(resultRecorder.failureReason)
        .isEqualTo(PasswordLoginFailureReason.ACCOUNT_SUSPENDED);
  }

  private static PasswordLoginAccount account(AccountAuthStatus status) {
    return new PasswordLoginAccount(
        ACCOUNT_ID, status, "{argon2}hash", List.of("ROLE_ADMIN"), false);
  }

  private static class FakeAccountLookup implements PasswordLoginAccountLookup {

    private Optional<PasswordLoginAccount> account = Optional.empty();
    private String normalizedEmail;

    @Override
    public Optional<PasswordLoginAccount> findByNormalizedEmail(String normalizedEmail) {
      this.normalizedEmail = normalizedEmail;
      return account;
    }
  }

  private static class FakePasswordVerifier implements PasswordVerifierPort {

    private boolean matches = true;

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
      return matches && "correct-password".equals(rawPassword);
    }
  }

  private static class FakeResultRecorder implements PasswordLoginResultRecorder {

    private UUID successAccountPublicId;
    private UUID knownAccountId;
    private PasswordLoginFailureReason failureReason;
    private String unknownIdentifierHash;
    private Instant occurredAt;

    @Override
    public void recordSuccess(UUID accountPublicId, Instant occurredAt) {
      successAccountPublicId = accountPublicId;
    }

    @Override
    public void recordInteractiveSuccess(UUID accountPublicId, Instant occurredAt) {
      // 이 service는 token 발급 없는 interactive 성공을 기록하지 않습니다.
    }

    @Override
    public void recordKnownFailure(
        UUID accountPublicId,
        String identifierHash,
        PasswordLoginFailureReason reason,
        Instant occurredAt) {
      knownAccountId = accountPublicId;
      failureReason = reason;
      this.occurredAt = occurredAt;
    }

    @Override
    public void recordUnknownFailure(String identifierHash, Instant occurredAt) {
      unknownIdentifierHash = identifierHash;
      this.occurredAt = occurredAt;
    }
  }
}
