package com.ronams.carmunity.account.internal.adapter.in.web.adminapi;

import com.ronams.carmunity.account.internal.application.command.InviteAdminCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** 관리자 초대 생성 HTTP 요청 DTO입니다. */
public record InviteAdminRequest(
    @Schema(description = "초대 대상 email") @NotBlank @Email String email,
    @Schema(description = "초대할 관리자 role code") @NotBlank String roleCode) {

  InviteAdminCommand toCommand(UUID inviterPublicId) {
    return new InviteAdminCommand(inviterPublicId, email, roleCode);
  }
}
