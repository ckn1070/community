package com.ronams.carmunity.account.internal.domain;

import java.util.Objects;

/**
 * account aggregate가 소유하는 활성 role 부여입니다.
 *
 * <p>Type: Account aggregate 내부 Value Object.
 *
 * <p>자체 가입의 초기 role은 시스템이 자동 부여하고, 관리자 초대 수락 role은 초대한 관리자 context와 함께 persistence에서 부여자를 기록합니다.
 *
 * @param roleCode 부여된 role입니다.
 * @param systemGranted 시스템 자동 부여 여부입니다.
 */
public record RoleAssignment(RoleCode roleCode, boolean systemGranted) {

  public RoleAssignment {
    Objects.requireNonNull(roleCode, "roleCode must not be null");
  }

  /** 자체 가입 초기 role은 관리자 부여자가 아니라 시스템 자동 부여로 기록합니다. */
  static RoleAssignment systemGranted(RoleCode roleCode) {
    return new RoleAssignment(roleCode, true);
  }

  /** 관리자 초대 수락 role은 시스템 자동 부여가 아니므로 persistence가 초대한 관리자를 부여자로 기록합니다. */
  static RoleAssignment adminInvitationGranted(RoleCode roleCode) {
    return new RoleAssignment(roleCode, false);
  }
}
