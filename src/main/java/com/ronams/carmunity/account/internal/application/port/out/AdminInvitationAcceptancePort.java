package com.ronams.carmunity.account.internal.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** 관리자 초대 수락 조회와 저장을 담당하는 persistence port입니다. */
public interface AdminInvitationAcceptancePort {

  /**
   * 초대 수락 검증에 필요한 snapshot을 조회합니다.
   *
   * @param invitationPublicId path에서 받은 초대 public id입니다.
   * @return 초대가 존재하면 수락 판단에 필요한 snapshot입니다.
   */
  Optional<AdminInvitationForAcceptance> findForAcceptance(UUID invitationPublicId);

  /**
   * 새 account graph 생성, 초대 accepted 상태, 수락 감사 이벤트를 한 transaction으로 저장합니다.
   *
   * @param request 수락 저장 요청입니다.
   */
  void accept(AcceptedAdminAccountStoreRequest request);
}
