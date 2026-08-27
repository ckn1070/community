package com.ronams.carmunity.auth.internal.application.service;

import com.ronams.carmunity.account.api.AccountAuthStatus;
import com.ronams.carmunity.account.api.PasswordLoginAccount;
import com.ronams.carmunity.account.api.PasswordLoginAccountLookup;
import com.ronams.carmunity.account.api.PasswordLoginFailureReason;
import com.ronams.carmunity.account.api.PasswordLoginResultRecorder;
import com.ronams.carmunity.auth.internal.application.AuthErrorCode;
import com.ronams.carmunity.auth.internal.application.port.out.IdentifierHasherPort;
import com.ronams.carmunity.auth.internal.application.port.out.PasswordVerifierPort;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** API token 로그인과 browser Form Login이 공유하는 비밀번호 인증 정책입니다. */
@RequiredArgsConstructor
@Service
public class AuthenticatePasswordService {

  private final PasswordLoginAccountLookup accountLookup;
  private final PasswordVerifierPort passwordVerifier;
  private final IdentifierHasherPort identifierHasher;
  private final PasswordLoginResultRecorder resultRecorder;
  private final Clock clock;

  /** email과 password를 검증하고 token 발급 전의 account 인증 결과를 반환합니다. */
  public AuthenticatedPasswordAccount authenticate(String rawEmail, String rawPassword) {
    String normalizedEmail = normalizeEmail(rawEmail);
    String identifierHash = identifierHasher.hash(normalizedEmail);
    PasswordLoginAccount account =
        accountLookup
            .findByNormalizedEmail(normalizedEmail)
            .orElseThrow(() -> unknownCredentials(identifierHash));

    if (!passwordVerifier.matches(rawPassword, account.passwordHash())) {
      recordKnownFailure(account, identifierHash, PasswordLoginFailureReason.PASSWORD_MISMATCH);
      throw new ApiException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    return new AuthenticatedPasswordAccount(account, sessionAccessFor(account, identifierHash));
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
