package com.ronams.carmunity.notification.internal.adapter.out.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.ronams.carmunity.notification.api.AdminInvitationNotificationRequest;
import com.ronams.carmunity.notification.api.EmailNotificationException;
import com.ronams.carmunity.notification.api.EmailNotificationFailureType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Resend 관리자 초대 adapter가 provider 요청, 설정 검증, bounded retry를 지키는지 검증합니다. */
class ResendAdminInvitationNotificationSenderTest {

  private static final UUID INVITATION_ID = UUID.fromString("7c24c7cb-b2d2-4d35-8a3d-49a2b71bdb8a");

  /** 발송 요청은 Resend CreateEmailOptions로 변환되고 raw token은 초대 수락 URL query에만 포함됩니다. */
  @Test
  void sendsAdminInvitationEmailThroughResendGateway() {
    FakeResendEmailGateway gateway = new FakeResendEmailGateway();
    ResendAdminInvitationNotificationSender sender = sender(gateway);
    Instant expiresAt = Instant.parse("2026-07-07T12:00:00Z");

    sender.send(
        new AdminInvitationNotificationRequest(
            "admin@example.com", INVITATION_ID, "raw token", expiresAt));

    assertThat(gateway.options).hasSize(1);
    CreateEmailOptions options = gateway.options.getFirst();
    assertThat(options.getFrom()).isEqualTo("no-reply@example.com");
    assertThat(options.getTo()).containsExactly("admin@example.com");
    assertThat(options.getSubject()).isEqualTo("Carmunity 관리자 초대를 수락해 주세요");
    assertThat(options.getHtml())
        .contains("https://app.example.com/admin/invitations/" + INVITATION_ID + "?token=raw+token")
        .contains("2026-07-07T12:00:00Z");
    assertThat(options.getText())
        .contains(
            "https://app.example.com/admin/invitations/" + INVITATION_ID + "?token=raw+token");
  }

  /** 5xx 같은 일시 provider 오류는 총 2회까지 즉시 재시도합니다. */
  @Test
  void retriesTemporaryProviderFailureOnce() {
    FakeResendEmailGateway gateway = new FakeResendEmailGateway();
    gateway.failures.add(new ResendException(500, "server error"));
    ResendAdminInvitationNotificationSender sender = sender(gateway);

    sender.send(
        new AdminInvitationNotificationRequest(
            "admin@example.com",
            INVITATION_ID,
            "raw-token",
            Instant.parse("2026-07-07T12:00:00Z")));

    assertThat(gateway.options).hasSize(2);
  }

  /** 필수 발송 설정이 비어 있으면 provider 호출 없이 설정 오류로 닫습니다. */
  @Test
  void rejectsMissingConfigurationWithoutProviderCall() {
    FakeResendEmailGateway gateway = new FakeResendEmailGateway();
    ResendAdminInvitationNotificationSender sender =
        new ResendAdminInvitationNotificationSender(
            EmailNotificationProperties.of("", "", "", "", ""), gateway);

    assertThatThrownBy(
            () ->
                sender.send(
                    new AdminInvitationNotificationRequest(
                        "admin@example.com",
                        INVITATION_ID,
                        "raw-token",
                        Instant.parse("2026-07-07T12:00:00Z"))))
        .isInstanceOfSatisfying(
            EmailNotificationException.class,
            exception ->
                assertThat(exception.failureType())
                    .isEqualTo(EmailNotificationFailureType.CONFIGURATION));
    assertThat(gateway.options).isEmpty();
  }

  private ResendAdminInvitationNotificationSender sender(FakeResendEmailGateway gateway) {
    return new ResendAdminInvitationNotificationSender(
        EmailNotificationProperties.of(
            "re_test",
            "no-reply@example.com",
            "https://app.example.com/verify-email",
            "https://app.example.com/admin/invitations/",
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
