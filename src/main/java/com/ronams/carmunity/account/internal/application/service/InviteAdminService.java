package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.InviteAdminCommand;
import com.ronams.carmunity.account.internal.application.port.in.InviteAdminUseCase;
import com.ronams.carmunity.account.internal.application.port.out.AccountUniquenessPort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationRecord;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationSaveRequest;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationStorePort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationTokenGeneratorPort;
import com.ronams.carmunity.account.internal.application.port.out.AuthRoleLookupPort;
import com.ronams.carmunity.account.internal.application.result.AdminInvitationResult;
import com.ronams.carmunity.account.internal.domain.EmailAddress;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import com.ronams.carmunity.notification.api.AdminInvitationNotificationRequest;
import com.ronams.carmunity.notification.api.AdminInvitationNotificationSender;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 최고 관리자가 새 관리자 초대를 생성하는 application service입니다. */
@Service
public class InviteAdminService implements InviteAdminUseCase {

  private static final Logger log = LoggerFactory.getLogger(InviteAdminService.class);
  private static final Duration INVITATION_TTL = Duration.ofHours(24);

  private final AccountUniquenessPort accountUniquenessPort;
  private final AuthRoleLookupPort authRoleLookupPort;
  private final AdminInvitationTokenGeneratorPort tokenGeneratorPort;
  private final AdminInvitationTokenHasher tokenHasher;
  private final AdminInvitationStorePort invitationStorePort;
  private final AdminInvitationNotificationSender notificationSender;
  private final Clock clock;

  /** 테스트가 초대 발급과 만료 시각을 통제할 수 있도록 Clock을 외부에서 주입받습니다. */
  public InviteAdminService(
      AccountUniquenessPort accountUniquenessPort,
      AuthRoleLookupPort authRoleLookupPort,
      AdminInvitationTokenGeneratorPort tokenGeneratorPort,
      AdminInvitationTokenHasher tokenHasher,
      AdminInvitationStorePort invitationStorePort,
      AdminInvitationNotificationSender notificationSender,
      Clock clock) {
    this.accountUniquenessPort = accountUniquenessPort;
    this.authRoleLookupPort = authRoleLookupPort;
    this.tokenGeneratorPort = tokenGeneratorPort;
    this.tokenHasher = tokenHasher;
    this.invitationStorePort = invitationStorePort;
    this.notificationSender = notificationSender;
    this.clock = clock;
  }

  /** 관리자 초대와 생성 감사를 저장한 뒤 transaction commit 이후 초대 이메일을 발송합니다. */
  @Override
  @Transactional
  public AdminInvitationResult execute(InviteAdminCommand command) {
    NormalizedInvitationInput input = normalizeAndValidate(command);

    if (accountUniquenessPort.existsByNormalizedEmail(input.email().normalized())) {
      throw new ApiException(AccountErrorCode.EMAIL_ALREADY_USED);
    }
    if (!authRoleLookupPort.existsByCode(RoleCode.ROLE_ADMIN)) {
      throw new ApiException(AccountErrorCode.AUTH_ROLE_NOT_CONFIGURED);
    }

    String rawToken = tokenGeneratorPort.generate();
    String tokenHash = tokenHasher.hash(rawToken);
    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plus(INVITATION_TTL);

    AdminInvitationRecord invitation =
        invitationStorePort.save(
            new AdminInvitationSaveRequest(
                command.inviterPublicId(),
                input.email(),
                input.roleCode(),
                tokenHash,
                issuedAt,
                expiresAt));

    runAfterCommit(() -> sendInvitation(invitation, rawToken));

    return new AdminInvitationResult(
        invitation.publicId(), invitation.email(), invitation.roleCode(), invitation.expiresAt());
  }

  private NormalizedInvitationInput normalizeAndValidate(InviteAdminCommand command) {
    try {
      EmailAddress email = EmailAddress.from(command.email());
      RoleCode roleCode = RoleCode.valueOf(command.roleCode());
      if (roleCode != RoleCode.ROLE_ADMIN) {
        throw new ApiException(AccountErrorCode.ADMIN_INVITATION_ROLE_NOT_ALLOWED);
      }
      return new NormalizedInvitationInput(email, roleCode);
    } catch (ApiException exception) {
      throw exception;
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST, exception);
    }
  }

  private void runAfterCommit(Runnable task) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      task.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            task.run();
          }
        });
  }

  private void sendInvitation(AdminInvitationRecord invitation, String rawToken) {
    try {
      notificationSender.send(
          new AdminInvitationNotificationRequest(
              invitation.email(), invitation.publicId(), rawToken, invitation.expiresAt()));
    } catch (RuntimeException exception) {
      log.warn("관리자 초대 발송에 실패했습니다. invitationId={}", invitation.invitationId(), exception);
    }
  }

  private record NormalizedInvitationInput(EmailAddress email, RoleCode roleCode) {}
}
