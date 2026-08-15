package com.ronams.carmunity.auth.internal.application.port.out;

/** 로그인 실패 감사 이벤트에 남길 비가역 식별자 hash를 생성하는 port입니다. */
public interface IdentifierHasherPort {

  /**
   * 정규화된 식별자를 감사 추적용 hash로 변환합니다.
   *
   * @param normalizedIdentifier 정규화된 email 같은 로그인 식별자입니다.
   * @return 64자 lowercase hex hash입니다.
   */
  String hash(String normalizedIdentifier);
}
