package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ConfirmPasswordResetCommand;
import com.ronams.carmunity.account.internal.application.port.in.ConfirmPasswordResetUseCase;
import com.ronams.carmunity.account.internal.application.port.out.PasswordHasherPort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetCredentialStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetTokenStorePort;
import com.ronams.carmunity.account.internal.application.port.out.StoredPasswordResetToken;
import com.ronams.carmunity.account.internal.domain.ChallengeState;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import com.ronams.carmunity.account.internal.domain.PasswordPolicy;
import com.ronams.carmunity.account.internal.domain.PasswordResetChallenge;
import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 재설정 완료 application service입니다. */
@Service
public class ConfirmPasswordResetService implements ConfirmPasswordResetUseCase {

  private final PasswordResetTokenHasher tokenHasher;
  private final PasswordResetTokenStorePort tokenStorePort;
  private final PasswordResetCredentialStorePort credentialStorePort;
  private final PasswordHasherPort passwordHasherPort;
  private final Clock clock;

  /** 테스트가 token 만료 판단 시각을 통제할 수 있도록 Clock을 외부에서 주입받습니다. */
  public ConfirmPasswordResetService(
      PasswordResetTokenHasher tokenHasher,
      PasswordResetTokenStorePort tokenStorePort,
      PasswordResetCredentialStorePort credentialStorePort,
      PasswordHasherPort passwordHasherPort,
      Clock clock) {
    this.tokenHasher = tokenHasher;
    this.tokenStorePort = tokenStorePort;
    this.credentialStorePort = credentialStorePort;
    this.passwordHasherPort = passwordHasherPort;
    this.clock = clock;
  }

  /**
   * reset token 소비, password hash 갱신, refresh token 폐기, 감사 이벤트를 하나의 transaction에서 처리합니다.
   *
   * <p>raw token은 즉시 hash로 바꾸고, 이미 소비/폐기/만료된 token은 password credential을 바꾸지 않습니다.
   */
  @Override
  @Transactional
  public void execute(ConfirmPasswordResetCommand command) {
    validateNewPassword(command);

    PasswordResetTokenHash tokenHash = hash(command.token());
    StoredPasswordResetToken token =
        tokenStorePort
            .findByHash(tokenHash)
            .orElseThrow(() -> new ApiException(AccountErrorCode.PASSWORD_RESET_TOKEN_INVALID));

    Instant now = clock.instant();
    PasswordResetChallenge challenge = validateToken(token, now);

    PasswordHash newPasswordHash = passwordHasherPort.hash(command.newPassword());
    challenge.consumeAt(now);
    tokenStorePort.consume(challenge.id(), now);
    credentialStorePort.resetPassword(token.accountId(), newPasswordHash, now);
  }

  private void validateNewPassword(ConfirmPasswordResetCommand command) {
    if (!Objects.equals(command.newPassword(), command.newPasswordConfirmation())) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST);
    }
    try {
      PasswordPolicy.validate(command.newPassword());
    } catch (IllegalArgumentException exception) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST, exception);
    }
  }

  private PasswordResetTokenHash hash(String rawToken) {
    try {
      return tokenHasher.hash(rawToken);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST, exception);
    }
  }

  private PasswordResetChallenge validateToken(StoredPasswordResetToken token, Instant now) {
    PasswordResetChallenge challenge =
        new PasswordResetChallenge(
            token.id(),
            token.accountId(),
            token.expiresAt(),
            token.lastSentAt(),
            token.consumedAt().orElse(null),
            token.revokedAt().orElse(null));
    ChallengeState state = challenge.stateAt(now);
    if (state == ChallengeState.CONSUMED || state == ChallengeState.REVOKED) {
      throw new ApiException(AccountErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }
    if (state == ChallengeState.EXPIRED) {
      throw new ApiException(AccountErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);
    }
    return challenge;
  }
}
