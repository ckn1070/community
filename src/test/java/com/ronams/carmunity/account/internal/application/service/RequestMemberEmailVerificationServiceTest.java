package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.internal.application.command.RequestMemberEmailVerificationCommand;
import com.ronams.carmunity.account.internal.application.port.out.AuthEventStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationAccount;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationAccountLookupPort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationConfirmFailureReason;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenGeneratorPort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.EmailVerificationTokenStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.StoredEmailVerificationToken;
import com.ronams.carmunity.account.internal.domain.AccountStatus;
import com.ronams.carmunity.account.internal.domain.EmailVerificationTokenHash;
import com.ronams.carmunity.notification.api.EmailVerificationNotificationRequest;
import com.ronams.carmunity.notification.api.EmailVerificationNotificationSender;
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

/** 이메일 인증 요청 use case가 enumeration 없이 token 발급과 발송 경계를 조율하는지 검증합니다. */
class RequestMemberEmailVerificationServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");

  private FakeEmailVerificationAccountLookupPort accountLookupPort;
  private FakeEmailVerificationTokenGeneratorPort tokenGeneratorPort;
  private EmailVerificationTokenHasher tokenHasher;
  private FakeEmailVerificationTokenStorePort tokenStorePort;
  private FakeEmailVerificationNotificationSender notificationSender;
  private FakeAuthEventStorePort authEventStorePort;
  private RequestMemberEmailVerificationService service;

  @BeforeEach
  void setUp() {
    accountLookupPort = new FakeEmailVerificationAccountLookupPort();
    tokenGeneratorPort = new FakeEmailVerificationTokenGeneratorPort();
    tokenHasher = new EmailVerificationTokenHasher();
    tokenStorePort = new FakeEmailVerificationTokenStorePort();
    notificationSender = new FakeEmailVerificationNotificationSender();
    authEventStorePort = new FakeAuthEventStorePort();
    service =
        new RequestMemberEmailVerificationService(
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
    service.execute(command("unknown@example.com"));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(tokenStorePort.savedTokens).isEmpty();
    assertThat(notificationSender.requests).isEmpty();
    assertThat(authEventStorePort.events).isEmpty();
  }

  /** 이미 인증된 account도 public 응답을 바꾸지 않고 내부 부수효과 없이 처리합니다. */
  @Test
  void ignoresAlreadyActiveAccountWithoutSideEffects() {
    accountLookupPort.account =
        Optional.of(new EmailVerificationAccount(1L, "active@example.com", AccountStatus.ACTIVE));

    service.execute(command("active@example.com"));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(tokenStorePort.savedTokens).isEmpty();
    assertThat(notificationSender.requests).isEmpty();
    assertThat(authEventStorePort.events).isEmpty();
  }

  /** cooldown 안의 open token이 있으면 기존 token을 유지하고 새 token 발급이나 발송을 하지 않습니다. */
  @Test
  void keepsOpenTokenDuringCooldown() {
    accountLookupPort.account =
        Optional.of(
            new EmailVerificationAccount(1L, "cooldown@example.com", AccountStatus.PENDING));
    tokenStorePort.openToken = Optional.of(storedToken(1L, "a".repeat(64), NOW.minusSeconds(10)));

    service.execute(command("cooldown@example.com"));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(tokenStorePort.revokedAccountIds).isEmpty();
    assertThat(tokenStorePort.savedTokens).isEmpty();
    assertThat(notificationSender.requests).isEmpty();
    assertThat(authEventStorePort.events).isEmpty();
  }

  /** cooldown 밖에서는 기존 open token을 폐기하고 새 token을 저장한 뒤 발송 요청과 성공 감사를 남깁니다. */
  @Test
  void reissuesTokenAndSendsEmailAfterCooldown() {
    accountLookupPort.account =
        Optional.of(new EmailVerificationAccount(1L, "member@example.com", AccountStatus.PENDING));
    tokenGeneratorPort.rawToken = "new-raw-token";
    tokenStorePort.openToken = Optional.of(storedToken(1L, "b".repeat(64), NOW.minusSeconds(61)));

    service.execute(command("member@example.com"));

    assertThat(tokenStorePort.revokedAccountIds).containsExactly(1L);
    assertThat(tokenStorePort.savedTokens).hasSize(1);
    EmailVerificationTokenStoreRequest savedToken = tokenStorePort.savedTokens.getFirst();
    assertThat(savedToken.accountId()).isEqualTo(1L);
    assertThat(savedToken.recipientNormalized()).isEqualTo("member@example.com");
    assertThat(savedToken.issuedAt()).isEqualTo(NOW);
    assertThat(savedToken.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
    assertThat(savedToken.lastSentAt()).isEqualTo(NOW);
    assertThat(savedToken.tokenHash().value()).matches("^[0-9a-f]{64}$");

    assertThat(notificationSender.requests)
        .containsExactly(
            new EmailVerificationNotificationRequest(
                "member@example.com", "new-raw-token", NOW.plusSeconds(1800)));
    assertThat(authEventStorePort.events).containsExactly(new AuthEventRecord(1L, true));
  }

  /** 발송 실패는 token 저장 결과를 되돌리지 않고 실패 감사 이벤트로만 남깁니다. */
  @Test
  void recordsFailureAuditWhenNotificationFails() {
    accountLookupPort.account =
        Optional.of(new EmailVerificationAccount(1L, "member@example.com", AccountStatus.PENDING));
    notificationSender.failure = new IllegalStateException("provider failure");

    assertThatCode(() -> service.execute(command("member@example.com"))).doesNotThrowAnyException();

    assertThat(tokenStorePort.savedTokens).hasSize(1);
    assertThat(authEventStorePort.events).containsExactly(new AuthEventRecord(1L, false));
  }

  /** email 형식 오류는 public API validation 실패로 매핑하고 외부 부수효과를 만들지 않습니다. */
  @Test
  void convertsInvalidEmailToInvalidRequest() {
    assertThatThrownBy(() -> service.execute(command("invalid-email")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(tokenGeneratorPort.generated).isFalse();
    assertThat(tokenStorePort.savedTokens).isEmpty();
    assertThat(notificationSender.requests).isEmpty();
  }

  private RequestMemberEmailVerificationCommand command(String email) {
    return new RequestMemberEmailVerificationCommand(email);
  }

  private StoredEmailVerificationToken storedToken(
      Long accountId, String tokenHash, Instant lastSentAt) {
    return new StoredEmailVerificationToken(
        10L,
        accountId,
        "member@example.com",
        EmailVerificationTokenHash.from(tokenHash),
        lastSentAt.minusSeconds(120),
        lastSentAt.plusSeconds(1800),
        lastSentAt,
        Optional.empty(),
        Optional.empty());
  }

  /** normalized email 조회 결과를 테스트가 제어하는 account lookup 대역입니다. */
  private static class FakeEmailVerificationAccountLookupPort
      implements EmailVerificationAccountLookupPort {

    private Optional<EmailVerificationAccount> account = Optional.empty();

    @Override
    public Optional<EmailVerificationAccount> findByNormalizedEmail(String normalizedEmail) {
      return account;
    }
  }

  /** 발급 raw token 값을 테스트가 고정하고, generate 호출 여부를 기록하는 대역입니다. */
  private static class FakeEmailVerificationTokenGeneratorPort
      implements EmailVerificationTokenGeneratorPort {

    private String rawToken = "raw-token";
    private boolean generated;

    @Override
    public String generate() {
      generated = true;
      return rawToken;
    }
  }

  /** open token 조회 결과, 저장 요청, 폐기 대상 account id를 기록하는 token store 대역입니다. */
  private static class FakeEmailVerificationTokenStorePort
      implements EmailVerificationTokenStorePort {

    private Optional<StoredEmailVerificationToken> openToken = Optional.empty();
    private final List<EmailVerificationTokenStoreRequest> savedTokens = new ArrayList<>();
    private final List<Long> revokedAccountIds = new ArrayList<>();

    @Override
    public void save(EmailVerificationTokenStoreRequest token) {
      savedTokens.add(token);
    }

    @Override
    public int revokeOpenEmailVerificationTokens(Long accountId, Instant revokedAt) {
      revokedAccountIds.add(accountId);
      return 1;
    }

    @Override
    public Optional<StoredEmailVerificationToken> findByHash(EmailVerificationTokenHash tokenHash) {
      return Optional.empty();
    }

    @Override
    public Optional<StoredEmailVerificationToken> findOpenEmailVerificationToken(Long accountId) {
      return openToken;
    }

    @Override
    public void consume(Long tokenId, Instant consumedAt) {
      throw new AssertionError("이 테스트 fake는 인증 요청 흐름에서 token 소비 호출을 허용하지 않습니다.");
    }
  }

  /** provider 호출 없이 발송 요청을 기록하고, 테스트가 지정한 RuntimeException을 던지는 대역입니다. */
  private static class FakeEmailVerificationNotificationSender
      implements EmailVerificationNotificationSender {

    private final List<EmailVerificationNotificationRequest> requests = new ArrayList<>();
    private RuntimeException failure;

    @Override
    public void send(EmailVerificationNotificationRequest request) {
      if (failure != null) {
        throw failure;
      }
      requests.add(request);
    }
  }

  /** 이메일 인증 요청 감사 이벤트를 기록하고, 완료 감사 이벤트 호출은 실패로 고정하는 대역입니다. */
  private static class FakeAuthEventStorePort implements AuthEventStorePort {

    private final List<AuthEventRecord> events = new ArrayList<>();

    @Override
    public void recordEmailVerificationRequested(Long accountId, boolean successful) {
      events.add(new AuthEventRecord(accountId, successful));
    }

    @Override
    public void recordEmailVerified(Long accountId) {
      throw new AssertionError("이 테스트 fake는 인증 요청 흐름에서 인증 완료 감사 이벤트를 허용하지 않습니다.");
    }

    @Override
    public void recordEmailVerificationConfirmFailure(
        Long accountId, EmailVerificationConfirmFailureReason reason) {
      throw new AssertionError("이 테스트 fake는 인증 요청 흐름에서 인증 완료 실패 감사를 허용하지 않습니다.");
    }

    @Override
    public void recordPasswordResetRequested(Long accountId, boolean successful) {
      throw new AssertionError("이 테스트 fake는 비밀번호 재설정 요청 감사를 허용하지 않습니다.");
    }
  }

  private record AuthEventRecord(Long accountId, boolean successful) {}
}
