package com.ronams.carmunity.auth.internal.application.service;

import com.ronams.carmunity.auth.internal.application.command.LogoutCommand;
import com.ronams.carmunity.auth.internal.application.port.in.LogoutUseCase;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenHashPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenLifecycleStorePort;
import com.ronams.carmunity.auth.internal.domain.RefreshSession;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** refresh token 원문으로 단일 인증 session을 로그아웃 처리하는 application service입니다. */
@RequiredArgsConstructor
@Service
public class LogoutService implements LogoutUseCase {

  private final RefreshTokenHashPort refreshTokenHashPort;
  private final RefreshTokenLifecycleStorePort refreshTokenLifecycleStorePort;
  private final Clock clock;

  /**
   * refresh token 폐기와 감사 이벤트 저장을 하나의 transaction 안에서 수행합니다.
   *
   * <p>row lock은 token 조회부터 LOGOUT 폐기까지 유지되어야 합니다. 이미 만료, 폐기, 교체된 token은 token 상태를 드러내지 않고 저장소
   * mutation 없이 종료합니다.
   */
  @Override
  @Transactional
  public void execute(LogoutCommand command) {
    String tokenHash = refreshTokenHashPort.hash(command.refreshToken());
    RefreshSession currentToken =
        refreshTokenLifecycleStorePort.findByHashForUpdate(tokenHash).orElse(null);
    if (currentToken == null) {
      return;
    }

    Instant now = clock.instant();
    if (currentToken.isExpiredAt(now) || currentToken.isReuseCandidate()) {
      return;
    }

    refreshTokenLifecycleStorePort.revokeForLogout(currentToken, now);
  }
}
