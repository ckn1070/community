package com.ronams.carmunity.auth.internal.application.service;

import com.ronams.carmunity.account.api.PasswordLoginAccount;
import com.ronams.carmunity.account.api.PasswordLoginResultRecorder;
import com.ronams.carmunity.auth.internal.application.command.CreatePasswordSessionCommand;
import com.ronams.carmunity.auth.internal.application.port.in.CreatePasswordSessionUseCase;
import com.ronams.carmunity.auth.internal.application.port.out.AccessTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenLifecycleStorePort;
import com.ronams.carmunity.auth.internal.application.result.CreatedSessionResult;
import com.ronams.carmunity.auth.internal.application.result.IssuedAccessToken;
import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import com.ronams.carmunity.securityaudit.api.SecurityAuditAppender;
import com.ronams.carmunity.securityaudit.api.SecurityAuditEventType;
import com.ronams.carmunity.securityaudit.api.SecurityAuditRecord;
import com.ronams.carmunity.securityaudit.api.SecurityAuditResult;
import com.ronams.carmunity.shared.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 기반 인증 session 생성 application service입니다. */
@RequiredArgsConstructor
@Service
public class CreatePasswordSessionService implements CreatePasswordSessionUseCase {

  private final AuthenticatePasswordService passwordAuthentication;
  private final AccessTokenIssuerPort accessTokenIssuer;
  private final RefreshTokenIssuerPort refreshTokenIssuer;
  private final RefreshTokenLifecycleStorePort refreshTokenLifecycleStorePort;
  private final PasswordLoginResultRecorder resultRecorder;
  private final SecurityAuditAppender securityAuditAppender;

  /**
   * password 검증, token 발급, refresh token 저장 요청, 감사 이벤트 기록 요청을 하나의 usecase로 조율합니다.
   *
   * <p>password 원문은 검증 port 입력으로만 사용하고, refresh token 원문은 응답으로만 반환합니다.
   */
  @Override
  @Transactional(noRollbackFor = ApiException.class)
  public CreatedSessionResult execute(CreatePasswordSessionCommand command) {
    AuthenticatedPasswordAccount authenticated =
        passwordAuthentication.authenticate(command.email(), command.password());
    PasswordLoginAccount account = authenticated.account();
    SessionAccess sessionAccess = authenticated.sessionAccess();
    IssuedAccessToken accessToken = accessTokenIssuer.issue(account.toAuthSubject(), sessionAccess);
    IssuedRefreshToken refreshToken = refreshTokenIssuer.issue();
    refreshTokenLifecycleStorePort.issue(account.publicId(), refreshToken);
    resultRecorder.recordSuccess(account.publicId(), refreshToken.issuedAt());
    securityAuditAppender.append(
        SecurityAuditRecord.of(
            account.publicId(),
            SecurityAuditEventType.PASSWORD_LOGIN,
            SecurityAuditResult.SUCCESS,
            null,
            null,
            refreshToken.issuedAt()));

    return new CreatedSessionResult(
        accessToken.value(),
        accessToken.tokenType(),
        accessToken.expiresAt(),
        refreshToken.value(),
        refreshToken.expiresAt(),
        account.publicId(),
        account.roleCodes(),
        sessionAccess);
  }
}
