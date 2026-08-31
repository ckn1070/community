/** account 모듈입니다. 일반 회원과 관리자를 모두 포괄하는 인증 주체의 생명주기를 소유합니다. */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"shared :: error", "notification :: api", "securityaudit :: api"})
package com.ronams.carmunity.account;
