package com.ronams.carmunity.support;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PostgreSQL 통합 테스트가 Docker 부재를 skip으로 숨기지 않는 저장소 품질 계약을 검증합니다.
 *
 * <p>이 테스트는 Testcontainers 기반을 상속하지 않으므로 Docker daemon 상태와 무관하게 fail-closed 설정 자체를 검사합니다.
 */
class PostgreSqlIntegrationTestSupportContractTest {

  private static final String SUPPORT_CLASS =
      "com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport";
  private static final String CONTRACT_CLASS =
      "com.ronams.carmunity.support.PostgreSqlIntegrationTestSupportContractTest";

  @Test
  void requiresDockerForPostgreSqlIntegrationTests() {
    var testcontainers = PostgreSqlIntegrationTestSupport.class.getAnnotation(Testcontainers.class);

    assertThat(testcontainers).isNotNull();
    assertThat(testcontainers.disabledWithoutDocker()).isFalse();
  }

  @Test
  void pinsPostgreSqlImageToPatchBaseAndDigest() {
    assertThat(PostgreSqlIntegrationTestSupport.POSTGRES_IMAGE)
        .matches("^postgres:[0-9]+\\.[0-9]+-alpine[0-9]+\\.[0-9]+@sha256:[0-9a-f]{64}$");
    assertThat(PostgreSqlIntegrationTestSupport.POSTGRES.getDockerImageName())
        .isEqualTo(PostgreSqlIntegrationTestSupport.POSTGRES_IMAGE);
  }

  @Test
  void preventsDirectTestcontainersUsageOutsideTheCommonSupport() {
    var testClasses = new ClassFileImporter().importPackages("com.ronams.carmunity");

    noClasses()
        .that()
        .doNotHaveFullyQualifiedName(SUPPORT_CLASS)
        .and()
        .doNotHaveFullyQualifiedName(CONTRACT_CLASS)
        .and()
        .doNotHaveFullyQualifiedName("com.ronams.carmunity.support.KafkaIntegrationTestSupport")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.testcontainers..")
        .check(testClasses);
  }
}
