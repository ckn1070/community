/**
 * 다른 모듈이 notification 내부 구현을 모르고 사용할 수 있는 발송 요청 공개 계약입니다.
 *
 * <p>provider SDK, template 조립, retry, 오류 변환은 notification internal에 숨깁니다.
 */
@org.springframework.modulith.NamedInterface
package com.ronams.carmunity.notification.api;
