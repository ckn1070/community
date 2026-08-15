package com.ronams.carmunity.account.internal.application.port.out;

/** 관리자 초대 raw token을 생성하는 outbound port입니다. */
public interface AdminInvitationTokenGeneratorPort {

  /**
   * 이메일 링크와 수락 request에 사용할 raw token을 생성합니다.
   *
   * @return 저장하지 않고 hash해서 비교할 bearer token입니다.
   */
  String generate();
}
