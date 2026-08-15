package com.ronams.carmunity.account.internal.application.result;

import java.util.Optional;
import java.util.UUID;

/**
 * 최초 최고 관리자 bootstrap 결과입니다.
 *
 * @param status bootstrap 처리 결과입니다.
 * @param accountPublicId 생성된 account public id입니다.
 * @param email 정규화되어 저장된 email입니다.
 * @param displayName 공개 표시 이름입니다.
 */
public record BootstrapSuperAdminAccountResult(
    Status status,
    Optional<UUID> accountPublicId,
    Optional<String> email,
    Optional<String> displayName) {

  public enum Status {
    CREATED,
    ALREADY_BOOTSTRAPPED
  }

  /** 새로 생성된 account의 public 결과만 담고 내부 DB id와 초기 password는 결과에서 제외합니다. */
  public static BootstrapSuperAdminAccountResult created(
      UUID accountPublicId, String email, String displayName) {
    return new BootstrapSuperAdminAccountResult(
        Status.CREATED, Optional.of(accountPublicId), Optional.of(email), Optional.of(displayName));
  }

  /** 이미 활성 최고 관리자가 있으면 기존 account 식별자를 노출하지 않는 no-op 결과로 응답합니다. */
  public static BootstrapSuperAdminAccountResult alreadyBootstrapped() {
    return new BootstrapSuperAdminAccountResult(
        Status.ALREADY_BOOTSTRAPPED, Optional.empty(), Optional.empty(), Optional.empty());
  }
}
