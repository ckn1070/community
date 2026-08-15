package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.RegisterMemberAccountCommand;
import com.ronams.carmunity.account.internal.application.port.in.RegisterMemberAccountUseCase;
import com.ronams.carmunity.account.internal.application.port.out.AccountUniquenessPort;
import com.ronams.carmunity.account.internal.application.port.out.AuthRoleLookupPort;
import com.ronams.carmunity.account.internal.application.port.out.MemberAccountStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordHasherPort;
import com.ronams.carmunity.account.internal.application.result.RegisterMemberAccountResult;
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

/** 일반 회원 자체 가입 application service입니다. */
@RequiredArgsConstructor
@Service
public class RegisterMemberAccountService implements RegisterMemberAccountUseCase {

  private final AccountUniquenessPort accountUniquenessPort;
  private final AuthRoleLookupPort authRoleLookupPort;
  private final PasswordHasherPort passwordHasherPort;
  private final MemberAccountStorePort memberAccountStorePort;

  /**
   * 가입 데이터를 하나의 트랜잭션에서 생성합니다.
   *
   * <p>account graph와 성공 감사 이벤트 중 하나라도 실패하면 전체 가입이 rollback되어야 합니다.
   */
  @Override
  @Transactional
  public RegisterMemberAccountResult execute(RegisterMemberAccountCommand command) {
    // domain 값 객체가 정규화와 정책 검증을 소유하고, application은 API 오류 계약으로만 변환합니다.
    NormalizedRegistrationInput input = normalizeAndValidate(command);

    // pre-check는 빠른 충돌 응답을 위한 1차 방어이고, 동시 가입 race condition은 DB unique constraint가 최종 방어합니다.
    if (accountUniquenessPort.existsByNormalizedEmail(input.email().normalized())) {
      throw new ApiException(AccountErrorCode.EMAIL_ALREADY_USED);
    }
    if (accountUniquenessPort.existsByNormalizedDisplayName(input.displayName().normalized())) {
      throw new ApiException(AccountErrorCode.DISPLAY_NAME_ALREADY_USED);
    }

    // ROLE_MEMBER seed 누락은 운영 설정 오류이므로 account graph를 만들기 전에 가입을 중단합니다.
    if (!authRoleLookupPort.existsByCode(RoleCode.ROLE_MEMBER)) {
      throw new ApiException(AccountErrorCode.AUTH_ROLE_NOT_CONFIGURED);
    }

    // 비밀번호 원문은 이 지점에서만 hash 입력으로 쓰고, 이후 저장 경로에는 hash 값 객체만 전달합니다.
    PasswordHash passwordHash = passwordHasherPort.hash(command.password());
    // 자체 가입 생성 규칙은 Account aggregate factory가 소유합니다.
    Account account =
        Account.registerMember(UUID.randomUUID(), input.email(), input.displayName(), passwordHash);
    // persistence adapter는 aggregate와 가입 성공 감사 이벤트를 같은 저장 단위로 매핑합니다.
    memberAccountStorePort.save(account);

    return new RegisterMemberAccountResult(
        account.publicId(),
        account.email().value(),
        account.profile().displayName().value(),
        account.status());
  }

  private NormalizedRegistrationInput normalizeAndValidate(RegisterMemberAccountCommand command) {
    try {
      EmailAddress email = EmailAddress.from(command.email());
      DisplayName displayName = DisplayName.from(command.displayName());
      PasswordPolicy.validate(command.password());
      return new NormalizedRegistrationInput(email, displayName);
    } catch (IllegalArgumentException exception) {
      // domain 값 객체와 정책의 validation 실패는 HTTP 계약에서 공통 INVALID_REQUEST로 축약합니다.
      throw new ApiException(CommonErrorCode.INVALID_REQUEST, exception);
    }
  }

  private record NormalizedRegistrationInput(EmailAddress email, DisplayName displayName) {}
}
