/**
 * account 모듈이 다른 모듈에 제공하는 공개 계약입니다.
 *
 * <p>다른 모듈은 account 내부 JPA entity나 internal package가 아니라 이 named interface를 통해서만 account token
 * subject 정보를 사용합니다.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ronams.carmunity.account.api;
