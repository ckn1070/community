package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.application.command.RevokeAdminInvitationCommand;
import com.ronams.carmunity.account.internal.application.port.in.RevokeAdminInvitationUseCase;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationRevocationPort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationRevokeRequest;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최고 관리자가 open 관리자 초대를 회수하는 application service입니다. */
@Service
public class RevokeAdminInvitationService implements RevokeAdminInvitationUseCase {

  private final AdminInvitationRevocationPort revocationPort;
  private final Clock clock;

  /** 운영 기본 시간 기준으로 Spring bean을 구성합니다. */
  @Autowired
  public RevokeAdminInvitationService(AdminInvitationRevocationPort revocationPort) {
    this(revocationPort, Clock.systemUTC());
  }

  /** 테스트가 회수 시각을 통제할 수 있도록 Clock을 외부에서 주입받습니다. */
  RevokeAdminInvitationService(AdminInvitationRevocationPort revocationPort, Clock clock) {
    this.revocationPort = revocationPort;
    this.clock = clock;
  }

  /** open 초대 회수 요청을 persistence port로 전달합니다. */
  @Override
  @Transactional
  public void execute(RevokeAdminInvitationCommand command) {
    revocationPort.revoke(
        new AdminInvitationRevokeRequest(
            command.revokerPublicId(), command.invitationId(), clock.instant()));
  }
}
