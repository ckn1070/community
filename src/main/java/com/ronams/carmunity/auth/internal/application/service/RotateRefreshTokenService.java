package com.ronams.carmunity.auth.internal.application.service;

import com.ronams.carmunity.account.api.AccountAuthStatus;
import com.ronams.carmunity.account.api.AccountAuthSubject;
import com.ronams.carmunity.account.api.AccountAuthSubjectLookup;
import com.ronams.carmunity.auth.internal.application.AuthErrorCode;
import com.ronams.carmunity.auth.internal.application.command.RotateRefreshTokenCommand;
import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRecord;
import com.ronams.carmunity.auth.internal.application.model.RefreshTokenRejectionReason;
import com.ronams.carmunity.auth.internal.application.port.in.RotateRefreshTokenUseCase;
import com.ronams.carmunity.auth.internal.application.port.out.AccessTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenHashPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenIssuerPort;
import com.ronams.carmunity.auth.internal.application.port.out.RefreshTokenLifecycleStorePort;
import com.ronams.carmunity.auth.internal.application.result.IssuedAccessToken;
import com.ronams.carmunity.auth.internal.application.result.IssuedRefreshToken;
import com.ronams.carmunity.auth.internal.application.result.RotatedRefreshTokenResult;
import com.ronams.carmunity.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** refresh token 원문으로 access token과 refresh token을 rotation하는 application service입니다. */
@RequiredArgsConstructor
@Service
public class RotateRefreshTokenService implements RotateRefreshTokenUseCase {

  private final RefreshTokenHashPort refreshTokenHashPort;
  private final RefreshTokenLifecycleStorePort refreshTokenLifecycleStorePort;
  private final AccountAuthSubjectLookup accountAuthSubjectLookup;
  private final AccessTokenIssuerPort accessTokenIssuerPort;
  private final RefreshTokenIssuerPort refreshTokenIssuerPort;
  private final Clock clock;

  /**
   * refresh token rotation을 하나의 transaction 안에서 수행합니다.
   *
   * <p>token row lock, 기존 token 폐기, 새 token 저장, 감사 이벤트 기록은 함께 성공하거나 rollback되어야 합니다. 다만 reuse
   * detection과 account 상태 거부로 남긴 family 폐기 감사 기록은 401 응답을 위해 {@link ApiException}을 던져도 rollback하지
   * 않습니다.
   */
  @Override
  @Transactional(noRollbackFor = ApiException.class)
  public RotatedRefreshTokenResult execute(RotateRefreshTokenCommand command) {
    String tokenHash = refreshTokenHashPort.hash(command.refreshToken());
    RefreshTokenRecord currentToken =
        refreshTokenLifecycleStorePort
            .findByHashForUpdate(tokenHash)
            .orElseThrow(this::invalidRefreshToken);
    Instant now = clock.instant();

    if (currentToken.isExpiredAt(now)) {
      throw invalidRefreshToken();
    }
    if (currentToken.isReuseCandidate()) {
      refreshTokenLifecycleStorePort.revokeFamilyForReuse(currentToken, now);
      throw invalidRefreshToken();
    }

    AccountAuthSubject subject =
        accountAuthSubjectLookup
            .findByPublicId(currentToken.accountPublicId())
            .orElseThrow(this::invalidRefreshToken);
    if (!canIssueAccessToken(subject.status())) {
      refreshTokenLifecycleStorePort.revokeFamilyForRejectedAccount(
          currentToken, rejectionReason(subject.status()), now);
      throw invalidRefreshToken();
    }
    // 강제 비밀번호 변경 대상은 기존 refresh token으로 새 access token을 받을 수 없습니다.
    if (subject.passwordChangeRequired()) {
      throw invalidRefreshToken();
    }

    IssuedAccessToken accessToken =
        accessTokenIssuerPort.issue(subject, sessionAccessFor(subject.status()));
    IssuedRefreshToken refreshToken = refreshTokenIssuerPort.issue();
    refreshTokenLifecycleStorePort.rotate(currentToken, refreshToken, now);

    return new RotatedRefreshTokenResult(
        accessToken.value(),
        accessToken.tokenType(),
        accessToken.expiresAt(),
        refreshToken.value(),
        refreshToken.expiresAt(),
        subject.publicId(),
        subject.roleCodes());
  }

  /** refresh token rotation은 ACTIVE/PENDING account에만 새 access token을 발급합니다. */
  private boolean canIssueAccessToken(AccountAuthStatus status) {
    return status == AccountAuthStatus.ACTIVE || status == AccountAuthStatus.PENDING;
  }

  private RefreshTokenRejectionReason rejectionReason(AccountAuthStatus status) {
    return switch (status) {
      case SUSPENDED -> RefreshTokenRejectionReason.ACCOUNT_SUSPENDED;
      case WITHDRAWN -> RefreshTokenRejectionReason.ACCOUNT_WITHDRAWN;
      default -> throw new IllegalStateException("refresh rejection reason is missing");
    };
  }

  /** refresh 재발급은 ACTIVE/PENDING만 통과하므로 그 외 상태는 이 method에 도달하지 않습니다. */
  private SessionAccess sessionAccessFor(AccountAuthStatus status) {
    if (status == AccountAuthStatus.ACTIVE) {
      return SessionAccess.FULL;
    }
    return SessionAccess.LIMITED;
  }

  /** refresh token 실패 원인은 외부에 구체화하지 않고 동일한 인증 실패로 축약합니다. */
  private ApiException invalidRefreshToken() {
    return new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN);
  }
}
