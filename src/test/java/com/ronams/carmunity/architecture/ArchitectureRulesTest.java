package com.ronams.carmunity.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;

/**
 * 모듈 경계와 계층 의존 방향을 자동 검증합니다.
 *
 * <p>이 테스트는 컴파일 성공만으로 드러나지 않는 Clean Architecture 경계 위반을 막기 위한 안전망입니다.
 */
@AnalyzeClasses(packages = "com.ronams.carmunity", importOptions = DoNotIncludeTests.class)
class ArchitectureRulesTest {

  private static final String ROOT_PACKAGE = "com.ronams.carmunity";

  @ArchTest
  // 모듈 간 협력은 공개 API로만 허용하고, 다른 모듈의 internal 구현을 직접 참조하지 못하게 합니다.
  static final ArchRule modules_must_not_access_other_modules_internal =
      classes()
          .that()
          .resideInAPackage(ROOT_PACKAGE + "..")
          .should(notAccessAnotherModuleInternalPackage());

  @ArchTest
  // shared는 공통 계약 소유자이므로 업무 애플리케이션 모듈로 의존 방향이 역전되면 안 됩니다.
  static final ArchRule shared_must_not_depend_on_application_modules =
      classes()
          .that()
          .resideInAPackage(ROOT_PACKAGE + ".shared..")
          .should(notDependOnApplicationModulePackages());

  @ArchTest
  // domain은 순수 업무 규칙을 유지하고 application, adapter, Spring/JPA/Servlet API를 직접 알지 않습니다.
  static final ArchRule domain_must_not_depend_on_application_adapter_or_spring =
      noClasses()
          .that()
          .resideInAPackage("..internal.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..internal.application..",
              "..internal.adapter..",
              "org.springframework..",
              "org.slf4j..",
              "ch.qos.logback..",
              "jakarta.persistence..",
              "jakarta.servlet..")
          .allowEmptyShould(true);

  @ArchTest
  // ErrorCode는 HTTP API 계약이므로 domain 내부 exception/model이 직접 의존하지 못하게 합니다.
  static final ArchRule domain_must_not_depend_on_http_api_error_contract =
      noClasses()
          .that()
          .resideInAPackage("..internal.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ROOT_PACKAGE + ".shared.error..")
          .allowEmptyShould(true);

  @ArchTest
  // application은 use case와 port를 소유하고, adapter 구현체나 Web/JPA 세부 API에 의존하지 않습니다.
  static final ArchRule application_must_not_depend_on_adapters_or_web_persistence_apis =
      noClasses()
          .that()
          .resideInAPackage("..internal.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..internal.adapter..",
              "org.springframework.web..",
              "org.springframework.data.jpa..",
              "jakarta.persistence..",
              "jakarta.servlet..")
          .allowEmptyShould(true);

  @ArchTest
  // persistence adapter 타입은 DB 접근 구현 세부사항이므로 다른 계층으로 새어 나가면 안 됩니다.
  static final ArchRule persistence_adapter_must_not_leak_to_other_layers =
      noClasses()
          .that()
          .resideOutsideOfPackage("..internal.adapter.out.persistence..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..internal.adapter.out.persistence..");

  private static ArchCondition<JavaClass> notAccessAnotherModuleInternalPackage() {
    return new ArchCondition<>("다른 애플리케이션 모듈의 internal 패키지에 의존하지 않는다") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        Optional<String> originModule = moduleNameOf(item.getPackageName());

        for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
          JavaClass targetClass = dependency.getTargetClass();
          Optional<String> targetModule = moduleNameOf(targetClass.getPackageName());

          if (targetModule.isEmpty() || !isInternalPackage(targetClass.getPackageName())) {
            continue;
          }

          if (originModule.isEmpty() || !originModule.get().equals(targetModule.get())) {
            String message = "%s depends on %s".formatted(item.getName(), targetClass.getName());
            events.add(SimpleConditionEvent.violated(item, message));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notDependOnApplicationModulePackages() {
    return new ArchCondition<>("업무 애플리케이션 모듈에 의존하지 않는다") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
          JavaClass targetClass = dependency.getTargetClass();
          Optional<String> targetModule = moduleNameOf(targetClass.getPackageName());

          if (targetModule.isEmpty() || targetModule.get().equals("shared")) {
            continue;
          }

          String message = "%s depends on %s".formatted(item.getName(), targetClass.getName());
          events.add(SimpleConditionEvent.violated(item, message));
        }
      }
    };
  }

  private static Optional<String> moduleNameOf(String packageName) {
    if (!packageName.startsWith(ROOT_PACKAGE + ".")) {
      return Optional.empty();
    }

    String moduleAndSubPackage = packageName.substring((ROOT_PACKAGE + ".").length());
    int separator = moduleAndSubPackage.indexOf('.');
    if (separator < 0) {
      return Optional.of(moduleAndSubPackage);
    }
    return Optional.of(moduleAndSubPackage.substring(0, separator));
  }

  private static boolean isInternalPackage(String packageName) {
    return packageName.matches("^%s\\.[^.]+\\.internal(\\..*)?$".formatted(ROOT_PACKAGE));
  }
}
