package com.ronams.carmunity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Staging Migration Job의 단일 Spring Boot process 종료와 database 결과를 검증합니다. */
class StagingMigrationProcessIntegrationTest extends PostgreSqlIntegrationTestSupport {

  private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(120);
  private static final String TEST_HMAC = "staging-process-test-hmac-secret-32-bytes";
  private static final List<String> CATALOG_TABLES =
      List.of(
          "car_brand",
          "car_certification",
          "car_configuration",
          "car_configuration_option",
          "car_configuration_price",
          "car_generation",
          "car_model",
          "car_model_year",
          "car_powertrain",
          "car_powertrain_component",
          "car_vehicle_variant");

  /** Migration과 runtime 권한 callback이 한 process에서 재실행 가능한 상태로 끝나는지 검증합니다. */
  @Test
  @Timeout(300)
  void migrationProcessAppliesSchemaAndRuntimePrivileges() throws Exception {
    createRuntimeRole();

    ProcessResult first = runMigrationProcess(postgresPassword());
    assertThat(first.exitCode()).withFailMessage(first.output()).isZero();
    assertMigrationAndRuntimePrivileges();

    ProcessResult second = runMigrationProcess(postgresPassword());
    assertThat(second.exitCode()).withFailMessage(second.output()).isZero();
    assertMigrationAndRuntimePrivileges();

    ProcessResult rejected = runMigrationProcess("wrong-password");
    assertThat(rejected.exitCode()).isNotZero();
  }

  private static void createRuntimeRole() throws SQLException {
    try (var connection =
            DriverManager.getConnection(postgresJdbcUrl(), postgresUsername(), postgresPassword());
        var statement = connection.createStatement()) {
      statement.execute(
          "DO $$ BEGIN CREATE ROLE carmunity_runtime NOLOGIN; "
              + "EXCEPTION WHEN duplicate_object THEN NULL; END $$");
    }
  }

  private static void assertMigrationAndRuntimePrivileges() throws SQLException {
    assertThat(queryBoolean("SELECT to_regclass('public.flyway_schema_history') IS NOT NULL"))
        .isTrue();
    assertThat(
            queryBoolean(
                "SELECT has_table_privilege('carmunity_runtime', 'public.account', 'SELECT')"))
        .isTrue();
    assertThat(
            queryBoolean(
                "SELECT has_table_privilege('carmunity_runtime', 'public.account', 'INSERT') "
                    + "AND has_table_privilege('carmunity_runtime', 'public.account', 'UPDATE') "
                    + "AND has_table_privilege('carmunity_runtime', 'public.account', 'DELETE')"))
        .isTrue();
    assertThat(
            queryBoolean(
                "SELECT has_sequence_privilege("
                    + "'carmunity_runtime', 'public.account_id_seq', 'USAGE')"))
        .isTrue();
    assertThat(
            queryBoolean(
                "SELECT has_table_privilege('carmunity_runtime', "
                    + "'public.flyway_schema_history', 'SELECT')"))
        .isFalse();

    for (String table : CATALOG_TABLES) {
      assertThat(
              queryBoolean(
                  "SELECT has_table_privilege('carmunity_runtime', 'public."
                      + table
                      + "', 'SELECT')"))
          .as("catalog runtime SELECT privilege: %s", table)
          .isTrue();
      assertThat(
              queryBoolean(
                  "SELECT has_table_privilege('carmunity_runtime', 'public."
                      + table
                      + "', 'INSERT') OR has_table_privilege('carmunity_runtime', 'public."
                      + table
                      + "', 'UPDATE') OR has_table_privilege('carmunity_runtime', 'public."
                      + table
                      + "', 'DELETE') OR has_table_privilege('carmunity_runtime', 'public."
                      + table
                      + "', 'TRUNCATE') OR has_table_privilege('carmunity_runtime', 'public."
                      + table
                      + "', 'REFERENCES') OR has_table_privilege('carmunity_runtime', 'public."
                      + table
                      + "', 'TRIGGER')"))
          .as("catalog runtime mutation privilege: %s", table)
          .isFalse();
      assertThat(
              queryBoolean(
                  "SELECT has_sequence_privilege('carmunity_runtime', 'public."
                      + table
                      + "_id_seq', 'USAGE') OR has_sequence_privilege('carmunity_runtime', 'public."
                      + table
                      + "_id_seq', 'SELECT') OR has_sequence_privilege('carmunity_runtime', 'public."
                      + table
                      + "_id_seq', 'UPDATE')"))
          .as("catalog runtime sequence privilege: %s", table)
          .isFalse();
    }

    assertRuntimeStatementsRespectCatalogReadOnlyBoundary();
  }

