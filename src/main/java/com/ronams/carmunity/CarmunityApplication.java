package com.ronams.carmunity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/** Spring Boot와 Spring Modulith가 사용하는 애플리케이션 루트입니다. */
@Modulithic(sharedModules = "shared")
@SpringBootApplication
public class CarmunityApplication {

  /**
   * 로컬 실행과 배포 runtime에서 동일한 Spring Boot bootstrap 경로를 사용합니다.
   *
   * @param args Spring Boot에 전달할 실행 인자입니다.
   */
  public static void main(String[] args) {
    SpringApplication.run(CarmunityApplication.class, args);
  }
}
