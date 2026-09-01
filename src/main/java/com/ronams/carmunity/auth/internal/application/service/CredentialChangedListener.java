package com.ronams.carmunity.auth.internal.application.service;

import com.ronams.carmunity.account.api.CredentialChanged;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenLifecycleStorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** account 자격 증명 변경 사실을 같은 transaction에서 auth session 폐기로 반영합니다. */
@RequiredArgsConstructor
@Component
class CredentialChangedListener {

  private final RefreshTokenLifecycleStorePort refreshTokenLifecycleStorePort;

  @EventListener
  void on(CredentialChanged event) {
    refreshTokenLifecycleStorePort.revokeActiveSessionsForCredentialChange(
        event.accountPublicId(), event.changedAt());
  }
}
