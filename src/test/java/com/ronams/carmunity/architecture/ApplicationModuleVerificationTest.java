package com.ronams.carmunity.architecture;

import com.ronams.carmunity.CarmunityApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Spring Modulith가 인식하는 애플리케이션 모듈 경계를 검증합니다. */
class ApplicationModuleVerificationTest {

  /** 모듈 간 internal 패키지 접근과 의존 방향 위반을 Spring Modulith 규칙으로 막습니다. */
  @Test
  void verifiesApplicationModuleBoundaries() {
    ApplicationModules.of(CarmunityApplication.class).verify();
  }
}
