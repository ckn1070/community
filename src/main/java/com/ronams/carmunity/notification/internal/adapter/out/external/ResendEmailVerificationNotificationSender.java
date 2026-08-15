package com.ronams.carmunity.notification.internal.adapter.out.external;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.ronams.carmunity.notification.api.EmailNotificationException;
import com.ronams.carmunity.notification.api.EmailNotificationFailureType;
import com.ronams.carmunity.notification.api.EmailVerificationNotificationRequest;
import com.ronams.carmunity.notification.api.EmailVerificationNotificationSender;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resend를 사용해 이메일 인증 메시지를 발송하는 notification adapter입니다. */
@RequiredArgsConstructor
@Component
class ResendEmailVerificationNotificationSender implements EmailVerificationNotificationSender {

  private static final int MAX_ATTEMPTS = 2;

  private final EmailNotificationProperties properties;
  private final ResendEmailGateway resendEmailGateway;
  private final VerificationEmailTemplate template = new VerificationEmailTemplate();

  /**
   * 인증 이메일을 Resend로 발송합니다.
   *
   * <p>provider 일시 오류는 즉시 1회 재시도하고, provider 응답 본문이나 raw token은 log/exception message로 노출하지 않습니다.
   */
  @Override
  public void send(EmailVerificationNotificationRequest request) {
    if (!properties.emailVerificationConfigured()) {
      throw new EmailNotificationException(EmailNotificationFailureType.CONFIGURATION);
    }

    String verificationUrl = verificationUrl(request.verificationToken());
    CreateEmailOptions options =
        CreateEmailOptions.builder()
            .from(properties.from())
            .to(request.recipientNormalized())
            .subject(template.subject())
            .html(template.html(verificationUrl, request.expiresAt()))
            .text(template.text(verificationUrl, request.expiresAt()))
            .build();

    sendWithRetry(options);
  }

  private void sendWithRetry(CreateEmailOptions options) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        resendEmailGateway.send(options);
        return;
      } catch (ResendException exception) {
        if (attempt < MAX_ATTEMPTS && retryable(exception)) {
          continue;
        }
        throw new EmailNotificationException(failureType(exception), exception);
      }
    }
  }

  private boolean retryable(ResendException exception) {
    Integer statusCode = exception.getStatusCode();
    return statusCode == null || statusCode == 408 || statusCode == 429 || statusCode >= 500;
  }

  private EmailNotificationFailureType failureType(ResendException exception) {
    if (retryable(exception)) {
      return EmailNotificationFailureType.TEMPORARY_PROVIDER_FAILURE;
    }
    return EmailNotificationFailureType.PERMANENT_PROVIDER_FAILURE;
  }

  private String verificationUrl(String rawToken) {
    String separator = properties.verificationBaseUrl().contains("?") ? "&" : "?";
    return properties.verificationBaseUrl()
        + separator
        + "token="
        + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
  }
}
