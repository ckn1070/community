package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.RoleCode;
import java.time.Instant;
import java.util.UUID;

/**
 * 초대 수락 검증에 필요한 관리자 초대 snapshot입니다.
 *
 * @param invitationId 내부 초대 id입니다.
 * @param publicId 외부 path에 노출된 초대 public id입니다.
 * @param email 정규화되어 저장된 초대 대상 email입니다.
 * @param roleId 초대 row가 참조하는 내부 role id입니다.
 * @param roleCode 수락 시 부여할 role code입니다.
 * @param tokenHash raw token의 저장 hash입니다.
 * @param invitedByAccountId 초대를 생성한 내부 account id입니다.
 * @param expiresAt token 만료 시각입니다.
 * @param closed 이미 수락 또는 회수된 초대인지 여부입니다.
 */
public record AdminInvitationForAcceptance(
    Long invitationId,
    UUID publicId,
    String email,
    Long roleId,
    RoleCode roleCode,
    String tokenHash,
    Long invitedByAccountId,
    Instant expiresAt,
    boolean closed) {

  public AdminInvitationForAcceptance withExpiresAt(Instant nextExpiresAt) {
    return new AdminInvitationForAcceptance(
        invitationId,
        publicId,
        email,
        roleId,
        roleCode,
        tokenHash,
        invitedByAccountId,
        nextExpiresAt,
        closed);
  }

  public AdminInvitationForAcceptance withClosed(boolean nextClosed) {
    return new AdminInvitationForAcceptance(
        invitationId,
        publicId,
        email,
        roleId,
        roleCode,
        tokenHash,
        invitedByAccountId,
        expiresAt,
        nextClosed);
  }
}
