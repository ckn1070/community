package com.ronams.carmunity.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * domain 패키지에 경계 전달 모델이 섞이지 않도록 소스 관례를 검증합니다.
 *
 * <p>DDD 역할 오분류는 컴파일 오류로 드러나지 않으므로, Type 명세와 DTO성 이름을 테스트로 고정합니다.
 */
class DomainModelConventionTest {

  private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");
  private static final Set<String> ALLOWED_TYPE_MARKERS =
      Set.of(
          "Aggregate Root",
          "Domain Entity",
          "Value Object",
          "Domain Policy",
          "Domain Service",
          "Domain Event",
          "Domain Exception",
          "Domain Enum");
  private static final Set<String> BOUNDARY_MODEL_SUFFIXES =
      Set.of(
          "Request",
          "Response",
          "Command",
          "Query",
          "Result",
          "Payload",
          "Input",
          "Output",
          "Projection",
          "Dto",
          "DTO",
          "JpaEntity",
          "Repository",
          "Port");
  private static final Set<String> PERSISTENCE_PAYLOAD_MARKERS =
      Set.of("Registered", "Saved", "Persisted", "Stored");
  private static final Set<String> PERSISTENCE_PAYLOAD_TARGET_SUFFIXES =
      Set.of("Account", "Member", "User");

  /** 모든 domain 공개 타입은 Javadoc에서 Type을 명시해 역할을 숨기지 않습니다. */
  @Test
  void domainTypesDeclareAllowedType() throws IOException {
    List<String> violations =
        findDomainSources().stream()
            .filter(source -> !declaresAllowedType(source))
            .map(DomainModelConventionTest::relativeToMainSource)
            .toList();

    assertThat(violations)
        .as("domain 타입은 Aggregate/Entity/Value Object/Event 같은 허용 Type을 Javadoc에 명시해야 합니다.")
        .isEmpty();
  }

  /** DTO, port model, persistence payload로 읽히는 이름은 domain 패키지에 둘 수 없습니다. */
  @Test
  void domainTypesDoNotUseBoundaryModelNames() throws IOException {
    List<String> violations =
        findDomainSources().stream()
            .map(DomainModelConventionTest::typeNameOf)
            .filter(DomainModelConventionTest::looksLikeBoundaryModel)
            .toList();

    assertThat(violations)
        .as("domain 타입 이름은 request/command/result/payload/port/persistence entity 성격을 드러내면 안 됩니다.")
        .isEmpty();
  }

  private static boolean declaresAllowedType(Path source) {
    try {
      String code = Files.readString(source);
      return code.contains("Type:") && ALLOWED_TYPE_MARKERS.stream().anyMatch(code::contains);
    } catch (IOException exception) {
      throw new IllegalStateException("domain 소스를 읽을 수 없습니다: " + source, exception);
    }
  }

  private static boolean looksLikeBoundaryModel(String typeName) {
    return BOUNDARY_MODEL_SUFFIXES.stream().anyMatch(typeName::endsWith)
        || looksLikeCompletedPersistencePayload(typeName);
  }

  private static boolean looksLikeCompletedPersistencePayload(String typeName) {
    return !typeName.endsWith("Event")
        && PERSISTENCE_PAYLOAD_MARKERS.stream().anyMatch(typeName::contains)
        && PERSISTENCE_PAYLOAD_TARGET_SUFFIXES.stream().anyMatch(typeName::endsWith);
  }

  private static String typeNameOf(Path source) {
    return source.getFileName().toString().replace(".java", "");
  }

  private static String relativeToMainSource(Path source) {
    return MAIN_SOURCE_ROOT.relativize(source).toString();
  }

  private static List<Path> findDomainSources() throws IOException {
    try (Stream<Path> sourceFiles = Files.walk(MAIN_SOURCE_ROOT)) {
      return sourceFiles
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .filter(path -> !path.getFileName().toString().equals("package-info.java"))
          .filter(DomainModelConventionTest::isInternalDomainSource)
          .sorted()
          .toList();
    }
  }

  private static boolean isInternalDomainSource(Path source) {
    String normalized = source.toString().replace('\\', '/');
    return normalized.contains("/internal/domain/");
  }
}
