package com.ronams.carmunity.account.internal.adapter.in.web.adminapi;

import com.ronams.carmunity.account.internal.application.result.AdminInvitationResult;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** 관리자 초대 생성 HTTP 응답 DTO입니다. */
public record InviteAdminResponse(
    @Schema(description = "초대 public id") UUID invitationId,
    @Schema(description = "정규화된 초대 대상 email") String email,
    @Schema(description = "초대된 관리자 role") RoleCode roleCode,
    @Schema(description = "초대 만료 시각") Instant expiresAt) {

  static InviteAdminResponse from(AdminInvitationResult result) {
    return new InviteAdminResponse(
        result.invitationId(), result.email(), result.roleCode(), result.expiresAt());
  }
}
