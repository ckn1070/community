package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 로컬 기본 로그 형식과 운영 structured logging 설정이 파일 단위로 분리되어 있는지 검증합니다.
 *
 * <p>이 테스트가 깨지면 개발 편의용 plain text 로그와 운영용 JSON 로그 기준이 한 파일에서 섞이거나 누락될 수 있습니다.
 */
class LoggingConfigurationFileTest {

  private final YamlPropertySourceLoader propertySourceLoader = new YamlPropertySourceLoader();

  /** 기본 `application.yaml`은 plain text 로그에서 request id가 보이도록 level pattern만 정의해야 합니다. */
  @Test
  void applicationYamlConfiguresRequestIdAwarePlainTextLogging() throws IOException {
    PropertySource<?> propertySource = loadSingle("application.yaml");

    assertThat(propertySource.getProperty("logging.pattern.level"))
        .isEqualTo("requestId:%X{requestId} %5p");
    assertThat(propertySource.containsProperty("logging.structured.format.console")).isFalse();
  }

  /** 운영 profile은 Boot structured logging을 사용하도록 `ecs` console format을 따로 켜야 합니다. */
  @Test
  void applicationProductionYamlEnablesStructuredConsoleLogging() throws IOException {
    PropertySource<?> propertySource = loadSingle("application-production.yaml");

    assertThat(propertySource.getProperty("logging.structured.format.console")).isEqualTo("ecs");
  }

  private PropertySource<?> loadSingle(String resourceName) throws IOException {
    List<PropertySource<?>> propertySources =
        propertySourceLoader.load(resourceName, new ClassPathResource(resourceName));
    assertThat(propertySources).hasSize(1);
    return propertySources.getFirst();
  }
}
