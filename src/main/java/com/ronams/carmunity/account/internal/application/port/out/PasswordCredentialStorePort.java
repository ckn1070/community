package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.PasswordHash;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 비밀번호 credential 조회, 변경, 감사 기록 저장 경계입니다. */
public interface PasswordCredentialStorePort {

  /**
   * 비밀번호 변경 판단에 필요한 credential 정보만 조회합니다.
   *
   * @param accountPublicId 인증된 account public id입니다.
   * @return 저장된 password hash와 강제 변경 flag입니다. 내부 DB id와 원문 password는 포함하지 않습니다.
   */
  Optional<PasswordCredentialRecord> findByAccountPublicId(UUID accountPublicId);

  /**
   * 새 password hash 저장, 강제 변경 flag 해제, 기존 refresh token 폐기, 감사 이벤트 저장을 한 저장 경계에 위임합니다.
   *
   * @param accountPublicId 인증된 account public id입니다.
   * @param passwordHash 저장 가능한 새 password hash입니다.
   * @param changedAt 변경 발생 시각입니다.
   */
  void changePassword(UUID accountPublicId, PasswordHash passwordHash, Instant changedAt);

  /**
   * current password 불일치를 감사 이벤트로 저장합니다.
   *
   * @param accountPublicId 인증된 account public id입니다.
   * @param occurredAt 실패 발생 시각입니다.
   */
  void recordPasswordChangeFailure(UUID accountPublicId, Instant occurredAt);

  /** 비밀번호 변경 usecase에 노출하는 최소 credential 조회 결과입니다. */
  record PasswordCredentialRecord(String passwordHash, boolean passwordChangeRequired) {}
}
