package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.ChangePasswordCommand;
import com.ronams.carmunity.account.internal.application.port.in.ChangePasswordUseCase;
import com.ronams.carmunity.account.internal.application.port.out.PasswordCredentialStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordHasherPort;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import com.ronams.carmunity.account.internal.domain.PasswordPolicy;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증된 사용자의 비밀번호 변경 application service입니다. */
@RequiredArgsConstructor
@Service
public class ChangePasswordService implements ChangePasswordUseCase {

  private final PasswordCredentialStorePort passwordCredentialStorePort;
  private final PasswordHasherPort passwordHasherPort;
  private final Clock clock;

  /**
   * current password 검증 뒤 새 password hash를 저장하고 기존 refresh token family를 폐기합니다.
   *
   * <p>current password 불일치 감사 이벤트는 실패 응답과 함께 남아야 하므로 {@link ApiException} rollback 대상에서 제외합니다.
   */
  @Override
  @Transactional(noRollbackFor = ApiException.class)
  public void execute(ChangePasswordCommand command) {
    validateNewPassword(command);

    PasswordCredentialStorePort.PasswordCredentialRecord credential =
        passwordCredentialStorePort
            .findByAccountPublicId(command.accountPublicId())
            .orElseThrow(() -> new ApiException(CommonErrorCode.INTERNAL_ERROR));
    if (!passwordHasherPort.matches(command.currentPassword(), credential.passwordHash())) {
      passwordCredentialStorePort.recordPasswordChangeFailure(
          command.accountPublicId(), clock.instant());
      throw new ApiException(AccountErrorCode.CURRENT_PASSWORD_MISMATCH);
    }

    PasswordHash newPasswordHash = passwordHasherPort.hash(command.newPassword());
    passwordCredentialStorePort.changePassword(
        command.accountPublicId(), newPasswordHash, clock.instant());
  }

  /** 새 비밀번호 정책 위반과 확인 불일치는 current password 검증 전에 요청 오류로 처리합니다. */
  private void validateNewPassword(ChangePasswordCommand command) {
    if (!Objects.equals(command.newPassword(), command.newPasswordConfirmation())) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST);
    }
    try {
      PasswordPolicy.validate(command.newPassword());
    } catch (IllegalArgumentException exception) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST, exception);
    }
  }
}
