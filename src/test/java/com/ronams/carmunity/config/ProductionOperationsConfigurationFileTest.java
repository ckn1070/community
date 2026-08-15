package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

/**
 * 운영 환경이 애플리케이션 생존 상태와 DB를 포함한 요청 처리 가능 상태를 구분하도록 설정 계약을 검증합니다.
 *
 * <p>이 계약이 깨지면 DB 장애가 불필요한 process 재시작으로 이어지거나, 준비되지 않은 instance가 트래픽을 받을 수 있습니다.
 */
class ProductionOperationsConfigurationFileTest {

  private final YamlPropertySourceLoader propertySourceLoader = new YamlPropertySourceLoader();

  /** 운영 상태 점검에 사용할 health endpoint를 제공하도록 Actuator가 runtime classpath에 있어야 합니다. */
  @Test
  void actuatorIsAvailableForProductionHealthChecks() {
    assertThat(
            ClassUtils.isPresent(
                "org.springframework.boot.health.actuate.endpoint.HealthEndpoint",
                ProductionOperationsConfigurationFileTest.class.getClassLoader()))
        .isTrue();
  }

  /** liveness는 DB 장애와 분리하고 readiness만 DB 상태를 반영해야 합니다. */
  @Test
  void productionProfileSeparatesLivenessFromDatabaseReadiness() throws IOException {
    PropertySource<?> propertySource = loadProductionConfiguration();

    assertThat(propertySource.getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health");
    assertThat(propertySource.getProperty("management.endpoint.health.probes.enabled"))
        .isEqualTo(true);
    assertThat(propertySource.getProperty("management.endpoint.health.group.liveness.include"))
        .isEqualTo("livenessState");
    assertThat(propertySource.getProperty("management.endpoint.health.group.readiness.include"))
        .isEqualTo("readinessState,db");
  }

  /** 종료 중인 instance를 트래픽에서 안전하게 제외하고 운영 SQL 원문을 출력하지 않아야 합니다. */
  @Test
  void productionProfileEnablesGracefulShutdownAndDisablesSqlOutput() throws IOException {
    PropertySource<?> propertySource = loadProductionConfiguration();

    assertThat(propertySource.getProperty("server.shutdown")).isEqualTo("graceful");
    assertThat(propertySource.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
        .isEqualTo("30s");
    assertThat(propertySource.getProperty("spring.jpa.show-sql")).isEqualTo(false);
    assertThat(propertySource.getProperty("spring.jpa.properties.hibernate.format_sql"))
        .isEqualTo(false);
  }

  /** DB 장애 readiness가 probe timeout 안에 실패하고 운영 API 문서가 public 경로에 노출되지 않아야 합니다. */
  @Test
  void productionProfileFailsDatabaseHealthFastAndDisablesApiDocumentation() throws IOException {
    PropertySource<?> propertySource = loadProductionConfiguration();

    assertThat(propertySource.getProperty("spring.datasource.hikari.connection-timeout"))
        .isEqualTo(1500);
    assertThat(propertySource.getProperty("spring.datasource.hikari.validation-timeout"))
        .isEqualTo(1000);
    assertThat(propertySource.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
    assertThat(propertySource.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
    assertThat(propertySource.getProperty("spring.web.resources.add-mappings")).isEqualTo(false);
  }

  /** 필수 credential은 repository 기본값 없이 환경변수가 누락되면 기동 실패로 드러나야 합니다. */
  @Test
  void requiredCredentialsDoNotHaveRepositoryDefaults() throws IOException {
    PropertySource<?> propertySource = loadSingle("application.yaml");

    assertThat(propertySource.getProperty("spring.datasource.username"))
        .isEqualTo("${DB_USERNAME}");
    assertThat(propertySource.getProperty("spring.datasource.password"))
        .isEqualTo("${DB_PASSWORD}");
    assertThat(propertySource.getProperty("carmunity.auth.jwt.hmac-secret"))
        .isEqualTo("${AUTH_JWT_HMAC_SECRET}");
    assertThat(propertySource.getProperty("carmunity.auth.login.identifier-hash.hmac-secret"))
        .isEqualTo("${AUTH_LOGIN_IDENTIFIER_HMAC_SECRET}");
  }

  private PropertySource<?> loadProductionConfiguration() throws IOException {
    return loadSingle("application-production.yaml");
  }

  private PropertySource<?> loadSingle(String resourceName) throws IOException {
    List<PropertySource<?>> propertySources =
        propertySourceLoader.load(resourceName, new ClassPathResource(resourceName));
    assertThat(propertySources).hasSize(1);
    return propertySources.getFirst();
  }
}
