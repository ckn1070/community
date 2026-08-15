package com.ronams.carmunity.account.internal.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 로그인 가능한 인증 주체 aggregate root입니다.
 *
 * <p>Type: Aggregate Root.
 *
 * <p>자체 가입 factory는 account, profile, password credential, 초기 role assignment가 함께 생성되어야 한다는 일관성 경계를
 * domain 안에 고정합니다. 관리자 초대 수락 factory도 신규 account graph 생성 경계는 같은 root가 소유합니다.
 */
public final class Account {

  private final UUID publicId;
  private final EmailAddress email;
  private final AccountStatus status;
  private final Instant emailVerifiedAt;
  private final AccountProfile profile;
  private final PasswordCredential passwordCredential;
  private final List<RoleAssignment> roleAssignments;
  private final List<AccountRegisteredEvent> domainEvents;

  private record AccountIdentity(
      UUID publicId, EmailAddress email, AccountStatus status, Instant emailVerifiedAt) {
    private AccountIdentity {
      Objects.requireNonNull(publicId, "publicId must not be null");
      Objects.requireNonNull(email, "email must not be null");
      Objects.requireNonNull(status, "status must not be null");
    }
  }

  private Account(
      AccountIdentity identity,
      AccountProfile profile,
      PasswordCredential passwordCredential,
      List<RoleAssignment> roleAssignments,
      List<AccountRegisteredEvent> domainEvents) {
    this.publicId = identity.publicId();
    this.email = identity.email();
    this.status = identity.status();
    this.emailVerifiedAt = identity.emailVerifiedAt();
    this.profile = Objects.requireNonNull(profile, "profile must not be null");
    this.passwordCredential =
        Objects.requireNonNull(passwordCredential, "passwordCredential must not be null");
    this.roleAssignments = List.copyOf(roleAssignments);
    this.domainEvents = List.copyOf(domainEvents);
  }

  /**
   * 일반 회원 자체 가입 account를 생성합니다.
   *
   * <p>이 factory는 관리자 role 부여, token 발급, 이메일 인증 완료 처리를 포함하지 않습니다.
   *
   * @param publicId 외부 API에 노출할 account 식별자입니다.
   * @param email 정규화와 검증이 끝난 email입니다.
   * @param displayName 공개 표시 이름입니다.
   * @param passwordHash 저장 가능한 비밀번호 hash입니다.
   * @return `PENDING` 상태와 `ROLE_MEMBER` role assignment를 가진 account aggregate입니다.
   */
  public static Account registerMember(
      UUID publicId, EmailAddress email, DisplayName displayName, PasswordHash passwordHash) {
    return new Account(
        new AccountIdentity(publicId, email, AccountStatus.PENDING, null),
        AccountProfile.memberProfile(displayName),
        PasswordCredential.memberSignUp(passwordHash),
        List.of(RoleAssignment.systemGranted(RoleCode.ROLE_MEMBER)),
        List.of(AccountRegisteredEvent.occurred(publicId)));
  }

  /**
   * 최초 최고 관리자 bootstrap account를 생성합니다.
   *
   * <p>이 factory는 운영 bootstrap 전용이며, 여러 관리자 생성이나 관리자 초대 흐름을 포함하지 않습니다.
   *
   * @param publicId 외부 API에 노출할 account 식별자입니다.
   * @param email 정규화와 검증이 끝난 email입니다.
   * @param displayName 공개 표시 이름입니다.
   * @param passwordHash 저장 가능한 초기 비밀번호 hash입니다.
   * @return `ACTIVE` 상태, `ROLE_SUPER_ADMIN`, 비밀번호 변경 강제 credential을 가진 account aggregate입니다.
   */
  public static Account bootstrapSuperAdmin(
      UUID publicId, EmailAddress email, DisplayName displayName, PasswordHash passwordHash) {
    return new Account(
        new AccountIdentity(publicId, email, AccountStatus.ACTIVE, null),
        AccountProfile.memberProfile(displayName),
        PasswordCredential.bootstrapTemporary(passwordHash),
        List.of(RoleAssignment.systemGranted(RoleCode.ROLE_SUPER_ADMIN)),
        List.of());
  }

  /**
   * 관리자 초대 수락으로 생성되는 account를 만듭니다.
   *
   * <p>초대 token이 email 소유권 확인 역할을 하므로 생성 시점에 `ACTIVE`와 email 인증 완료 상태를 함께 고정합니다.
   *
   * @param publicId 외부 API에 노출할 account 식별자입니다.
   * @param email 정규화와 검증이 끝난 초대 email입니다.
   * @param displayName 공개 표시 이름입니다.
   * @param passwordHash 저장 가능한 비밀번호 hash입니다.
   * @param roleCode 초대로 부여할 관리자 role입니다.
   * @param verifiedAt 초대 token 검증으로 email 소유권을 확인한 시각입니다.
   * @return `ACTIVE` 상태와 관리자 role assignment를 가진 account aggregate입니다.
   */
  public static Account acceptAdminInvitation(
      UUID publicId,
      EmailAddress email,
      DisplayName displayName,
      PasswordHash passwordHash,
      RoleCode roleCode,
      Instant verifiedAt) {
    Objects.requireNonNull(roleCode, "roleCode must not be null");
    if (roleCode != RoleCode.ROLE_ADMIN) {
      throw new IllegalArgumentException("admin invitation role must be ROLE_ADMIN");
    }
    return new Account(
        new AccountIdentity(
            publicId,
            email,
            AccountStatus.ACTIVE,
            Objects.requireNonNull(verifiedAt, "verifiedAt must not be null")),
        AccountProfile.adminInvitationProfile(displayName),
        PasswordCredential.adminInvitation(passwordHash),
        List.of(RoleAssignment.adminInvitationGranted(roleCode)),
        List.of());
  }

  public UUID publicId() {
    return publicId;
  }

  public EmailAddress email() {
    return email;
  }

  public AccountStatus status() {
    return status;
  }

  public Optional<Instant> emailVerifiedAt() {
    return Optional.ofNullable(emailVerifiedAt);
  }

  public AccountProfile profile() {
    return profile;
  }

  public PasswordCredential passwordCredential() {
    return passwordCredential;
  }

  public List<RoleAssignment> roleAssignments() {
    return roleAssignments;
  }

  public List<AccountRegisteredEvent> domainEvents() {
    return domainEvents;
  }
}
