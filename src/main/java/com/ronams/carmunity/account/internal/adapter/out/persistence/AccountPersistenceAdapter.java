package com.ronams.carmunity.account.internal.adapter.out.persistence;

import com.ronams.carmunity.account.api.AccountAuthStatus;
import com.ronams.carmunity.account.api.AccountAuthSubject;
import com.ronams.carmunity.account.api.AccountAuthSubjectLookup;
import com.ronams.carmunity.account.api.CredentialChanged;
import com.ronams.carmunity.account.api.PasswordLoginAccount;
import com.ronams.carmunity.account.api.PasswordLoginAccountLookup;
import com.ronams.carmunity.account.api.PasswordLoginFailureReason;
import com.ronams.carmunity.account.api.PasswordLoginResultRecorder;
import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.port.out.AccountUniquenessPort;
import com.ronams.carmunity.account.internal.application.port.out.AuthRoleLookupPort;
import com.ronams.carmunity.account.internal.application.port.out.MemberAccountStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordCredentialStorePort;
import com.ronams.carmunity.account.internal.application.port.out.PasswordResetCredentialStorePort;
import com.ronams.carmunity.account.internal.application.port.out.SuperAdminBootstrapStorePort;
import com.ronams.carmunity.account.internal.domain.Account;
import com.ronams.carmunity.account.internal.domain.PasswordCredential;
import com.ronams.carmunity.account.internal.domain.PasswordHash;
import com.ronams.carmunity.account.internal.domain.RoleAssignment;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import com.ronams.carmunity.securityaudit.api.SecurityAuditAppender;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * account 가입 저장 Port의 JPA adapter입니다.
 *
 * <p>account, profile, password credential, role assignment를 하나의 가입 저장 단위로 다룹니다. pre-check로 중복을 걸러도
 * 동시 요청은 DB unique constraint가 마지막 방어선이므로 constraint 이름을 account 오류 코드로 변환합니다.
 */
