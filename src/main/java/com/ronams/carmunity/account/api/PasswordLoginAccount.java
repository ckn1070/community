package com.ronams.carmunity.account.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 비밀번호 로그인 판단에 필요한 account 공개 조회 모델입니다.
 *
 * <p>auth 모듈은 account 내부 DB id와 JPA entity를 모르고, password hash는 검증 port 입력으로만 사용해야 합니다.
 *
 * @param publicId 외부에 노출 가능한 account 식별자입니다.
 * @param status 로그인 정책을 결정할 account 상태입니다.
 * @param passwordHash 저장된 password encoder 결과입니다. 로그와 응답에 노출하지 않습니다.
 * @param roleCodes access token role claim에 넣을 활성 role code 목록입니다.
 * @param passwordChangeRequired 비밀번호 변경 전 API 접근 제한 판단에 쓰는 flag입니다.
 */
public record PasswordLoginAccount(
    UUID publicId,
    AccountAuthStatus status,
    String passwordHash,
    List<String> roleCodes,
    boolean passwordChangeRequired) {

  public PasswordLoginAccount {
    Objects.requireNonNull(publicId, "publicId must not be null");
    Objects.requireNonNull(status, "status must not be null");
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new IllegalArgumentException("passwordHash must not be blank");
    }
    roleCodes = List.copyOf(Objects.requireNonNull(roleCodes, "roleCodes must not be null"));
  }

  /** access token subject로 필요한 최소 account 표현만 변환합니다. */
  public AccountAuthSubject toAuthSubject() {
    return new AccountAuthSubject(publicId, status, roleCodes, passwordChangeRequired);
  }
}
