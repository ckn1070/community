/**
 * 전역 Spring MVC, Security, OpenAPI, request id/MDC, 공통 예외 로그 같은 애플리케이션 조립 설정을 소유합니다.
 *
 * <p>업무 모듈은 이 패키지에 의존하지 않고, 이 패키지가 공개 오류 계약을 HTTP 응답으로 변환합니다.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "shared :: error")
package com.ronams.carmunity.config;
