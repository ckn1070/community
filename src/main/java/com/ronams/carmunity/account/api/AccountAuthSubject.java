package com.ronams.carmunity.account.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 인증 토큰 subject를 만들 때 사용하는 account 공개 계약입니다.
 *
 * <p>내부 DB id, email, profile 정보는 token claim으로 노출하지 않도록 이 모델에 포함하지 않습니다.
 *
 * @param publicId 외부에 노출 가능한 account 식별자입니다.
 * @param status refresh token 재발급 시점에 다시 확인한 account 상태입니다.
 * @param roleCodes 활성 role code 목록입니다.
 * @param passwordChangeRequired 비밀번호 변경 전 API 접근 제한 판단에 쓰는 flag입니다.
 */
public record AccountAuthSubject(
    UUID publicId,
    AccountAuthStatus status,
    List<String> roleCodes,
    boolean passwordChangeRequired) {

  public AccountAuthSubject {
    Objects.requireNonNull(publicId, "publicId must not be null");
    Objects.requireNonNull(status, "status must not be null");
    roleCodes = List.copyOf(Objects.requireNonNull(roleCodes, "roleCodes must not be null"));
  }
}
