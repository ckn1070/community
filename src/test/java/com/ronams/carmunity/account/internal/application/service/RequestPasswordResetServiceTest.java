package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.internal.application.command.RequestPasswordResetCommand;
import com.ronams.carmunity.account.internal.application.port.out.AuthEventStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationConfirmFailureReason;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetAccount;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetAccountLookupPort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenGeneratorPort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.StoredPasswordResetToken;
import com.ronams.carmunity.account.internal.domain.AccountStatus;
import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import com.ronams.carmunity.notification.api.PasswordResetNotificationRequest;
import com.ronams.carmunity.notification.api.PasswordResetNotificationSender;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 비밀번호 재설정 요청 usecase가 enumeration 없이 reset token과 notification 경계를 조율하는지 검증합니다. */
class RequestPasswordResetServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-06T00:00:00Z");
  private static final UUID ACCOUNT_PUBLIC_ID =
      UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");

  private FakePasswordResetAccountLookupPort accountLookupPort;
  private FakePasswordResetTokenGeneratorPort tokenGeneratorPort;
  private PasswordResetTokenHasher tokenHasher;
  private FakePasswordResetTokenStorePort tokenStorePort;
  private FakePasswordResetNotificationSender notificationSender;
  private FakeAuthEventStorePort authEventStorePort;
  private RequestPasswordResetService service;

  @BeforeEach
  void setUp() {
    accountLookupPort = new FakePasswordResetAccountLookupPort();
    tokenGeneratorPort = new FakePasswordResetTokenGeneratorPort();
    tokenHasher = new PasswordResetTokenHasher();
    tokenStorePort = new FakePasswordResetTokenStorePort();
    notificationSender = new FakePasswordResetNotificationSender();
    authEventStorePort = new FakeAuthEventStorePort();
    service =
        new RequestPasswordResetService(
            accountLookupPort,
            tokenGeneratorPort,
            tokenHasher,
            tokenStorePort,
            notificationSender,
            authEventStorePort,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** 존재하지 않는 email은 account enumeration을 만들지 않도록 token, 발송, 감사 이벤트 없이 조용히 끝냅니다. */
  @Test
  void ignoresUnknownEmailWithoutSideEffects() {
    service.execute(new RequestPasswordResetCommand("unknown@example.com"));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(tokenStorePort.savedTokens).isEmpty();
    assertThat(notificationSender.requests).isEmpty();
    assertThat(authEventStorePort.passwordResetRequestedEvents).isEmpty();
  }

  /** SUSPENDED/WITHDRAWN account도 public 응답을 구분하지 않고 내부 부수효과 없이 처리합니다. */
  @Test
  void ignoresIneligibleAccountStatusWithoutSideEffects() {
    accountLookupPort.account =
        Optional.of(account(AccountStatus.SUSPENDED, "suspended@example.com"));

    service.execute(new RequestPasswordResetCommand("suspended@example.com"));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(tokenStorePort.savedTokens).isEmpty();
    assertThat(notificationSender.requests).isEmpty();
    assertThat(authEventStorePort.passwordResetRequestedEvents).isEmpty();
  }

  /** ACTIVE account는 reset token을 만들고 발송 성공 감사를 남깁니다. */
  @Test
  void issuesResetTokenForActiveAccount() {
    accountLookupPort.account = Optional.of(account(AccountStatus.ACTIVE, "member@example.com"));
    tokenGeneratorPort.rawToken = "new-reset-token";

    service.execute(new RequestPasswordResetCommand("MEMBER@example.com"));

    assertThat(tokenStorePort.savedTokens).hasSize(1);
    PasswordResetTokenStoreRequest savedToken = tokenStorePort.savedTokens.getFirst();
    assertThat(savedToken.accountId()).isEqualTo(1L);
    assertThat(savedToken.recipientNormalized()).isEqualTo("member@example.com");
    assertThat(savedToken.issuedAt()).isEqualTo(NOW);
    assertThat(savedToken.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
    assertThat(savedToken.tokenHash().value()).matches("^[0-9a-f]{64}$");
    assertThat(notificationSender.requests)
        .containsExactly(
            new PasswordResetNotificationRequest(
                "member@example.com", "new-reset-token", NOW.plusSeconds(1800)));
    assertThat(authEventStorePort.passwordResetRequestedEvents)
        .containsExactly(new RequestAuditRecord(1L, true));
  }

  /** PENDING account도 credential 복구는 허용하지만 account 활성화는 이 흐름에서 하지 않습니다. */
  @Test
  void issuesResetTokenForPendingAccount() {
    accountLookupPort.account = Optional.of(account(AccountStatus.PENDING, "pending@example.com"));

    service.execute(new RequestPasswordResetCommand("pending@example.com"));

    assertThat(tokenStorePort.savedTokens).hasSize(1);
    assertThat(notificationSender.requests).hasSize(1);
  }

  /** cooldown 안의 open reset token이 있으면 기존 token을 유지하고 새 token이나 발송을 만들지 않습니다. */
  @Test
  void keepsOpenTokenDuringCooldown() {
    accountLookupPort.account = Optional.of(account(AccountStatus.ACTIVE, "cooldown@example.com"));
    tokenStorePort.openToken = Optional.of(storedToken("a".repeat(64), NOW.minusSeconds(10)));

    service.execute(new RequestPasswordResetCommand("cooldown@example.com"));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(tokenStorePort.revokedAccountIds).isEmpty();
    assertThat(tokenStorePort.savedTokens).isEmpty();
    assertThat(notificationSender.requests).isEmpty();
  }

  /** cooldown 밖 재요청은 기존 open reset token을 폐기한 뒤 새 token을 발급합니다. */
  @Test
  void reissuesTokenAndRevokesOpenResetTokenAfterCooldown() {
    accountLookupPort.account = Optional.of(account(AccountStatus.ACTIVE, "member@example.com"));
    tokenStorePort.openToken = Optional.of(storedToken("b".repeat(64), NOW.minusSeconds(61)));

    service.execute(new RequestPasswordResetCommand("member@example.com"));

    assertThat(tokenStorePort.revokedAccountIds).containsExactly(1L);
    assertThat(tokenStorePort.savedTokens).hasSize(1);
  }

  /** provider 실패는 token 저장을 되돌리지 않고 실패 감사 이벤트로만 남깁니다. */
  @Test
  void recordsFailureAuditWhenNotificationFails() {
    accountLookupPort.account = Optional.of(account(AccountStatus.ACTIVE, "member@example.com"));
    notificationSender.failure = new IllegalStateException("provider failure");

    assertThatCode(() -> service.execute(new RequestPasswordResetCommand("member@example.com")))
        .doesNotThrowAnyException();

    assertThat(tokenStorePort.savedTokens).hasSize(1);
    assertThat(authEventStorePort.passwordResetRequestedEvents)
        .containsExactly(new RequestAuditRecord(1L, false));
  }

  /** email 형식 오류는 public API validation 실패로 매핑하고 외부 부수효과를 만들지 않습니다. */
  @Test
  void convertsInvalidEmailToInvalidRequest() {
    assertThatThrownBy(() -> service.execute(new RequestPasswordResetCommand("invalid-email")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(tokenStorePort.savedTokens).isEmpty();
  }

  private PasswordResetAccount account(AccountStatus status, String recipientNormalized) {
    return new PasswordResetAccount(1L, ACCOUNT_PUBLIC_ID, recipientNormalized, status);
  }

  private StoredPasswordResetToken storedToken(String tokenHash, Instant lastSentAt) {
    return new StoredPasswordResetToken(
        10L,
        1L,
        "member@example.com",
        PasswordResetTokenHash.from(tokenHash),
        lastSentAt.minusSeconds(120),
        lastSentAt.plusSeconds(1800),
        lastSentAt,
        Optional.empty(),
        Optional.empty());
  }

  private static class FakePasswordResetAccountLookupPort
      implements PasswordResetAccountLookupPort {

    private Optional<PasswordResetAccount> account = Optional.empty();

    @Override
    public Optional<PasswordResetAccount> findByNormalizedEmail(String normalizedEmail) {
      return account;
    }
  }

  private static class FakePasswordResetTokenGeneratorPort
      implements PasswordResetTokenGeneratorPort {

    private String rawToken = "raw-reset-token";
    private boolean generated;

    @Override
    public String generate() {
      generated = true;
      return rawToken;
    }
  }

  private static class FakePasswordResetTokenStorePort implements PasswordResetTokenStorePort {

    private Optional<StoredPasswordResetToken> openToken = Optional.empty();
    private final List<PasswordResetTokenStoreRequest> savedTokens = new ArrayList<>();
    private final List<Long> revokedAccountIds = new ArrayList<>();

    @Override
    public void save(PasswordResetTokenStoreRequest token) {
      savedTokens.add(token);
    }

    @Override
    public int revokeOpenPasswordResetTokens(Long accountId, Instant revokedAt) {
      revokedAccountIds.add(accountId);
      return 1;
    }

    @Override
    public Optional<StoredPasswordResetToken> findByHash(PasswordResetTokenHash tokenHash) {
      return Optional.empty();
    }

    @Override
    public Optional<StoredPasswordResetToken> findOpenPasswordResetToken(Long accountId) {
      return openToken;
    }

    @Override
    public void consume(Long tokenId, Instant consumedAt) {
      throw new AssertionError("이 테스트 fake는 reset 요청 흐름에서 token 소비 호출을 허용하지 않습니다.");
    }
  }

  private static class FakePasswordResetNotificationSender
      implements PasswordResetNotificationSender {

    private final List<PasswordResetNotificationRequest> requests = new ArrayList<>();
    private RuntimeException failure;

    @Override
    public void send(PasswordResetNotificationRequest request) {
      if (failure != null) {
        throw failure;
      }
      requests.add(request);
    }
  }

  private static class FakeAuthEventStorePort implements AuthEventStorePort {

    private final List<RequestAuditRecord> passwordResetRequestedEvents = new ArrayList<>();

    @Override
    public void recordEmailVerificationRequested(Long accountId, boolean successful) {
      throw new AssertionError("이 테스트 fake는 이메일 인증 요청 감사를 허용하지 않습니다.");
    }

    @Override
    public void recordEmailVerified(Long accountId) {
      throw new AssertionError("이 테스트 fake는 이메일 인증 완료 감사를 허용하지 않습니다.");
    }

    @Override
    public void recordEmailVerificationConfirmFailure(
        Long accountId, EmailVerificationConfirmFailureReason reason) {
      throw new AssertionError("이 테스트 fake는 이메일 인증 완료 실패 감사를 허용하지 않습니다.");
    }

    @Override
    public void recordPasswordResetRequested(Long accountId, boolean successful) {
      passwordResetRequestedEvents.add(new RequestAuditRecord(accountId, successful));
    }
  }

  private record RequestAuditRecord(Long accountId, boolean successful) {}
}