@RequiredArgsConstructor
@Component
class AccountPersistenceAdapter
    implements AccountUniquenessPort,
        AuthRoleLookupPort,
        MemberAccountStorePort,
        SuperAdminBootstrapStorePort,
        PasswordCredentialStorePort,
        PasswordResetCredentialStorePort,
        PasswordLoginAccountLookup,
        PasswordLoginResultRecorder,
        AccountAuthSubjectLookup {

  private static final String UNIQUE_EMAIL_CONSTRAINT = "uk_account_email_normalized";
  private static final String UNIQUE_DISPLAY_NAME_CONSTRAINT =
      "uk_account_profile_display_name_normalized";

  private final AccountJpaRepository accountRepository;
  private final AccountProfileJpaRepository profileRepository;
  private final AccountPasswordCredentialJpaRepository passwordCredentialRepository;
  private final AuthRoleJpaRepository authRoleRepository;
  private final AccountRoleAssignmentJpaRepository roleAssignmentRepository;
  private final SecurityAuditAppender securityAuditAppender;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /** 회원 가입 중복 pre-check용 조회이며, 동시성 최종 방어는 DB unique constraint가 담당합니다. */
  @Override
  public boolean existsByNormalizedEmail(String normalizedEmail) {
    return accountRepository.existsByEmailNormalized(normalizedEmail);
  }

  /** 표시 이름 중복 pre-check용 조회이며, 동시성 최종 방어는 DB unique constraint가 담당합니다. */
  @Override
  public boolean existsByNormalizedDisplayName(String normalizedDisplayName) {
    return profileRepository.existsByDisplayNameNormalized(normalizedDisplayName);
  }

  /** usecase가 필요한 role 기준 데이터 누락을 저장 시점 전에 구분할 수 있게 확인합니다. */
  @Override
  public boolean existsByCode(RoleCode roleCode) {
    return authRoleRepository.existsByCode(roleCode.name());
  }

  /**
   * bootstrap command의 빠른 no-op 판단을 위한 조회입니다.
   *
   * <p>최종 중복 방지는 {@link #saveIfNoActiveSuperAdmin(Account)}의 role row lock 안에서 다시 수행합니다.
   */
  @Override
  public boolean activeSuperAdminExists() {
    return authRoleRepository
        .findByCode(RoleCode.ROLE_SUPER_ADMIN.name())
        .map(role -> roleAssignmentRepository.existsByRoleIdAndRevokedAtIsNull(role.id()))
        .orElse(false);
  }

  /**
   * normalized email로 비밀번호 로그인 판단에 필요한 account 공개 모델을 조회합니다.
   *
   * <p>내부 DB id는 adapter 안에서만 사용하고, auth 모듈에는 public id, status, password hash, role code, 비밀번호 변경
   * flag만 반환합니다.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<PasswordLoginAccount> findByNormalizedEmail(String normalizedEmail) {
    return accountRepository
        .findByEmailNormalized(normalizedEmail)
        .flatMap(
            account ->
                passwordCredentialRepository
                    .findById(account.id())
                    .map(credential -> toPasswordLoginAccount(account, credential)));
  }

  /**
   * auth 모듈에 token subject 생성에 필요한 최소 account 정보만 제공합니다.
   *
   * <p>account internal entity를 다른 모듈에 노출하지 않고, 내부 DB id와 email도 공개 계약에서 제외합니다. 여러 repository 조회가
   * 하나의 보안 판단으로 조립되므로 read-only transaction 안에서 실행합니다.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<AccountAuthSubject> findByPublicId(UUID accountPublicId) {
    return accountRepository
        .findByPublicId(accountPublicId)
        .map(
            account ->
                new AccountAuthSubject(
                    account.publicId(),
                    AccountAuthStatus.valueOf(account.status().name()),
                    roleAssignmentRepository.findActiveRoleCodesByAccountId(account.id()),
                    passwordCredentialRepository
                        .findById(account.id())
                        .map(AccountPasswordCredentialJpaEntity::passwordChangeRequired)
                        .orElse(false)));
  }

  /**
   * account 가입 저장 묶음을 원자적으로 생성합니다.
   *
   * <p>이 adapter를 service transaction 밖에서 호출하더라도 profile constraint 실패가 account 단독 생성으로 남지 않게 별도
   * transaction 경계를 둡니다.
   */
  @Override
  @Transactional
  public void save(Account account) {
    try {
      saveAccountGraph(account);
      account
          .domainEvents()
          .forEach(
              event ->
                  securityAuditAppender.append(
                      AccountSecurityAuditRecords.signUpSuccess(
                          event.accountPublicId(), clock.instant())));
    } catch (DataIntegrityViolationException exception) {
      throw mapDataIntegrityViolation(exception);
    }
  }

  /**
   * 최초 최고 관리자 저장 전용 transaction입니다.
   *
   * <p>`ROLE_SUPER_ADMIN` 기준 row를 잠근 뒤 같은 transaction 안에서 활성 부여 이력을 재확인해 동시 bootstrap 중복 생성을 막습니다.
   */
  @Override
  @Transactional
  public Optional<Account> saveIfNoActiveSuperAdmin(Account account) {
    AuthRoleJpaEntity superAdminRole =
        authRoleRepository
            .findByCodeForUpdate(RoleCode.ROLE_SUPER_ADMIN.name())
            .orElseThrow(() -> new ApiException(AccountErrorCode.AUTH_ROLE_NOT_CONFIGURED));

    if (roleAssignmentRepository.existsByRoleIdAndRevokedAtIsNull(superAdminRole.id())) {
      return Optional.empty();
    }

    try {
      saveAccountGraph(account);
      securityAuditAppender.append(
          AccountSecurityAuditRecords.roleGrantedBySuperAdminBootstrap(
              account.publicId(), clock.instant()));
      securityAuditAppender.append(
          AccountSecurityAuditRecords.passwordResetRequiredByInitialAdminBootstrap(
              account.publicId(), clock.instant()));
      return Optional.of(account);
    } catch (DataIntegrityViolationException exception) {
      throw mapDataIntegrityViolation(exception);
    }
  }

  /**
   * 비밀번호 변경 usecase에 credential hash와 강제 변경 flag만 제공합니다.
   *
   * <p>비밀번호 원문은 이 경계에 없고, 내부 DB id는 credential 조회에만 사용한 뒤 port 밖으로 내보내지 않습니다.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<PasswordCredentialRecord> findByAccountPublicId(UUID accountPublicId) {
    return accountRepository
        .findByPublicId(accountPublicId)
        .flatMap(
            account ->
                passwordCredentialRepository
                    .findById(account.id())
                    .map(
                        credential ->
                            new PasswordCredentialRecord(
                                credential.passwordHash(), credential.passwordChangeRequired())));
  }

  /**
   * 비밀번호 변경 성공 결과를 credential, refresh token, 감사 이벤트에 원자적으로 반영합니다.
   *
   * <p>원문 password와 token은 이 경계로 들어오지 않고, 저장 가능한 hash와 폐기 reason만 남깁니다.
   */
  @Override
  @Transactional
  public void changePassword(UUID accountPublicId, PasswordHash passwordHash, Instant changedAt) {
    AccountJpaEntity account = findAccountByPublicId(accountPublicId);
    changePasswordCredential(account.id(), passwordHash, changedAt);
    eventPublisher.publishEvent(new CredentialChanged(account.publicId(), changedAt));
    securityAuditAppender.append(
        AccountSecurityAuditRecords.passwordChangedSuccess(account.publicId(), changedAt));
  }

  /**
   * 비밀번호 재설정 성공 결과를 credential, refresh token, 감사 이벤트에 원자적으로 반영합니다.
   *
   * <p>reset은 account status를 바꾸지 않습니다. 따라서 PENDING account는 이메일 인증 완료 전까지 PENDING으로 남습니다.
   */
  @Override
  @Transactional
  public void resetPassword(Long accountId, PasswordHash passwordHash, Instant changedAt) {
    AccountJpaEntity account = findAccountById(accountId);
    changePasswordCredential(accountId, passwordHash, changedAt);
    eventPublisher.publishEvent(new CredentialChanged(account.publicId(), changedAt));
    securityAuditAppender.append(
        AccountSecurityAuditRecords.passwordResetCompleted(account.publicId(), changedAt));
  }

  private void changePasswordCredential(
      Long accountId, PasswordHash passwordHash, Instant changedAt) {
    AccountPasswordCredentialJpaEntity credential =
        passwordCredentialRepository
            .findById(accountId)
            .orElseThrow(() -> new ApiException(CommonErrorCode.INTERNAL_ERROR));
    PasswordCredential changedCredential = credential.toDomain().changeTo(passwordHash);
    credential.changePassword(changedCredential, changedAt);
    passwordCredentialRepository.saveAndFlush(credential);
  }

  /** current password 불일치 감사 이벤트를 account에 연결해 저장합니다. */
  @Override
  @Transactional
  public void recordPasswordChangeFailure(UUID accountPublicId, Instant occurredAt) {
    AccountJpaEntity account = findAccountByPublicId(accountPublicId);
    securityAuditAppender.append(
        AccountSecurityAuditRecords.passwordChangedFailure(account.publicId(), occurredAt));
  }

  /**
   * 로그인 성공 결과를 account 관련 테이블에 원자적으로 반영합니다.
   *
   * <p>refresh token hash 저장, 실패 횟수 초기화, last_login_at, PASSWORD_LOGIN 성공 감사 이벤트가 함께 성공하거나
   * rollback되어야 합니다.
   */
  @Override
  @Transactional
  public void recordSuccess(UUID accountPublicId, Instant occurredAt) {
    AccountJpaEntity account = findAccountByPublicId(accountPublicId);
    accountRepository.updateLastLoginAt(account.id(), occurredAt);
    passwordCredentialRepository.resetLoginFailureState(account.id(), occurredAt);
  }

  /** refresh token 없이 browser session 로그인 상태와 성공 감사 이벤트를 원자적으로 반영합니다. */
  @Override
  @Transactional
  public void recordInteractiveSuccess(UUID accountPublicId, Instant occurredAt) {
    AccountJpaEntity account = findAccountByPublicId(accountPublicId);
    accountRepository.updateLastLoginAt(account.id(), occurredAt);
    passwordCredentialRepository.resetLoginFailureState(account.id(), occurredAt);
    securityAuditAppender.append(
        AccountSecurityAuditRecords.loginSuccess(account.publicId(), occurredAt));
  }

  /**
   * account를 특정한 로그인 실패를 credential 상태와 감사 이벤트로 기록합니다.
   *
   * <p>hard lock은 이번 범위에서 적용하지 않고, 실패 횟수와 마지막 실패 시각만 갱신합니다.
   */
  @Override
  @Transactional
  public void recordKnownFailure(
      UUID accountPublicId,
      String identifierHash,
      PasswordLoginFailureReason reason,
      Instant occurredAt) {
    AccountJpaEntity account = findAccountByPublicId(accountPublicId);
    passwordCredentialRepository.recordLoginFailure(account.id(), occurredAt);
    securityAuditAppender.append(
        AccountSecurityAuditRecords.loginFailure(
            account.publicId(), identifierHash, reason, occurredAt));
  }

  /** account를 특정하지 못한 로그인 실패를 identifier hash만 가진 감사 이벤트로 기록합니다. */
  @Override
  @Transactional
  public void recordUnknownFailure(String identifierHash, Instant occurredAt) {
    securityAuditAppender.append(
        AccountSecurityAuditRecords.unknownLoginFailure(identifierHash, occurredAt));
  }

  /**
   * 로그인 판단에 필요한 공개 account 모델을 조립합니다.
   *
   * <p>auth 모듈에는 public id, 상태, password hash, 활성 role code, 비밀번호 변경 flag만 넘기고 내부 DB id는 숨깁니다.
   */
  private PasswordLoginAccount toPasswordLoginAccount(
      AccountJpaEntity account, AccountPasswordCredentialJpaEntity credential) {
    return new PasswordLoginAccount(
        account.publicId(),
        AccountAuthStatus.valueOf(account.status().name()),
        credential.passwordHash(),
        roleAssignmentRepository.findActiveRoleCodesByAccountId(account.id()),
        credential.passwordChangeRequired());
  }

  /**
   * 이미 인증된 public id를 내부 account row로 되돌리는 adapter 내부 조회입니다.
   *
   * <p>호출 전 account 존재가 전제된 저장 흐름에서만 사용하므로, 누락은 사용자 오류가 아니라 내부 불변식 위반으로 숨깁니다.
   */
  private AccountJpaEntity findAccountByPublicId(UUID accountPublicId) {
    return accountRepository
        .findByPublicId(accountPublicId)
        .orElseThrow(() -> new ApiException(CommonErrorCode.INTERNAL_ERROR));
  }

  private AccountJpaEntity findAccountById(Long accountId) {
    return accountRepository
        .findById(accountId)
        .orElseThrow(() -> new ApiException(CommonErrorCode.INTERNAL_ERROR));
  }

  /**
   * account aggregate를 account/profile/credential/role assignment 저장 graph로 펼칩니다.
   *
   * <p>호출 method의 transaction 안에서 감사 이벤트와 함께 완료되어야 하며, role seed 누락이나 DB constraint 실패는 전체 저장을
   * 중단합니다.
   */
  private Long saveAccountGraph(Account account) {
    AccountJpaEntity accountEntity = accountRepository.saveAndFlush(AccountJpaEntity.from(account));
    Long accountId = accountEntity.id();

    profileRepository.saveAndFlush(AccountProfileJpaEntity.of(accountId, account.profile()));
    passwordCredentialRepository.saveAndFlush(
        AccountPasswordCredentialJpaEntity.of(accountId, account.passwordCredential()));
    for (RoleAssignment roleAssignment : account.roleAssignments()) {
      AuthRoleJpaEntity role =
          authRoleRepository
              .findByCode(roleAssignment.roleCode().name())
              .orElseThrow(() -> new ApiException(AccountErrorCode.AUTH_ROLE_NOT_CONFIGURED));
      roleAssignmentRepository.saveAndFlush(
          AccountRoleAssignmentJpaEntity.systemGranted(accountId, role.id()));
    }
    return accountId;
  }

  /** 알려진 가입 중복 constraint만 사용자 오류로 변환하고, 나머지 DB 오류는 내부 오류로 숨깁니다. */
  private ApiException mapDataIntegrityViolation(DataIntegrityViolationException exception) {
    Optional<String> constraintName = findConstraintName(exception);
    if (constraintName.filter(UNIQUE_EMAIL_CONSTRAINT::equals).isPresent()) {
      return new ApiException(AccountErrorCode.EMAIL_ALREADY_USED, exception);
    }
    if (constraintName.filter(UNIQUE_DISPLAY_NAME_CONSTRAINT::equals).isPresent()) {
      return new ApiException(AccountErrorCode.DISPLAY_NAME_ALREADY_USED, exception);
    }
    return new ApiException(CommonErrorCode.INTERNAL_ERROR, exception);
  }

  /** Spring Data 예외가 Hibernate constraint 예외를 cause chain 안에 감싸므로 constraint 이름만 추출합니다. */
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
