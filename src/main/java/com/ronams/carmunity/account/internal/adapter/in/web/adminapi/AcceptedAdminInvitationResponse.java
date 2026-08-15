package com.ronams.carmunity.account.internal.adapter.in.web.adminapi;

import com.ronams.carmunity.account.internal.application.result.AcceptedAdminInvitationResult;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 관리자 초대 수락 HTTP 응답 DTO입니다. */
public record AcceptedAdminInvitationResponse(
    @Schema(description = "생성된 account public id") UUID accountId,
    @Schema(description = "정규화된 관리자 email") String email,
    @Schema(description = "부여된 관리자 role") RoleCode roleCode) {

  static AcceptedAdminInvitationResponse from(AcceptedAdminInvitationResult result) {
    return new AcceptedAdminInvitationResponse(
        result.accountId(), result.email(), result.roleCode());
  }
}
