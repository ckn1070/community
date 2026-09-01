package com.ronams.carmunity.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DDD Context별 물리 저장소 소유권이 source 수준에서도 하나로 유지되는지 검증합니다. */
class DddOwnershipSourceTest {

  private static final Path PRODUCTION_SOURCE = Path.of("src/main/java/com/ronams/carmunity");

  @Test
  void securityAudit만_authEvent를_매핑한다() throws IOException {
    assertThat(javaSourcesContaining("@Table(name = \"auth_event\")"))
        .allMatch(path -> path.toString().contains("/securityaudit/"));
  }

  @Test
  void auth만_refreshToken을_매핑한다() throws IOException {
    assertThat(javaSourcesContaining("@Table(name = \"refresh_token\")"))
        .allMatch(path -> path.toString().contains("/auth/"));
  }

  @Test
  void auth는_account테이블을_직접_조회하지_않는다() throws IOException {
    Path authSource = PRODUCTION_SOURCE.resolve("auth");

    assertThat(javaSourcesContaining(authSource, "from account")).isEmpty();
  }

  @Test
  void config만_운영_Clock을_생성한다() throws IOException {
    assertThat(javaSourcesContaining("Clock.systemUTC()"))
        .allMatch(path -> path.toString().contains("/config/"));
  }

  private static List<Path> javaSourcesContaining(String fragment) throws IOException {
    return javaSourcesContaining(PRODUCTION_SOURCE, fragment);
  }

  private static List<Path> javaSourcesContaining(Path root, String fragment) throws IOException {
    try (var paths = Files.walk(root)) {
      return paths
          .filter(path -> path.toString().endsWith(".java"))
          .filter(path -> contains(path, fragment))
          .toList();
    }
  }

  private static boolean contains(Path path, String fragment) {
    try {
      return Files.readString(path).contains(fragment);
    } catch (IOException exception) {
      throw new IllegalStateException("source를 읽지 못했습니다: " + path, exception);
    }
  }
}
