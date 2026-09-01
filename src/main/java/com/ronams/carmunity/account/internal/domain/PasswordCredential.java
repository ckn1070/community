package com.ronams.carmunity.account.internal.domain;

import java.util.Objects;

/**
 * account aggregate가 소유하는 비밀번호 인증 수단입니다.
 *
 * <p>Type: Account aggregate 내부 Value Object.
 *
 * <p>사용자가 직접 설정한 비밀번호는 임시 비밀번호가 아니므로 가입 직후 강제 변경 상태로 만들지 않습니다.
 *
 * @param passwordHash 저장 가능한 비밀번호 hash입니다.
 * @param passwordChangeRequired 로그인 전 비밀번호 강제 변경 필요 여부입니다.
 */
public record PasswordCredential(PasswordHash passwordHash, boolean passwordChangeRequired) {

  public PasswordCredential {
    Objects.requireNonNull(passwordHash, "passwordHash must not be null");
  }

  /** 자체 가입 비밀번호는 임시 발급이 아니므로 가입 직후 강제 변경 대상으로 만들지 않습니다. */
  static PasswordCredential memberSignUp(PasswordHash passwordHash) {
    return new PasswordCredential(passwordHash, false);
  }

  /** bootstrap 최고 관리자 비밀번호는 운영자가 지정한 초기값이므로 첫 로그인 뒤 변경을 강제합니다. */
  static PasswordCredential bootstrapTemporary(PasswordHash passwordHash) {
    return new PasswordCredential(passwordHash, true);
  }

  /** 초대 수락자가 직접 설정한 비밀번호는 가입 직후 강제 변경 대상으로 만들지 않습니다. */
  static PasswordCredential adminInvitation(PasswordHash passwordHash) {
    return new PasswordCredential(passwordHash, false);
  }

  /** 사용자가 확인한 새 hash로 교체하고 강제 변경 상태를 해제합니다. */
  public PasswordCredential changeTo(PasswordHash newPasswordHash) {
    return new PasswordCredential(newPasswordHash, false);
  }
}
