package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 루트 `.env.example`의 값 미기입 계약을 검증합니다.
 *
 * <p>이 파일은 로컬 `.env` 작성에 필요한 변수 이름만 공유하고, 비밀값이 아니어도 예시값이나 기본값은 커밋하지 않습니다.
 */
class EnvExampleFileTest {

  private static final Pattern ENV_ASSIGNMENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*=(.*)$");
  private static final List<String> REQUIRED_STAGING_KEYS =
      List.of(
          "STG_DB_RUNTIME_PASSWORD",
          "STG_DB_MIGRATOR_PASSWORD",
          "STG_AUTH_JWT_HMAC_SECRET",
          "STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET",
          "STG_RESEND_API_KEY");

  /** `.env.example`의 모든 환경변수 항목은 `KEY=` 형태로 비어 있어야 합니다. */
  @Test
  void envExampleContainsRequiredKeysWithoutValues() throws IOException {
    List<String> lines = Files.readAllLines(Path.of(".env.example"));

    Map<String, String> assignments =
        lines.stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .filter(line -> !line.startsWith("#"))
            .map(ENV_ASSIGNMENT::matcher)
            .filter(matcher -> matcher.matches())
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    matcher -> matcher.group(0).substring(0, matcher.group(0).indexOf('=')),
                    matcher -> matcher.group(1)));

    List<String> entriesWithValues =
        assignments.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .toList();

    assertThat(entriesWithValues).isEmpty();
    assertThat(assignments).containsKeys(REQUIRED_STAGING_KEYS.toArray(String[]::new));
    assertThat(assignments)
        .doesNotContainKeys("STG_DB_URL", "STG_DB_RUNTIME_USERNAME", "STG_DB_MIGRATOR_USERNAME");
  }
}
