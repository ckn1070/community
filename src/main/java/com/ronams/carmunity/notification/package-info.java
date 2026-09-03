/** notification 모듈입니다. 외부 발송과 Community inbox·event receipt, 완료 조회, Kafka 소비 경계를 소유합니다. */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"shared :: pagination", "shared :: error", "shared :: integration"})
package com.ronams.carmunity.notification;
