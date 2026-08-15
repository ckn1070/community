package com.ronams.carmunity.auth.internal.application.service;

import com.ronams.carmunity.account.api.AccountAuthStatus;
import com.ronams.carmunity.account.api.PasswordLoginAccount;
import com.ronams.carmunity.account.api.PasswordLoginAccountLookup;
import com.ronams.carmunity.account.api.PasswordLoginFailureReason;
import com.ronams.carmunity.account.api.PasswordLoginResultRecorder;
import com.ronams.carmunity.account.api.PasswordLoginSuccess;
import com.ronams.carmunity.account.api.StoredRefreshToken;
import com.ronams.carmunity.auth.internal.application.AuthErrorCode;
import com.ronams.carmunity.auth.internal.application.command.CreatePasswordSessionCommand;
import com.ronams.carmunity.auth.internal.application.port.in.CreatePasswordSessionUseCase;
import com.ronams.carmunity.auth.internal.application.port.out.AccessTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.port.out.IdentifierHasherPort;
import com.ronams.carmunity.auth.internal.application.port.out.PasswordVerifierPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.result.CreatedSessionResult;
import com.ronams.carmunity.auth.internal.application.result.IssuedAccessToken;
import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 비밀번호 기반 인증 session 생성 application service입니다. */
@RequiredArgsConstructor
@Service
public class CreatePasswordSessionService implements CreatePasswordSessionUseCase {

  private final PasswordLoginAccountLookup accountLookup;
  private final PasswordVerifierPort passwordVerifier;
  private final AccessTokenIssuerPort accessTokenIssuer;
  private final RefreshTokenIssuerPort refreshTokenIssuer;
  private final IdentifierHasherPort identifierHasher;
  private final PasswordLoginResultRecorder resultRecorder;
  private final Clock clock;

  /**
   * password 검증, token 발급, refresh token 저장 요청, 감사 이벤트 기록 요청을 하나의 usecase로 조율합니다.
   *
   * <p>password 원문은 검증 port 입력으로만 사용하고, refresh token 원문은 응답으로만 반환합니다.
   */
  @Override
  public CreatedSessionResult execute(CreatePasswordSessionCommand command) {
    String normalizedEmail = normalizeEmail(command.email());
    String identifierHash = identifierHasher.hash(normalizedEmail);
    PasswordLoginAccount account =
        accountLookup
            .findByNormalizedEmail(normalizedEmail)
            .orElseThrow(() -> unknownCredentials(identifierHash));

    if (!passwordVerifier.matches(command.password(), account.passwordHash())) {
      recordKnownFailure(account, identifierHash, PasswordLoginFailureReason.PASSWORD_MISMATCH);
      throw new ApiException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    SessionAccess sessionAccess = sessionAccessFor(account, identifierHash);
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

  private String normalizeEmail(String rawEmail) {
    String normalized = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST);
    }
    return normalized;
  }

  private ApiException unknownCredentials(String identifierHash) {
    resultRecorder.recordUnknownFailure(identifierHash, clock.instant());
    return new ApiException(AuthErrorCode.INVALID_CREDENTIALS);
  }

  private void recordKnownFailure(
      PasswordLoginAccount account, String identifierHash, PasswordLoginFailureReason reason) {
    resultRecorder.recordKnownFailure(account.publicId(), identifierHash, reason, clock.instant());
  }

  private SessionAccess sessionAccessFor(PasswordLoginAccount account, String identifierHash) {
    if (account.status() == AccountAuthStatus.ACTIVE) {
      return SessionAccess.FULL;
    }
    if (account.status() == AccountAuthStatus.PENDING) {
      return SessionAccess.LIMITED;
    }

    recordKnownFailure(account, identifierHash, loginRestrictedReason(account.status()));
    throw new ApiException(AuthErrorCode.ACCOUNT_LOGIN_RESTRICTED);
  }

  private PasswordLoginFailureReason loginRestrictedReason(AccountAuthStatus status) {
    return switch (status) {
      case SUSPENDED -> PasswordLoginFailureReason.ACCOUNT_SUSPENDED;
      case WITHDRAWN -> PasswordLoginFailureReason.ACCOUNT_WITHDRAWN;
      default -> throw new IllegalStateException("login restricted reason is missing");
    };
  }
}
