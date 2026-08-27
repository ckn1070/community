package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/** production과 staging profile의 Swagger 노출 차이를 설정 source에서 검증합니다. */
class StagingProfileConfigurationTest {

  private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

  /** production은 문서를 닫고 staging override는 문서와 UI resource를 명시적으로 엽니다. */
  @Test
  void enablesSwaggerOnlyInStagingOverride() throws IOException {
    var production =
        loader.load("production", new ClassPathResource("application-production.yaml")).getFirst();
    var staging =
        loader.load("staging", new ClassPathResource("application-staging.yaml")).getFirst();

    assertThat(production.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
    assertThat(production.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
    assertThat(production.getProperty("spring.web.resources.add-mappings")).isEqualTo(false);
    assertThat(staging.getProperty("springdoc.api-docs.enabled")).isEqualTo(true);
    assertThat(staging.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(true);
    assertThat(staging.getProperty("spring.web.resources.add-mappings")).isEqualTo(true);
  }
}
