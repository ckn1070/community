package com.ronams.carmunity.account.internal.adapter.in.web.adminapi;

import com.ronams.carmunity.account.internal.application.command.AcceptAdminInvitationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 관리자 초대 수락 HTTP 요청 DTO입니다. */
public record AcceptAdminInvitationRequest(
    @Schema(description = "이메일 링크로 전달된 raw 초대 token") @NotBlank String token,
    @Schema(description = "새 관리자 비밀번호. 최소 10자, 최대 128자입니다.", minLength = 10, maxLength = 128)
        @NotBlank
        @Size(min = 10, max = 128)
        String password,
    @Schema(description = "새 관리자 비밀번호 확인. password와 같은 값이어야 합니다.", minLength = 10, maxLength = 128)
        @NotBlank
        @Size(min = 10, max = 128)
        String passwordConfirm,
    @Schema(description = "새 관리자 표시 이름") @NotBlank String displayName) {

  AcceptAdminInvitationCommand toCommand(UUID invitationId) {
    return new AcceptAdminInvitationCommand(
        invitationId, token, password, passwordConfirm, displayName);
  }
}