  private static void assertRuntimeStatementsRespectCatalogReadOnlyBoundary() throws SQLException {
    try (var connection =
            DriverManager.getConnection(postgresJdbcUrl(), postgresUsername(), postgresPassword());
        var statement = connection.createStatement()) {
      statement.execute("SET ROLE carmunity_runtime");
      for (String table : CATALOG_TABLES) {
        statement.executeQuery("SELECT * FROM public." + table + " LIMIT 0").close();
        assertPermissionDenied(statement, "INSERT INTO public." + table + " DEFAULT VALUES");
        assertPermissionDenied(statement, "UPDATE public." + table + " SET id = id WHERE FALSE");
        assertPermissionDenied(statement, "DELETE FROM public." + table + " WHERE FALSE");
        assertPermissionDenied(statement, "TRUNCATE TABLE public." + table);
        assertPermissionDenied(
            statement, "SELECT nextval('public." + table + "_id_seq'::regclass)");
      }
      statement.execute("RESET ROLE");
    }
  }

  private static void assertPermissionDenied(java.sql.Statement statement, String sql) {
    try {
      statement.execute(sql);
      throw new AssertionError("runtime catalog statement가 거부되지 않았습니다: " + sql);
    } catch (SQLException exception) {
      assertThat(exception.getSQLState()).isEqualTo("42501");
    }
  }

  private static boolean queryBoolean(String sql) throws SQLException {
    try (var connection =
            DriverManager.getConnection(postgresJdbcUrl(), postgresUsername(), postgresPassword());
        var statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      result.next();
      return result.getBoolean(1);
    }
  }

  private static ProcessResult runMigrationProcess(String password)
      throws IOException, InterruptedException {
    Path outputPath = Files.createTempFile("car-community-migration-process-", ".log");
    outputPath.toFile().deleteOnExit();
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(CarmunityApplication.class.getName());
    command.addAll(migrationArguments());

    ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
    processBuilder.redirectOutput(outputPath.toFile());
    Map<String, String> environment = processBuilder.environment();
    environment.put("SPRING_PROFILES_ACTIVE", "production");
    environment.put("DB_URL", postgresJdbcUrl());
    environment.put("DB_USERNAME", postgresUsername());
    environment.put("DB_PASSWORD", password);
    environment.put("AUTH_JWT_HMAC_SECRET", TEST_HMAC);
    environment.put("AUTH_LOGIN_IDENTIFIER_HMAC_SECRET", TEST_HMAC + "-identifier");

    Process process = processBuilder.start();
    boolean finished = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(10, TimeUnit.SECONDS);
    }
    String output = Files.readString(outputPath, StandardCharsets.UTF_8);
    assertThat(finished).withFailMessage(output).isTrue();
    return new ProcessResult(process.exitValue(), output);
  }

  private static List<String> migrationArguments() {
    return List.of(
        "--spring.main.web-application-type=none",
        "--spring.main.lazy-initialization=true",
        "--spring.flyway.enabled=true",
        "--spring.flyway.callback-locations=classpath:db/callback",
        "--spring.jpa.hibernate.ddl-auto=validate");
  }

  private record ProcessResult(int exitCode, String output) {}
}
