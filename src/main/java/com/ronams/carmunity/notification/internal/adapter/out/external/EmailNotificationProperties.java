package com.ronams.carmunity.notification.internal.adapter.out.external;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Resend credential, 발신자와 기능별 frontend URL을 하나로 묶은 notification 설정 계약입니다. */
@ConfigurationProperties(prefix = "carmunity.notification.email")
record EmailNotificationProperties(
    Resend resend, String from, Link verification, Link adminInvitation, Link passwordReset) {

  private static final String PARTIAL_CONFIGURATION_MESSAGE =
      "Notification email 설정은 전부 비우거나 다섯 값을 모두 설정해야 합니다.";

  EmailNotificationProperties {
    resend = Objects.requireNonNullElseGet(resend, () -> new Resend(""));
    from = Objects.requireNonNullElse(from, "");
    verification = Objects.requireNonNullElseGet(verification, () -> new Link(""));
    adminInvitation = Objects.requireNonNullElseGet(adminInvitation, () -> new Link(""));
    passwordReset = Objects.requireNonNullElseGet(passwordReset, () -> new Link(""));

    List<String> values =
        List.of(
            resend.apiKey(),
            from,
            verification.baseUrl(),
            adminInvitation.baseUrl(),
            passwordReset.baseUrl());
    boolean anyConfigured = values.stream().anyMatch(value -> !value.isBlank());
    boolean allConfigured = values.stream().allMatch(value -> !value.isBlank());
    if (anyConfigured && !allConfigured) {
      throw new IllegalArgumentException(PARTIAL_CONFIGURATION_MESSAGE);
    }
    if (allConfigured) {
      requireHttpUrl(verification.baseUrl());
      requireHttpUrl(adminInvitation.baseUrl());
      requireHttpUrl(passwordReset.baseUrl());
      if (!from.contains("@")) {
        throw new IllegalArgumentException("Notification 발신자는 email 형식이어야 합니다.");
      }
    }
  }

  static EmailNotificationProperties of(
      String apiKey,
      String from,
      String verificationBaseUrl,
      String adminInvitationBaseUrl,
      String passwordResetBaseUrl) {
    return new EmailNotificationProperties(
        new Resend(apiKey),
        from,
        new Link(verificationBaseUrl),
        new Link(adminInvitationBaseUrl),
        new Link(passwordResetBaseUrl));
  }

  boolean emailVerificationConfigured() {
    return configured();
  }

  boolean passwordResetConfigured() {
    return configured();
  }

  boolean adminInvitationConfigured() {
    return configured();
  }

  String resendApiKey() {
    return resend.apiKey();
  }

  String verificationBaseUrl() {
    return verification.baseUrl();
  }

  String adminInvitationBaseUrl() {
    return adminInvitation.baseUrl();
  }

  String passwordResetBaseUrl() {
    return passwordReset.baseUrl();
  }

  private boolean configured() {
    return !resend.apiKey().isBlank();
  }

  private static void requireHttpUrl(String value) {
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Notification base URL은 http(s) URL이어야 합니다.", exception);
    }
    if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
      throw new IllegalArgumentException("Notification base URL은 http(s) URL이어야 합니다.");
    }
  }

  record Resend(String apiKey) {
    Resend {
      apiKey = Objects.requireNonNullElse(apiKey, "");
    }
  }

  record Link(String baseUrl) {
    Link {
      baseUrl = Objects.requireNonNullElse(baseUrl, "");
    }
  }
}
