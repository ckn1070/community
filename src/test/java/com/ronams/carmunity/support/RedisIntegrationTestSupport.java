package com.ronams.carmunity.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/** 실제 Redis ACL·TTL·command 동작을 검증하는 통합 테스트 기반입니다. */
public abstract class RedisIntegrationTestSupport extends PostgreSqlIntegrationTestSupport {

  static final String REDIS_IMAGE =
      "redis:8.2.9-alpine3.22@sha256:77dcf738f8eba3ca2c7a26a5a1756e1259a7eca7451234f766eca3bc4a46790e";
  private static final String APPLICATION_USER = "application";
  private static final String APPLICATION_PASSWORD = "redis-integration-password";
  private static final String OPERATION_USER = "operation";
  private static final String OPERATION_PASSWORD = "redis-integration-operation-password";
  private static final String OBSERVER_USER = "integration-observer";
  private static final String OBSERVER_PASSWORD = "redis-integration-observer-password";

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
          .withExposedPorts(6379)
          .withCommand(
              "redis-server",
              "--save",
              "",
              "--appendonly",
              "no",
              "--user",
              "default",
              "off",
              "--user",
              APPLICATION_USER,
              "on",
              ">" + APPLICATION_PASSWORD,
              "~carmunity:catalog:model-detail:v1:*",
              "+get",
              "+set",
              "+del",
              "+ping",
              "--user",
              OPERATION_USER,
              "on",
              ">" + OPERATION_PASSWORD,
              "~carmunity:catalog:model-detail:v1:*",
              "+ttl",
              "+info|commandstats",
              "+info|stats",
              "+ping",
              "--user",
              OBSERVER_USER,
              "on",
              ">" + OBSERVER_PASSWORD,
              "~carmunity:catalog:model-detail:v1:*",
              "+ttl",
              "+exists");

  @DynamicPropertySource
  static void registerRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.username", () -> APPLICATION_USER);
    registry.add("spring.data.redis.password", () -> APPLICATION_PASSWORD);
    registry.add("carmunity.catalog.model-detail-cache.enabled", () -> "true");
  }

  /** Application ACL에 진단 command를 보태지 않고 TTL·존재 여부를 관찰합니다. */
  protected static String observeRedis(String command, String key) throws Exception {
    return REDIS
        .execInContainer(
            "redis-cli",
            "--user",
            OBSERVER_USER,
            "-a",
            OBSERVER_PASSWORD,
            "--no-auth-warning",
            command,
            key)
        .getStdout()
        .trim();
  }

  /** 손상 document 복구 경계를 만들기 위해 application과 같은 ACL로 값을 기록합니다. */
  protected static void setRedisValueAsApplication(String key, String value) throws Exception {
    REDIS.execInContainer(
        "redis-cli",
        "--user",
        APPLICATION_USER,
        "-a",
        APPLICATION_PASSWORD,
        "--no-auth-warning",
        "SET",
        key,
        value);
  }

  /** Production operation ACL과 같은 사용자로 진단 command 허용·거부를 확인합니다. */
  protected static String executeAsOperation(String... command) throws Exception {
    String[] redisCommand = new String[command.length + 6];
    redisCommand[0] = "redis-cli";
    redisCommand[1] = "--user";
    redisCommand[2] = OPERATION_USER;
    redisCommand[3] = "-a";
    redisCommand[4] = OPERATION_PASSWORD;
    redisCommand[5] = "--no-auth-warning";
    System.arraycopy(command, 0, redisCommand, 6, command.length);
    return REDIS.execInContainer(redisCommand).getStdout();
  }
}
