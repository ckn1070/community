package com.ronams.carmunity.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 모든 업무 모듈이 공유하는 UTC 시간 기준을 중립 composition root에서 조립합니다. */
@Configuration
class TimeConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
