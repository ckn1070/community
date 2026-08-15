package com.ronams.carmunity.notification.internal.adapter.out.external;

import com.resend.Resend;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Resend 기반 notification adapter 조립 설정입니다. */
@Configuration
@EnableConfigurationProperties(EmailNotificationProperties.class)
class ResendEmailNotificationConfig {

  @Bean
  ResendEmailGateway resendEmailGateway(EmailNotificationProperties properties) {
    return new ResendEmailGatewayAdapter(new Resend(properties.resendApiKey()));
  }
}
