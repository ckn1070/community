package com.ronams.carmunity.auth.internal.application.service;

import com.ronams.carmunity.account.api.PasswordLoginAccount;
import com.ronams.carmunity.account.api.PasswordLoginResultRecorder;
import com.ronams.carmunity.account.api.PasswordLoginSuccess;
import com.ronams.carmunity.account.api.StoredRefreshToken;
import com.ronams.carmunity.auth.internal.application.command.CreatePasswordSessionCommand;
import com.ronams.carmunity.auth.internal.application.port.in.CreatePasswordSessionUseCase;
import com.ronams.carmunity.auth.internal.application.port.out.AccessTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.result.CreatedSessionResult;
import com.ronams.carmunity.auth.internal.application.result.IssuedAccessToken;
import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 비밀번호 기반 인증 session 생성 application service입니다. */
@RequiredArgsConstructor
@Service
public class CreatePasswordSessionService implements CreatePasswordSessionUseCase {

  private final AuthenticatePasswordService passwordAuthentication;
  private final AccessTokenIssuerPort accessTokenIssuer;
  private final RefreshTokenIssuerPort refreshTokenIssuer;
  private final PasswordLoginResultRecorder resultRecorder;

  /**
   * password 검증, token 발급, refresh token 저장 요청, 감사 이벤트 기록 요청을 하나의 usecase로 조율합니다.
   *
   * <p>password 원문은 검증 port 입력으로만 사용하고, refresh token 원문은 응답으로만 반환합니다.
   */
  @Override
  public CreatedSessionResult execute(CreatePasswordSessionCommand command) {
    AuthenticatedPasswordAccount authenticated =
        passwordAuthentication.authenticate(command.email(), command.password());
    PasswordLoginAccount account = authenticated.account();
    SessionAccess sessionAccess = authenticated.sessionAccess();
    IssuedAccessToken accessToken = accessTokenIssuer.issue(account.toAuthSubject(), sessionAccess);
    IssuedRefreshToken refreshToken = refreshTokenIssuer.issue();
    resultRecorder.recordSuccess(
        new PasswordLoginSuccess(
            account.publicId(),
            new StoredRefreshToken(
                refreshToken.hash(),
                refreshToken.familyId(),
                refreshToken.issuedAt(),
                refreshToken.expiresAt())));

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
