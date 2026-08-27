package com.ronams.carmunity.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 인증이 끝난 API 요청의 account public id를 공통 완료 로그까지 전달합니다. */
@Component
final class ApiRequestActorContextInterceptor implements HandlerInterceptor, WebMvcConfigurer {

  private static final String API_PATH_PATTERN = "/api/**";

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this).addPathPatterns(API_PATH_PATTERN);
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Principal principal = request.getUserPrincipal();
    if (principal != null) {
      ApiRequestLogContext.bindActor(request, principal.getName());
    }
    return true;
  }
}
