package com.ronams.carmunity.account.api;

import java.util.Objects;
import java.util.UUID;

/**
 * 비밀번호 로그인 성공 후 account 모듈이 원자적으로 기록할 결과입니다.
 *
 * <p>내부 DB id 대신 public id로 account를 식별하고, refresh token은 원문 없이 hash만 저장합니다.
 *
 * @param accountPublicId 로그인에 성공한 account public id입니다.
 * @param refreshToken 저장할 refresh token lifecycle 값입니다.
 */
public record PasswordLoginSuccess(UUID accountPublicId, StoredRefreshToken refreshToken) {

  public PasswordLoginSuccess {
    Objects.requireNonNull(accountPublicId, "accountPublicId must not be null");
    Objects.requireNonNull(refreshToken, "refreshToken must not be null");
  }
}
