package com.ronams.carmunity.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * JPA 저장 모델의 기본 생성자 작성 관례를 소스 수준에서 검증합니다.
 *
 * <p>Lombok 생성자 annotation은 bytecode에 남지 않아 ArchUnit으로 직접 생성자와 구분할 수 없습니다.
 */
class JpaEntityConstructorConventionTest {

  private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");
  private static final String REQUIRED_NO_ARGS_CONSTRUCTOR_ANNOTATION =
      "@NoArgsConstructor(access = AccessLevel.PROTECTED)";

  /** JPA 복원용 기본 생성자는 entity마다 직접 빈 생성자를 반복하지 않고 Lombok annotation으로만 표현합니다. */
  @Test
  void jpaEntitiesUseLombokProtectedNoArgsConstructor() throws IOException {
    List<Path> entitySources = findJpaEntitySources();
    assertThat(entitySources).as("검증할 JPA 엔티티 소스가 있어야 합니다.").isNotEmpty();

    List<String> violations = new ArrayList<>();
    for (Path entitySource : entitySources) {
      String source = Files.readString(entitySource);
      String entityName = entitySource.getFileName().toString().replace(".java", "");
      if (!usesLombokProtectedNoArgsConstructor(source)
          || declaresDirectNoArgsConstructor(source, entityName)) {
        violations.add(MAIN_SOURCE_ROOT.relativize(entitySource).toString());
      }
    }

    assertThat(violations)
        .as("*JpaEntity는 직접 protected 기본 생성자를 쓰지 않고 Lombok 보호 기본 생성자를 사용해야 합니다.")
        .isEmpty();
  }

  private static List<Path> findJpaEntitySources() throws IOException {
    try (Stream<Path> sourceFiles = Files.walk(MAIN_SOURCE_ROOT)) {
      return sourceFiles
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith("JpaEntity.java"))
          .sorted()
          .toList();
    }
  }

  private static boolean usesLombokProtectedNoArgsConstructor(String source) {
    return source.contains("import lombok.AccessLevel;")
        && source.contains("import lombok.NoArgsConstructor;")
        && source.contains(REQUIRED_NO_ARGS_CONSTRUCTOR_ANNOTATION);
  }

  private static boolean declaresDirectNoArgsConstructor(String source, String entityName) {
    return source
        .lines()
        .map(String::strip)
        .anyMatch(line -> isNoArgsConstructor(line, entityName));
  }

  private static boolean isNoArgsConstructor(String line, String entityName) {
    return line.equals(entityName + "() {}")
        || line.equals("public " + entityName + "() {}")
        || line.equals("protected " + entityName + "() {}")
        || line.equals("private " + entityName + "() {}");
  }
}
