package com.ronams.carmunity.notification.internal.adapter.out.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.ronams.carmunity.notification.api.EmailNotificationException;
import com.ronams.carmunity.notification.api.EmailNotificationFailureType;
import com.ronams.carmunity.notification.api.EmailVerificationNotificationRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Resend 이메일 인증 adapter가 provider 요청, 설정 검증, bounded retry를 지키는지 검증합니다. */
class ResendEmailVerificationNotificationSenderTest {

  /** 발송 요청은 Resend CreateEmailOptions로 변환되고 token은 frontend 인증 URL query에만 포함됩니다. */
  @Test
  void sendsVerificationEmailThroughResendGateway() {
    FakeResendEmailGateway gateway = new FakeResendEmailGateway();
    ResendEmailVerificationNotificationSender sender = sender(gateway);
    Instant expiresAt = Instant.parse("2026-06-29T00:30:00Z");

    sender.send(
        new EmailVerificationNotificationRequest("member@example.com", "raw token", expiresAt));

    assertThat(gateway.options).hasSize(1);
    CreateEmailOptions options = gateway.options.getFirst();
    assertThat(options.getFrom()).isEqualTo("no-reply@example.com");
    assertThat(options.getTo()).containsExactly("member@example.com");
    assertThat(options.getSubject()).isEqualTo("Carmunity 이메일 인증을 완료해 주세요");
    assertThat(options.getHtml())
        .contains("https://app.example.com/verify-email?token=raw+token")
        .contains("2026-06-29T00:30:00Z");
    assertThat(options.getText()).contains("https://app.example.com/verify-email?token=raw+token");
  }

  /** 5xx 같은 일시 provider 오류는 총 2회까지 즉시 재시도합니다. */
  @Test
  void retriesTemporaryProviderFailureOnce() {
    FakeResendEmailGateway gateway = new FakeResendEmailGateway();
    gateway.failures.add(new ResendException(500, "server error"));
    ResendEmailVerificationNotificationSender sender = sender(gateway);

    sender.send(
        new EmailVerificationNotificationRequest(
            "member@example.com", "raw-token", Instant.parse("2026-06-29T00:30:00Z")));

    assertThat(gateway.options).hasSize(2);
  }

  /** 4xx 영구 실패는 재시도하지 않고 provider 세부사항 없는 분류 예외로 변환합니다. */
  @Test
  void doesNotRetryPermanentProviderFailure() {
    FakeResendEmailGateway gateway = new FakeResendEmailGateway();
    gateway.failures.add(new ResendException(400, "bad request"));
    ResendEmailVerificationNotificationSender sender = sender(gateway);

    assertThatThrownBy(
            () ->
                sender.send(
                    new EmailVerificationNotificationRequest(
                        "member@example.com", "raw-token", Instant.parse("2026-06-29T00:30:00Z"))))
        .isInstanceOfSatisfying(
            EmailNotificationException.class,
            exception ->
                assertThat(exception.failureType())
                    .isEqualTo(EmailNotificationFailureType.PERMANENT_PROVIDER_FAILURE));
    assertThat(gateway.options).hasSize(1);
  }

  /** 필수 발송 설정이 비어 있으면 provider 호출 없이 설정 오류로 닫습니다. */
  @Test
  void rejectsMissingConfigurationWithoutProviderCall() {
    FakeResendEmailGateway gateway = new FakeResendEmailGateway();
    ResendEmailVerificationNotificationSender sender =
        new ResendEmailVerificationNotificationSender(
            EmailNotificationProperties.of("", "", "", "", ""), gateway);

    assertThatThrownBy(
            () ->
                sender.send(
                    new EmailVerificationNotificationRequest(
                        "member@example.com", "raw-token", Instant.parse("2026-06-29T00:30:00Z"))))
        .isInstanceOfSatisfying(
            EmailNotificationException.class,
            exception ->
                assertThat(exception.failureType())
                    .isEqualTo(EmailNotificationFailureType.CONFIGURATION));
    assertThat(gateway.options).isEmpty();
  }

  private ResendEmailVerificationNotificationSender sender(FakeResendEmailGateway gateway) {
    return new ResendEmailVerificationNotificationSender(
        EmailNotificationProperties.of(
            "re_test",
            "no-reply@example.com",
            "https://app.example.com/verify-email",
            "https://app.example.com/admin/invitations",
            "https://app.example.com/reset-password"),
        gateway);
  }

  private static class FakeResendEmailGateway implements ResendEmailGateway {

    private final List<CreateEmailOptions> options = new ArrayList<>();
    private final List<ResendException> failures = new ArrayList<>();

    @Override
    public CreateEmailResponse send(CreateEmailOptions options) throws ResendException {
      this.options.add(options);
      if (!failures.isEmpty()) {
        throw failures.removeFirst();
      }
      return new CreateEmailResponse("email-id");
    }
  }
}
