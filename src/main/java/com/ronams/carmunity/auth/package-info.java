/** 인증 session, token 발급/검증, refresh token lifecycle 기반을 소유하는 auth 모듈입니다. */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"account :: api", "shared :: error", "securityaudit :: api"})
package com.ronams.carmunity.auth;
