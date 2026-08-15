package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.BootstrapSuperAdminAccountCommand;
import com.ronams.carmunity.account.internal.application.port.in.BootstrapSuperAdminAccountUseCase;
import com.ronams.carmunity.account.internal.application.port.out.AuthRoleLookupPort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordHasherPort;
import com.ronams.carmunity.account.internal.application.port.out.SuperAdminBootstrapStorePort;
import com.ronams.carmunity.account.internal.application.result.BootstrapSuperAdminAccountResult;
import com.ronams.carmunity.account.internal.domain.Account;
import com.ronams.carmunity.account.internal.domain.DisplayName;
import com.ronams.carmunity.account.internal.domain.EmailAddress;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import com.ronams.carmunity.account.internal.domain.PasswordPolicy;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최초 최고 관리자 bootstrap application service입니다. */
@RequiredArgsConstructor
@Service
public class BootstrapSuperAdminAccountService implements BootstrapSuperAdminAccountUseCase {

  private final AuthRoleLookupPort authRoleLookupPort;
  private final PasswordHasherPort passwordHasherPort;
  private final SuperAdminBootstrapStorePort superAdminBootstrapStorePort;

  /**
   * 최초 최고 관리자 account graph와 bootstrap 감사 이벤트를 하나의 저장 단위로 생성합니다.
   *
   * <p>활성 최고 관리자가 이미 있으면 비밀번호 원문을 hash로도 넘기지 않고 no-op 결과를 반환합니다.
   */
  @Override
  @Transactional
  public BootstrapSuperAdminAccountResult execute(BootstrapSuperAdminAccountCommand command) {
    NormalizedBootstrapInput input = normalizeAndValidate(command);

    if (!authRoleLookupPort.existsByCode(RoleCode.ROLE_SUPER_ADMIN)) {
      throw new ApiException(AccountErrorCode.AUTH_ROLE_NOT_CONFIGURED);
    }

    if (superAdminBootstrapStorePort.activeSuperAdminExists()) {
      return BootstrapSuperAdminAccountResult.alreadyBootstrapped();
    }

    PasswordHash passwordHash = passwordHasherPort.hash(command.password());
    Account account =
        Account.bootstrapSuperAdmin(
            UUID.randomUUID(), input.email(), input.displayName(), passwordHash);

    return superAdminBootstrapStorePort
        .saveIfNoActiveSuperAdmin(account)
        .map(
            storedAccount ->
                BootstrapSuperAdminAccountResult.created(
                    storedAccount.publicId(),
                    storedAccount.email().value(),
                    storedAccount.profile().displayName().value()))
        .orElseGet(BootstrapSuperAdminAccountResult::alreadyBootstrapped);
  }

  /** command 입력을 domain 값 객체로 정규화하고, 초기 비밀번호 정책 위반은 command 입력 오류로 축약합니다. */
  private NormalizedBootstrapInput normalizeAndValidate(BootstrapSuperAdminAccountCommand command) {
    try {
      EmailAddress email = EmailAddress.from(command.email());
      DisplayName displayName = DisplayName.from(command.displayName());
      PasswordPolicy.validate(command.password());
      return new NormalizedBootstrapInput(email, displayName);
    } catch (IllegalArgumentException exception) {
      // domain validation 실패는 운영 command adapter에서 INVALID_INPUT exit code로 매핑됩니다.
      throw new ApiException(CommonErrorCode.INVALID_REQUEST, exception);
    }
  }

  private record NormalizedBootstrapInput(EmailAddress email, DisplayName displayName) {}
}
