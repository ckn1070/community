package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.port.out.AcceptedAdminAccountStoreRequest;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationAcceptancePort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationForAcceptance;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationRecord;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationRevocationPort;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationRevokeRequest;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationSaveRequest;
import com.ronams.carmunity.account.internal.application.port.out.AdminInvitationStorePort;
import com.ronams.carmunity.account.internal.domain.RoleAssignment;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 초대 저장 port의 JPA adapter입니다. */
@RequiredArgsConstructor
@Component
class AdminInvitationPersistenceAdapter
    implements AdminInvitationStorePort,
        AdminInvitationAcceptancePort,
        AdminInvitationRevocationPort {

  private static final String UNIQUE_EMAIL_CONSTRAINT = "uk_account_email_normalized";
  private static final String UNIQUE_DISPLAY_NAME_CONSTRAINT =
      "uk_account_profile_display_name_normalized";

  private final AccountJpaRepository accountRepository;
  private final AccountProfileJpaRepository profileRepository;
  private final AccountPasswordCredentialJpaRepository passwordCredentialRepository;
  private final AccountRoleAssignmentJpaRepository roleAssignmentRepository;
  private final AuthRoleJpaRepository authRoleRepository;
  private final AdminInvitationJpaRepository invitationRepository;
  private final AuthEventJpaRepository authEventRepository;

  /**
   * 같은 email의 기존 open 초대를 폐기하고 새 초대를 저장합니다.
   *
   * <p>open email partial unique index와 pessimistic lock을 함께 사용해 동시 재초대가 open row를 둘 이상 남기지 못하게
   * 합니다.
   */
  @Override
  @Transactional
  public AdminInvitationRecord save(AdminInvitationSaveRequest request) {
    AccountJpaEntity inviter =
        accountRepository
            .findByPublicId(request.inviterPublicId())
            .orElseThrow(() -> new ApiException(CommonErrorCode.INTERNAL_ERROR));
    AuthRoleJpaEntity role =
        authRoleRepository
            .findByCode(request.roleCode().name())
            .orElseThrow(() -> new ApiException(AccountErrorCode.AUTH_ROLE_NOT_CONFIGURED));

    List<AdminInvitationJpaEntity> openInvitations =
        invitationRepository.findOpenByEmailNormalizedForUpdate(request.email().normalized());
    openInvitations.forEach(invitation -> invitation.revoke(inviter.id(), request.issuedAt()));
    invitationRepository.flush();

    AdminInvitationJpaEntity saved =
        invitationRepository.saveAndFlush(
            AdminInvitationJpaEntity.issue(request, inviter.id(), role.id()));

    return saved.toRecord(RoleCode.valueOf(role.code()), !openInvitations.isEmpty());
  }

  /** commit 이후 초대 발송 결과를 관리자 초대 생성 감사 이벤트로 저장합니다. */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordCreated(AdminInvitationRecord invitation, boolean notificationSuccessful) {
    authEventRepository.save(
        AuthEventJpaEntity.adminInvitationCreated(
            invitation.invitedByAccountId(),
            invitation.publicId(),
            invitation.email(),
            invitation.roleCode().name(),
            invitation.previousInvitationRevoked(),
            notificationSuccessful));
  }

  /**
   * public 초대 수락 API가 검증할 초대 snapshot을 pessimistic lock과 함께 조회합니다.
   *
   * <p>service transaction 안에서 호출하면 수락 저장까지 같은 DB transaction이 lock을 유지합니다.
   */
  @Override
  @Transactional
  public Optional<AdminInvitationForAcceptance> findForAcceptance(UUID invitationPublicId) {
    return invitationRepository
        .findByPublicId(invitationPublicId)
        .map(
            invitation -> {
              AuthRoleJpaEntity role =
                  authRoleRepository
                      .findById(invitation.roleId())
                      .orElseThrow(
                          () -> new ApiException(AccountErrorCode.AUTH_ROLE_NOT_CONFIGURED));
              return invitation.toAcceptance(RoleCode.valueOf(role.code()));
            });
  }

  /**
   * 새 관리자 account graph, 초대 accepted 상태, 수락 감사 이벤트를 하나의 저장 단위로 반영합니다.
   *
   * <p>동시 수락/회수는 초대 row lock과 terminal state 재확인으로 하나만 성공하게 합니다.
   */
  @Override
  @Transactional
  public void accept(AcceptedAdminAccountStoreRequest request) {
    try {
      AdminInvitationJpaEntity invitation =
          invitationRepository
              .findById(request.invitationId())
              .orElseThrow(() -> new ApiException(AccountErrorCode.ADMIN_INVITATION_NOT_FOUND));
      if (invitation.closed()) {
        throw new ApiException(AccountErrorCode.ADMIN_INVITATION_ALREADY_CLOSED);
      }

      AccountJpaEntity accountEntity =
          accountRepository.saveAndFlush(AccountJpaEntity.from(request.account()));
      Long accountId = accountEntity.id();

      profileRepository.saveAndFlush(
          AccountProfileJpaEntity.of(accountId, request.account().profile()));
      passwordCredentialRepository.saveAndFlush(
          AccountPasswordCredentialJpaEntity.of(accountId, request.account().passwordCredential()));
      for (RoleAssignment ignored : request.account().roleAssignments()) {
        roleAssignmentRepository.saveAndFlush(
            AccountRoleAssignmentJpaEntity.adminInvitationGranted(
                accountId, request.roleId(), request.invitedByAccountId()));
      }

      invitation.accept(accountId, request.acceptedAt());
      invitationRepository.saveAndFlush(invitation);
      authEventRepository.save(
          AuthEventJpaEntity.adminInvitationAccepted(
              accountId,
              request.invitationPublicId(),
              request.roleCode().name(),
              request.invitedByAccountId()));
    } catch (DataIntegrityViolationException exception) {
      throw mapAccountDataIntegrityViolation(exception);
    }
  }

  /**
   * open 초대를 회수 상태로 전환하고 회수 감사 이벤트를 저장합니다.
   *
   * <p>만료된 초대도 아직 terminal state가 아니면 명시 회수가 가능하며, 이미 수락/회수된 초대는 conflict로 변환합니다.
   */
  @Override
  @Transactional
  public void revoke(AdminInvitationRevokeRequest request) {
    AccountJpaEntity revoker =
        accountRepository
            .findByPublicId(request.revokerPublicId())
            .orElseThrow(() -> new ApiException(CommonErrorCode.INTERNAL_ERROR));
    AdminInvitationJpaEntity invitation =
        invitationRepository
            .findByPublicId(request.invitationPublicId())
            .orElseThrow(() -> new ApiException(AccountErrorCode.ADMIN_INVITATION_NOT_FOUND));
    if (invitation.closed()) {
      throw new ApiException(AccountErrorCode.ADMIN_INVITATION_ALREADY_CLOSED);
    }

    invitation.revoke(revoker.id(), request.revokedAt());
    invitationRepository.saveAndFlush(invitation);
    authEventRepository.save(
        AuthEventJpaEntity.adminInvitationRevoked(
            revoker.id(), invitation.publicId(), invitation.emailNormalized()));
  }

  // account graph 저장 중 발생한 알려진 unique constraint만 사용자 오류로 변환합니다.
  private ApiException mapAccountDataIntegrityViolation(DataIntegrityViolationException exception) {
    Optional<String> constraintName = findConstraintName(exception);
    if (constraintName.filter(UNIQUE_EMAIL_CONSTRAINT::equals).isPresent()) {
      return new ApiException(AccountErrorCode.EMAIL_ALREADY_USED, exception);
    }
    if (constraintName.filter(UNIQUE_DISPLAY_NAME_CONSTRAINT::equals).isPresent()) {
      return new ApiException(AccountErrorCode.DISPLAY_NAME_ALREADY_USED, exception);
    }
    return new ApiException(CommonErrorCode.INTERNAL_ERROR, exception);
  }

  // Spring Data 예외가 Hibernate constraint 예외를 cause chain 안에 감싸므로 이름만 추출합니다.
  private Optional<String> findConstraintName(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof ConstraintViolationException constraintViolationException) {
        return Optional.ofNullable(constraintViolationException.getConstraintName());
      }
      current = current.getCause();
    }
    return Optional.empty();
  }
}
