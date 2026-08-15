package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.RoleCode;
import java.time.Instant;
import java.util.UUID;

/**
 * persistence가 저장 후 반환하는 관리자 초대 snapshot입니다.
 *
 * @param invitationId 내부 초대 id입니다.
 * @param publicId 외부 API와 이메일 링크에 노출할 초대 public id입니다.
 * @param email 정규화되어 저장된 초대 대상 email입니다.
 * @param roleCode 초대된 관리자 role입니다.
 * @param invitedByAccountId 초대를 생성한 내부 account id입니다.
 * @param expiresAt 초대 token 만료 시각입니다.
 * @param previousInvitationRevoked 같은 email의 기존 open 초대를 폐기했는지 여부입니다.
 */
public record AdminInvitationRecord(
    Long invitationId,
    UUID publicId,
    String email,
    RoleCode roleCode,
    Long invitedByAccountId,
    Instant expiresAt,
    boolean previousInvitationRevoked) {

  public AdminInvitationRecord(
      Long invitationId,
      UUID publicId,
      String email,
      RoleCode roleCode,
      Long invitedByAccountId,
      Instant expiresAt) {
    this(invitationId, publicId, email, roleCode, invitedByAccountId, expiresAt, false);
  }
}
