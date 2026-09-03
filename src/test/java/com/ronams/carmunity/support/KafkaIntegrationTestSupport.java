package com.ronams.carmunity.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL와 실제 Kafka의 transaction·offset 경계를 검증하며 Docker 부재를 허용하지 않습니다. */
public abstract class KafkaIntegrationTestSupport extends PostgreSqlIntegrationTestSupport {
  @Container
  static KafkaContainer KAFKA =
      new KafkaContainer(
              DockerImageName.parse(
                      "apache/kafka:4.2.1@sha256:9916d60eca5d599550e2c320230808fda342124ba550bb4ac4ea8591803262a0")
                  .asCompatibleSubstituteFor("apache/kafka"))
          .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  /** Broker process를 실제 중단했다 재개하여 log·group offset 보존 후 client 회복을 검증합니다. */
  protected static void pauseKafka() {
    KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
  }

  protected static void resumeKafka() {
    KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
  }

  protected static String kafkaBootstrapServers() {
    return KAFKA.getBootstrapServers();
  }
}
