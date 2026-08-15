package com.ronams.carmunity.support;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL 동작이 필요한 통합 테스트의 공통 Testcontainers 기반입니다.
 *
 * <p>Flyway migration, PostgreSQL constraint, inet/jsonb 타입처럼 H2로 대체하면 놓칠 수 있는 경계를 실제 PostgreSQL로
 * 검증합니다. Docker를 사용할 수 없으면 이 테스트 계층 전체를 실패시켜 DB 검증이 실행되지 않은 상태를 성공으로 보고하지 않습니다.
 */
@Testcontainers(disabledWithoutDocker = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgreSqlIntegrationTestSupport {

  static final String POSTGRES_IMAGE =
      "postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297";
  static final DockerImageName POSTGRES_DOCKER_IMAGE =
      DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_DOCKER_IMAGE);

  /** Spring Boot datasource와 Flyway가 같은 PostgreSQL container를 사용하도록 동적 속성을 주입합니다. */
  @DynamicPropertySource
  static void registerPostgreSqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    // DB 중심 통합 테스트도 전체 application context에서 Security bean을 로딩하므로 테스트 JWT 설정을 함께 둡니다.
    registry.add("carmunity.auth.jwt.issuer", () -> "carmunity-api-test");
    registry.add("carmunity.auth.jwt.access-token-ttl", () -> "15m");
    registry.add(
        "carmunity.auth.jwt.hmac-secret", () -> "test-hmac-secret-must-be-at-least-32-bytes");
    registry.add(
        "carmunity.auth.login.identifier-hash.hmac-secret",
        () -> "test-identifier-secret-must-be-at-least-32-bytes");
    // 개인 .env의 부분 notification 설정이 repository 통합 테스트 결과를 바꾸지 않게 비활성 상태를 고정합니다.
    registry.add("carmunity.notification.email.resend.api-key", () -> "");
    registry.add("carmunity.notification.email.from", () -> "");
    registry.add("carmunity.notification.email.verification.base-url", () -> "");
    registry.add("carmunity.notification.email.admin-invitation.base-url", () -> "");
    registry.add("carmunity.notification.email.password-reset.base-url", () -> "");
  }

  /** 별도 application process 검증이 같은 PostgreSQL container endpoint를 사용하도록 제공합니다. */
  protected static String postgresJdbcUrl() {
    return POSTGRES.getJdbcUrl();
  }

  /** 별도 application process 검증이 같은 PostgreSQL container 계정을 사용하도록 제공합니다. */
  protected static String postgresUsername() {
    return POSTGRES.getUsername();
  }

  /** 별도 application process 검증이 같은 PostgreSQL container credential을 사용하도록 제공합니다. */
  protected static String postgresPassword() {
    return POSTGRES.getPassword();
  }
}
