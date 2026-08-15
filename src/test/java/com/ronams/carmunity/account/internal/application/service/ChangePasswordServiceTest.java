package com.ronams.carmunity.account.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ChangePasswordCommand;
import com.ronams.carmunity.account.internal.application.port.out.PasswordCredentialStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordHasherPort;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 비밀번호 변경 application service의 current password 검증과 저장 요청을 검증합니다. */
class ChangePasswordServiceTest {

  private static final UUID ACCOUNT_PUBLIC_ID =
      UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

  private FakePasswordCredentialStorePort passwordCredentialStorePort;
  private FakePasswordHasherPort passwordHasherPort;
  private ChangePasswordService service;

  @BeforeEach
  void setUp() {
    passwordCredentialStorePort = new FakePasswordCredentialStorePort();
    passwordHasherPort = new FakePasswordHasherPort();
    service =
        new ChangePasswordService(
            passwordCredentialStorePort, passwordHasherPort, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** current password가 맞으면 새 hash 저장, 강제 변경 해제, refresh family 폐기, 성공 감사 이벤트를 요청합니다. */
  @Test
  void changesPasswordAndRevokesRefreshTokenFamily() {
    service.execute(
        command(
            "correct horse battery staple",
            "new correct horse battery staple",
            "new correct horse battery staple"));

    assertThat(passwordHasherPort.lastRawPassword).isEqualTo("new correct horse battery staple");
    assertThat(passwordCredentialStorePort.changedAccountPublicId).isEqualTo(ACCOUNT_PUBLIC_ID);
    assertThat(passwordCredentialStorePort.changedPasswordHash.value())
        .isEqualTo("{argon2}new-password-hash");
    assertThat(passwordCredentialStorePort.changedAt).isEqualTo(NOW);
    assertThat(passwordCredentialStorePort.failureAccountPublicId).isNull();
  }

  /** current password가 틀리면 실패 감사 이벤트만 남기고 password hash는 갱신하지 않습니다. */
  @Test
  void rejectsCurrentPasswordMismatchAndRecordsFailureEvent() {
    assertThatThrownBy(
            () ->
                service.execute(
                    command(
                        "wrong current password",
                        "new correct horse battery staple",
                        "new correct horse battery staple")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(AccountErrorCode.CURRENT_PASSWORD_MISMATCH));

    assertThat(passwordCredentialStorePort.failureAccountPublicId).isEqualTo(ACCOUNT_PUBLIC_ID);
    assertThat(passwordCredentialStorePort.failureAt).isEqualTo(NOW);
    assertThat(passwordCredentialStorePort.changedAccountPublicId).isNull();
  }

  /** 새 비밀번호 확인이 다르면 저장소 조회나 hash 생성 전 INVALID_REQUEST로 닫습니다. */
  @Test
  void rejectsNewPasswordConfirmationMismatchBeforeStoreLookup() {
    assertThatThrownBy(
            () ->
                service.execute(
                    command(
                        "correct horse battery staple",
                        "new correct horse battery staple",
                        "different correct horse battery staple")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(passwordCredentialStorePort.lookupCalled).isFalse();
    assertThat(passwordHasherPort.lastRawPassword).isNull();
  }

  /** 새 비밀번호 정책 위반도 저장소 조회 전 INVALID_REQUEST로 닫습니다. */
  @Test
  void rejectsInvalidNewPasswordBeforeStoreLookup() {
    assertThatThrownBy(
            () -> service.execute(command("correct horse battery staple", "short", "short")))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));

    assertThat(passwordCredentialStorePort.lookupCalled).isFalse();
    assertThat(passwordHasherPort.lastRawPassword).isNull();
  }

  private ChangePasswordCommand command(
      String currentPassword, String newPassword, String newPasswordConfirmation) {
    return new ChangePasswordCommand(
        ACCOUNT_PUBLIC_ID, currentPassword, newPassword, newPasswordConfirmation);
  }

  /** password credential 조회, 갱신, 실패 이벤트 기록을 관찰하는 저장 port 대역입니다. */
  private static class FakePasswordCredentialStorePort implements PasswordCredentialStorePort {

    private boolean lookupCalled;
    private UUID changedAccountPublicId;
    private PasswordHash changedPasswordHash;
    private Instant changedAt;
    private UUID failureAccountPublicId;
    private Instant failureAt;

    @Override
    public Optional<PasswordCredentialRecord> findByAccountPublicId(UUID accountPublicId) {
      lookupCalled = true;
      return Optional.of(new PasswordCredentialRecord("{argon2}stored-password-hash", true));
    }

    @Override
    public void changePassword(UUID accountPublicId, PasswordHash passwordHash, Instant changedAt) {
      changedAccountPublicId = accountPublicId;
      changedPasswordHash = passwordHash;
      this.changedAt = changedAt;
    }

    @Override
    public void recordPasswordChangeFailure(UUID accountPublicId, Instant occurredAt) {
      failureAccountPublicId = accountPublicId;
      failureAt = occurredAt;
    }
  }

  /** current password 검증과 새 password hash 생성을 분리해 관찰합니다. */
  private static class FakePasswordHasherPort implements PasswordHasherPort {

    private String lastRawPassword;

    @Override
    public PasswordHash hash(String rawPassword) {
      lastRawPassword = rawPassword;
      return PasswordHash.from("{argon2}new-password-hash");
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
      return "correct horse battery staple".equals(rawPassword)
          && "{argon2}stored-password-hash".equals(passwordHash);
    }
  }
}
