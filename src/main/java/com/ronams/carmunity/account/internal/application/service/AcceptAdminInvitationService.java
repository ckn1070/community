package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.AcceptAdminInvitationCommand;
import com.ronams.carmunity.account.internal.application.port.in.AcceptAdminInvitationUseCase;
import com.ronams.carmunity.account.internal.application.port.out.AcceptedAdminAccountStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.AccountUniquenessPort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationAcceptancePort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationForAcceptance;
import com.ronams.carmunity.account.internal.application.port.out.PasswordHasherPort;
import com.ronams.carmunity.account.internal.application.result.AcceptedAdminInvitationResult;
import com.ronams.carmunity.account.internal.domain.Account;
import com.ronams.carmunity.account.internal.domain.AdminInvitation;
import com.ronams.carmunity.account.internal.domain.ChallengeState;
import com.ronams.carmunity.account.internal.domain.DisplayName;
import com.ronams.carmunity.account.internal.domain.EmailAddress;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import com.ronams.carmunity.account.internal.domain.PasswordPolicy;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** public 관리자 초대 수락 application service입니다. */
@Service
public class AcceptAdminInvitationService implements AcceptAdminInvitationUseCase {

  private final AccountUniquenessPort accountUniquenessPort;
  private final PasswordHasherPort passwordHasherPort;
  private final AdminInvitationTokenHasher tokenHasher;
  private final AdminInvitationAcceptancePort acceptancePort;
  private final Supplier<UUID> accountPublicIdGenerator;
  private final Clock clock;

  /** 운영에서는 config Clock과 UUID generator를 조합합니다. */
  @Autowired
  public AcceptAdminInvitationService(
      AccountUniquenessPort accountUniquenessPort,
      PasswordHasherPort passwordHasherPort,
      AdminInvitationTokenHasher tokenHasher,
      AdminInvitationAcceptancePort acceptancePort,
      Clock clock) {
    this(
        accountUniquenessPort,
        passwordHasherPort,
        tokenHasher,
        acceptancePort,
        UUID::randomUUID,
        clock);
  }

  /** 테스트가 account public id와 현재 시각을 통제할 수 있도록 의존성을 외부에서 주입받습니다. */
  public AcceptAdminInvitationService(
      AccountUniquenessPort accountUniquenessPort,
      PasswordHasherPort passwordHasherPort,
      AdminInvitationTokenHasher tokenHasher,
      AdminInvitationAcceptancePort acceptancePort,
      Supplier<UUID> accountPublicIdGenerator,
      Clock clock) {
    this.accountUniquenessPort = accountUniquenessPort;
    this.passwordHasherPort = passwordHasherPort;
    this.tokenHasher = tokenHasher;
    this.acceptancePort = acceptancePort;
    this.accountPublicIdGenerator = accountPublicIdGenerator;
    this.clock = clock;
  }

  /**
   * invitation id와 raw token을 모두 검증한 뒤 새 관리자 account graph를 저장합니다.
   *
   * <p>기존 account 변경이나 비밀번호 재설정은 이 흐름에 포함하지 않습니다.
   */
  @Override
  @Transactional
  public AcceptedAdminInvitationResult execute(AcceptAdminInvitationCommand command) {
    // passwordConfirm은 인증 수단이 아니라 새 비밀번호 최초 설정 입력의 일치 여부만 서버에서 확인합니다.
    if (!Objects.equals(command.password(), command.passwordConfirm())) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST);
    }

    AdminInvitationForAcceptance invitation =
        acceptancePort
            .findForAcceptance(command.invitationId())
            .orElseThrow(() -> new ApiException(AccountErrorCode.ADMIN_INVITATION_NOT_FOUND));
    Instant now = clock.instant();

    validateInvitation(invitation, command.token(), now);

    NormalizedAcceptanceInput input =
        normalizeAndValidate(invitation.email(), command.displayName(), command.password());
    if (accountUniquenessPort.existsByNormalizedEmail(input.email().normalized())) {
      throw new ApiException(AccountErrorCode.EMAIL_ALREADY_USED);
    }
    if (accountUniquenessPort.existsByNormalizedDisplayName(input.displayName().normalized())) {
      throw new ApiException(AccountErrorCode.DISPLAY_NAME_ALREADY_USED);
    }

    PasswordHash passwordHash = passwordHasherPort.hash(command.password());
    Account account =
        Account.acceptAdminInvitation(
            accountPublicIdGenerator.get(),
            input.email(),
            input.displayName(),
            passwordHash,
            invitation.roleCode(),
            now);

    acceptancePort.accept(
        new AcceptedAdminAccountStoreRequest(
            invitation.invitationId(),
            invitation.publicId(),
            invitation.invitedByAccountId(),
            invitation.roleId(),
            invitation.roleCode(),
            account,
            now));

    return new AcceptedAdminInvitationResult(
        account.publicId(), account.email().value(), invitation.roleCode());
  }

  private void validateInvitation(
      AdminInvitationForAcceptance invitation, String rawToken, Instant now) {
    ChallengeState state =
        new AdminInvitation(
                invitation.publicId(),
                invitation.expiresAt(),
                invitation.closed() ? now : null,
                null)
            .stateAt(now);
    if (state == ChallengeState.CONSUMED || state == ChallengeState.REVOKED) {
      throw new ApiException(AccountErrorCode.ADMIN_INVITATION_ALREADY_CLOSED);
    }
    if (!tokenMatches(invitation.tokenHash(), rawToken)) {
      throw new ApiException(AccountErrorCode.ADMIN_INVITATION_TOKEN_INVALID);
    }
    if (state == ChallengeState.EXPIRED) {
      throw new ApiException(AccountErrorCode.ADMIN_INVITATION_TOKEN_EXPIRED);
    }
    new AdminInvitation(invitation.publicId(), invitation.expiresAt(), null, null).acceptAt(now);
  }

  private boolean tokenMatches(String expectedHash, String rawToken) {
    try {
      String actualHash = tokenHasher.hash(rawToken);
      return MessageDigest.isEqual(
          expectedHash.getBytes(StandardCharsets.UTF_8),
          actualHash.getBytes(StandardCharsets.UTF_8));
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private NormalizedAcceptanceInput normalizeAndValidate(
      String email, String displayName, String password) {
    try {
      EmailAddress normalizedEmail = EmailAddress.from(email);
      DisplayName normalizedDisplayName = DisplayName.from(displayName);
      PasswordPolicy.validate(password);
      return new NormalizedAcceptanceInput(normalizedEmail, normalizedDisplayName);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST, exception);
    }
  }

  private record NormalizedAcceptanceInput(EmailAddress email, DisplayName displayName) {}
}
