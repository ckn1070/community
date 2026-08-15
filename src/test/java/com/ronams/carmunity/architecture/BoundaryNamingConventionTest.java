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
 * 경계 객체 이름이 호출부에서 같은 의미를 반복하지 않도록 소스 관례를 검증합니다.
 *
 * <p>UseCase와 Port 이름은 새 기능마다 반복해서 생기는 경계 계약이므로 문서 기준뿐 아니라 테스트로도 고정합니다.
 */
class BoundaryNamingConventionTest {

  private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");
  private static final Set<String> VERB_PREFIXES =
      Set.of("Save", "Load", "Check", "Find", "Create", "Update", "Delete", "Register");

  /** UseCase 타입 이름이 작업 의도를 담고, 실행 method 이름은 execute로 통일합니다. */
  @Test
  void useCaseInterfacesExposeExecuteOnly() throws IOException {
    List<Path> useCaseSources = findSources("application/port/in", "UseCase.java");

    List<String> violations =
        useCaseSources.stream()
            .flatMap(BoundaryNamingConventionTest::findNonExecuteUseCaseMethods)
            .toList();

    assertThat(violations).as("UseCase method는 타입명과 동사를 반복하지 않고 execute만 사용해야 합니다.").isEmpty();
  }

  /** Output Port 타입명에는 method 이름과 반복될 가능성이 큰 동사 prefix를 쓰지 않습니다. */
  @Test
  void outputPortsAvoidVerbPrefixes() throws IOException {
    List<Path> portSources = findSources("application/port/out", "Port.java");

    List<String> violations =
        portSources.stream()
            .map(path -> path.getFileName().toString().replace(".java", ""))
            .filter(BoundaryNamingConventionTest::startsWithVerbPrefix)
            .toList();

    assertThat(violations)
        .as("Output Port 이름은 Save/Load/Check 같은 동사 prefix 대신 대상 경계나 능력 이름을 사용해야 합니다.")
        .isEmpty();
  }

  private static Stream<String> findNonExecuteUseCaseMethods(Path source) {
    try {
      String code = Files.readString(source);
      return code.lines()
          .map(String::strip)
          .filter(BoundaryNamingConventionTest::looksLikeUseCaseMethodSignature)
          .filter(methodSignature -> !methodSignature.contains(" execute("))
          .map(methodSignature -> MAIN_SOURCE_ROOT.relativize(source) + ": " + methodSignature);
    } catch (IOException exception) {
      throw new IllegalStateException("UseCase 소스를 읽을 수 없습니다: " + source, exception);
    }
  }

  private static boolean looksLikeUseCaseMethodSignature(String line) {
    return line.endsWith(";")
        && line.contains("(")
        && !line.startsWith("package ")
        && !line.startsWith("import ");
  }

  private static boolean startsWithVerbPrefix(String typeName) {
    return typeName.endsWith("Port") && VERB_PREFIXES.stream().anyMatch(typeName::startsWith);
  }

  private static List<Path> findSources(String packageSuffix, String fileSuffix)
      throws IOException {
    try (Stream<Path> sourceFiles = Files.walk(MAIN_SOURCE_ROOT)) {
      return sourceFiles
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().contains(packageSuffix))
          .filter(path -> path.getFileName().toString().endsWith(fileSuffix))
          .sorted()
          .toList();
    }
  }
}
