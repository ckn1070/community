package com.ronams.carmunity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring Boot와 Spring Modulith가 사용하는 애플리케이션 루트입니다. */
@Modulithic(sharedModules = "shared")
@SpringBootApplication
@EnableScheduling
public class CarmunityApplication {

  /**
   * 로컬 실행과 배포 runtime에서 동일한 Spring Boot bootstrap 경로를 사용합니다.
   *
   * @param args Spring Boot에 전달할 실행 인자입니다.
   */
  public static void main(String[] args) {
    if (java.util.Arrays.asList(args).contains("--command=kafka-events")) {
      SpringApplication application = new SpringApplication(CarmunityApplication.class);
      application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
      application.setLazyInitialization(true);
      // 운영 명령은 HTTP·일반 worker를 시작하지 않습니다. CLI/환경변수로 이 격리를 덮어쓸 수 없습니다.
      application.addInitializers(
          context ->
              context
                  .getEnvironment()
                  .getPropertySources()
                  .addFirst(
                      new org.springframework.core.env.MapPropertySource(
                          "kafka-operation-isolation",
                          java.util.Map.of(
                              "carmunity.kafka.operation-enabled",
                              true,
                              "carmunity.kafka.replay-enabled",
                              java.util.Arrays.asList(args).contains("--approved=true")
                                  && java.util.Arrays.asList(args).contains("--mode=replay"),
                              "carmunity.kafka.relay-enabled",
                              false,
                              "carmunity.kafka.consumer-enabled",
                              false,
                              "carmunity.kafka.cleanup-enabled",
                              false,
                              "carmunity.community.enabled",
                              false,
                              "spring.main.web-application-type",
                              "none"))));
      String[] operationArgs =
          java.util.Arrays.stream(args)
              .filter(argument -> !argument.startsWith("--spring.main.web-application-type="))
              .filter(argument -> !argument.startsWith("--carmunity.kafka.relay-enabled="))
              .filter(argument -> !argument.startsWith("--carmunity.kafka.consumer-enabled="))
              .filter(argument -> !argument.startsWith("--carmunity.kafka.cleanup-enabled="))
              .filter(argument -> !argument.startsWith("--carmunity.community.enabled="))
              .toArray(String[]::new);
      try (var context = application.run(operationArgs)) {
        // ApplicationRunner 종료 뒤 producer·DB pool까지 닫아 일회 실행 process를 종료합니다.
      }
      return;
    }
    SpringApplication.run(CarmunityApplication.class, args);
  }
}
