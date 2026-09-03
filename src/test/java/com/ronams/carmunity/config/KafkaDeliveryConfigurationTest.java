package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.mock.env.MockEnvironment;

class KafkaDeliveryConfigurationTest {
  private final KafkaDeliveryConfiguration configuration = new KafkaDeliveryConfiguration();

  @Test
  void rejectsAnEnabledRoleWithoutBootstrapServer() {
    assertThatThrownBy(
            () ->
                configuration.communityKafkaProducerFactory(
                    new KafkaProperties(), new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("bootstrap");
  }

  @Test
  void productionRejectsPlaintextOrMissingRoleCredential() {
    var properties = new KafkaProperties();
    properties.setBootstrapServers(java.util.List.of("kafka:9093"));
    var environment =
        new MockEnvironment()
            .withProperty("spring.profiles.active", "production")
            .withProperty("spring.kafka.bootstrap-servers", "kafka:9093")
            .withProperty("carmunity.kafka.relay.properties.security.protocol", "PLAINTEXT");
    environment.setActiveProfiles("production");
    assertThatThrownBy(() -> configuration.communityKafkaProducerFactory(properties, environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("relay/security.protocol");
  }
}
