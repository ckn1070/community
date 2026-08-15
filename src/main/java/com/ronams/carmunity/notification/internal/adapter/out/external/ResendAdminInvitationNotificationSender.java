package com.ronams.carmunity.notification.internal.adapter.out.external;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.ronams.carmunity.notification.api.AdminInvitationNotificationRequest;
import com.ronams.carmunity.notification.api.AdminInvitationNotificationSender;
import com.ronams.carmunity.notification.api.EmailNotificationException;
import com.ronams.carmunity.notification.api.EmailNotificationFailureType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resend를 사용해 관리자 초대 메시지를 발송하는 notification adapter입니다. */
@RequiredArgsConstructor
@Component
class ResendAdminInvitationNotificationSender implements AdminInvitationNotificationSender {

  private static final int MAX_ATTEMPTS = 2;

  private final EmailNotificationProperties properties;
  private final ResendEmailGateway resendEmailGateway;
  private final AdminInvitationEmailTemplate template = new AdminInvitationEmailTemplate();

  /**
   * 관리자 초대 이메일을 Resend로 발송합니다.
   *
   * <p>provider 응답 본문, 초대 raw token, 대상 email 원문은 log/exception message로 노출하지 않습니다.
   */
  @Override
  public void send(AdminInvitationNotificationRequest request) {
    if (!properties.adminInvitationConfigured()) {
      throw new EmailNotificationException(EmailNotificationFailureType.CONFIGURATION);
    }

    String invitationUrl = invitationUrl(request);
    CreateEmailOptions options =
        CreateEmailOptions.builder()
            .from(properties.from())
            .to(request.recipientNormalized())
            .subject(template.subject())
            .html(template.html(invitationUrl, request.expiresAt()))
            .text(template.text(invitationUrl, request.expiresAt()))
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

  private String invitationUrl(AdminInvitationNotificationRequest request) {
    String baseUrl = trimTrailingSlashes(properties.adminInvitationBaseUrl());
    return baseUrl
        + "/"
        + request.invitationId()
        + "?token="
        + URLEncoder.encode(request.invitationToken(), StandardCharsets.UTF_8);
  }

  private static String trimTrailingSlashes(String value) {
    int endIndex = value.length();
    while (endIndex > 0 && value.charAt(endIndex - 1) == '/') {
      endIndex--;
    }
    return value.substring(0, endIndex);
  }
}
