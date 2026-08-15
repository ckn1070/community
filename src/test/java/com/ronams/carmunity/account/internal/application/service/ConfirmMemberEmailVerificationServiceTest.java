package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ConfirmMemberEmailVerificationCommand;
import com.ronams.carmunity.account.internal.application.port.out.AuthEventStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationAccountActivationPort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationConfirmFailureReason;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.StoredEmailVerificationToken;
import com.ronams.carmunity.account.internal.domain.EmailVerificationTokenHash;
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

/** 이메일 인증 완료 use case가 token 상태 검증, account 활성화, token 소비, 감사 기록 호출을 조율하는지 검증합니다. */
class ConfirmMemberEmailVerificationServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");

  private EmailVerificationTokenHasher tokenHasher;
  private FakeEmailVerificationTokenStorePort tokenStorePort;
  private FakeEmailVerificationAccountActivationPort accountActivationPort;
  private FakeAuthEventStorePort authEventStorePort;
  private ConfirmMemberEmailVerificationService service;

  @BeforeEach
  void setUp() {
    tokenHasher = new EmailVerificationTokenHasher();
    tokenStorePort = new FakeEmailVerificationTokenStorePort();
    accountActivationPort = new FakeEmailVerificationAccountActivationPort();
    authEventStorePort = new FakeAuthEventStorePort();
    service =
        new ConfirmMemberEmailVerificationService(
            tokenHasher,
            tokenStorePort,
            accountActivationPort,
            authEventStorePort,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** valid token은 account 활성화, token 소비, EMAIL_VERIFICATION_CONFIRM 감사 기록을 모두 호출합니다. */
  @Test
  void confirmsValidToken() {
    String rawToken = "valid-token";
    tokenStorePort.token =
        Optional.of(openToken(10L, 1L, tokenHasher.hash(rawToken), NOW.plusSeconds(60)));

    service.execute(new ConfirmMemberEmailVerificationCommand(rawToken));

    assertThat(accountActivationPort.activatedAccounts)
        .containsExactly(new ActivationRecord(1L, NOW));
    assertThat(tokenStorePort.consumedTokens).containsExactly(new TokenConsumeRecord(10L, NOW));
    assertThat(authEventStorePort.events).containsExactly(AuthEventRecord.success(1L));
  }

  /** 알 수 없는 token은 account 상태를 바꾸지 않고 실패 감사 이벤트를 남긴 뒤 invalid token 오류로 닫습니다. */
  @Test
  void rejectsUnknownToken() {
    assertThatThrownBy(() -> service.execute(new ConfirmMemberEmailVerificationCommand("unknown")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID));

    assertThat(accountActivationPort.activatedAccounts).isEmpty();
    assertThat(tokenStorePort.consumedTokens).isEmpty();
    assertThat(authEventStorePort.events)
        .containsExactly(
            AuthEventRecord.failure(null, EmailVerificationConfirmFailureReason.TOKEN_INVALID));
  }

  /** 만료 token은 실패 감사 이벤트를 남기고 expired 오류로 응답하되 account와 token terminal state는 변경하지 않습니다. */
  @Test
  void rejectsExpiredTokenWithoutConsuming() {
    String rawToken = "expired-token";
    tokenStorePort.token =
        Optional.of(openToken(10L, 1L, tokenHasher.hash(rawToken), NOW.minusSeconds(1)));

    assertThatThrownBy(() -> service.execute(new ConfirmMemberEmailVerificationCommand(rawToken)))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.EMAIL_VERIFICATION_TOKEN_EXPIRED));

    assertThat(accountActivationPort.activatedAccounts).isEmpty();
    assertThat(tokenStorePort.consumedTokens).isEmpty();
    assertThat(authEventStorePort.events)
        .containsExactly(
            AuthEventRecord.failure(1L, EmailVerificationConfirmFailureReason.TOKEN_EXPIRED));
  }

  /** 이미 소비된 token은 single-use 원칙에 따라 실패 감사 이벤트를 남기고 invalid로 처리합니다. */
  @Test
  void rejectsConsumedToken() {
    String rawToken = "consumed-token";
    tokenStorePort.token =
        Optional.of(
            new StoredEmailVerificationToken(
                10L,
                1L,
                "member@example.com",
                tokenHasher.hash(rawToken),
                NOW.minusSeconds(120),
                NOW.plusSeconds(60),
                NOW.minusSeconds(120),
                Optional.of(NOW.minusSeconds(1)),
                Optional.empty()));

    assertThatThrownBy(() -> service.execute(new ConfirmMemberEmailVerificationCommand(rawToken)))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID));

    assertThat(accountActivationPort.activatedAccounts).isEmpty();
    assertThat(tokenStorePort.consumedTokens).isEmpty();
    assertThat(authEventStorePort.events)
        .containsExactly(
            AuthEventRecord.failure(1L, EmailVerificationConfirmFailureReason.TOKEN_ALREADY_USED));
  }

  /** 폐기된 token은 재발급된 링크만 유효하므로 실패 감사 이벤트를 남기고 invalid로 처리합니다. */
  @Test
  void rejectsRevokedToken() {
    String rawToken = "revoked-token";
    tokenStorePort.token =
        Optional.of(
            new StoredEmailVerificationToken(
                10L,
                1L,
                "member@example.com",
                tokenHasher.hash(rawToken),
                NOW.minusSeconds(120),
                NOW.plusSeconds(60),
                NOW.minusSeconds(120),
                Optional.empty(),
                Optional.of(NOW.minusSeconds(1))));

    assertThatThrownBy(() -> service.execute(new ConfirmMemberEmailVerificationCommand(rawToken)))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID));

    assertThat(accountActivationPort.activatedAccounts).isEmpty();
    assertThat(tokenStorePort.consumedTokens).isEmpty();
    assertThat(authEventStorePort.events)
        .containsExactly(
            AuthEventRecord.failure(1L, EmailVerificationConfirmFailureReason.TOKEN_REVOKED));
  }

  /** account가 더 이상 PENDING이 아니면 token을 소비하지 않고 실패 감사 이벤트를 남긴 뒤 invalid로 닫습니다. */
  @Test
  void rejectsWhenAccountCannotBeActivated() {
    String rawToken = "active-account-token";
    accountActivationPort.activationResult = false;
    tokenStorePort.token =
        Optional.of(openToken(10L, 1L, tokenHasher.hash(rawToken), NOW.plusSeconds(60)));

    assertThatThrownBy(() -> service.execute(new ConfirmMemberEmailVerificationCommand(rawToken)))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID));

    assertThat(accountActivationPort.activatedAccounts)
        .containsExactly(new ActivationRecord(1L, NOW));
    assertThat(tokenStorePort.consumedTokens).isEmpty();
    assertThat(authEventStorePort.events)
        .containsExactly(
            AuthEventRecord.failure(1L, EmailVerificationConfirmFailureReason.ACCOUNT_NOT_PENDING));
  }

  /** blank token은 token 조회 전에 실패 감사 이벤트를 남기고 공통 invalid request로 변환합니다. */
  @Test
  void rejectsBlankTokenAsInvalidRequest() {
    assertThatThrownBy(() -> service.execute(new ConfirmMemberEmailVerificationCommand(" ")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(tokenStorePort.lookupTokenHashes).isEmpty();
    assertThat(authEventStorePort.events)
        .containsExactly(
            AuthEventRecord.failure(null, EmailVerificationConfirmFailureReason.TOKEN_INVALID));
  }

  private StoredEmailVerificationToken openToken(
      Long tokenId, Long accountId, EmailVerificationTokenHash tokenHash, Instant expiresAt) {
    return new StoredEmailVerificationToken(
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

  /** token hash 조회 입력과 token 소비 호출을 기록하고, 테스트가 지정한 token snapshot을 반환하는 대역입니다. */
  private static class FakeEmailVerificationTokenStorePort
      implements EmailVerificationTokenStorePort {

    private Optional<StoredEmailVerificationToken> token = Optional.empty();
    private final List<EmailVerificationTokenHash> lookupTokenHashes = new ArrayList<>();
    private final List<TokenConsumeRecord> consumedTokens = new ArrayList<>();

    @Override
    public void save(
        com.ronams.carmunity.account.internal.application.port.out
                .EmailVerificationTokenStoreRequest
            token) {
      throw new AssertionError("이 테스트 fake는 인증 완료 흐름에서 token 저장 호출을 허용하지 않습니다.");
    }

    @Override
    public int revokeOpenEmailVerificationTokens(Long accountId, Instant revokedAt) {
      return 0;
    }

    @Override
    public Optional<StoredEmailVerificationToken> findByHash(EmailVerificationTokenHash tokenHash) {
      lookupTokenHashes.add(tokenHash);
      return token;
    }

    @Override
    public Optional<StoredEmailVerificationToken> findOpenEmailVerificationToken(Long accountId) {
      return Optional.empty();
    }

    @Override
    public void consume(Long tokenId, Instant consumedAt) {
      consumedTokens.add(new TokenConsumeRecord(tokenId, consumedAt));
    }
  }

  /** account 활성화 호출의 account id와 verifiedAt을 기록하고, 활성화 성공 여부를 테스트가 제어하는 대역입니다. */
  private static class FakeEmailVerificationAccountActivationPort
      implements EmailVerificationAccountActivationPort {

    private boolean activationResult = true;
    private final List<ActivationRecord> activatedAccounts = new ArrayList<>();

    @Override
    public boolean activatePendingAccount(Long accountId, Instant verifiedAt) {
      activatedAccounts.add(new ActivationRecord(accountId, verifiedAt));
      return activationResult;
    }
  }

  /** EMAIL_VERIFICATION_CONFIRM 감사 결과와 reason을 기록하고, 요청 감사 이벤트 호출은 실패로 고정하는 대역입니다. */
  private static class FakeAuthEventStorePort implements AuthEventStorePort {

    private final List<AuthEventRecord> events = new ArrayList<>();

    @Override
    public void recordEmailVerificationRequested(Long accountId, boolean successful) {
      throw new AssertionError("이 테스트 fake는 인증 완료 흐름에서 요청 감사 이벤트를 허용하지 않습니다.");
    }

    @Override
    public void recordEmailVerified(Long accountId) {
      events.add(AuthEventRecord.success(accountId));
    }

    @Override
    public void recordEmailVerificationConfirmFailure(
        Long accountId, EmailVerificationConfirmFailureReason reason) {
      events.add(AuthEventRecord.failure(accountId, reason));
    }

    @Override
    public void recordPasswordResetRequested(Long accountId, boolean successful) {
      throw new AssertionError("이 테스트 fake는 비밀번호 재설정 요청 감사를 허용하지 않습니다.");
    }
  }

  private record ActivationRecord(Long accountId, Instant verifiedAt) {}

  private record TokenConsumeRecord(Long tokenId, Instant consumedAt) {}

  private record AuthEventRecord(
      Long accountId, boolean successful, EmailVerificationConfirmFailureReason reason) {

    private static AuthEventRecord success(Long accountId) {
      return new AuthEventRecord(accountId, true, null);
    }

    private static AuthEventRecord failure(
        Long accountId, EmailVerificationConfirmFailureReason reason) {
      return new AuthEventRecord(accountId, false, reason);
    }
  }
}
