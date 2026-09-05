package com.ronams.carmunity.notification.internal.adapter.out.external;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Notification email namespace가 하나의 검증된 binding 계약으로 조립되는지 확인합니다. */
class EmailNotificationPropertiesBindingTest {

  private static final String SECRET_SENTINEL = "RESEND-SECRET-MUST-NOT-APPEAR-IN-ERROR";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(ResendEmailNotificationConfig.class);

  @Test
  void bindsCompleteNotificationEmailConfiguration() {
    contextRunner
        .withPropertyValues(
            "carmunity.notification.email.resend.api-key=re_test",
            "carmunity.notification.email.from=no-reply@example.com",
            "carmunity.notification.email.verification.base-url=https://example.com",
            "carmunity.notification.email.admin-invitation.base-url=https://example.com",
            "carmunity.notification.email.password-reset.base-url=https://example.com")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(EmailNotificationProperties.class);
              assertThat(EmailNotificationProperties.class)
                  .hasAnnotation(ConfigurationProperties.class);
            });
  }

  /** 일부 값만 설정한 profile은 발송 시점까지 잠복하지 않고 실제 값을 노출하지 않은 채 기동을 막습니다. */
  @Test
  void rejectsPartialConfigurationWithoutSecretDisclosure() {
    contextRunner
        .withPropertyValues("carmunity.notification.email.resend.api-key=" + SECRET_SENTINEL)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasMessageNotContaining(SECRET_SENTINEL)
                  .hasRootCauseMessage("Notification email 설정은 전부 비우거나 다섯 값을 모두 설정해야 합니다.");
            });
  }

  @Test
  void allowsCompletelyDisabledNotificationConfiguration() {
    contextRunner.run(context -> assertThat(context).hasNotFailed());
  }
}
