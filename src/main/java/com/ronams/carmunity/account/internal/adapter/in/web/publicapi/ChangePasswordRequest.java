package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.account.internal.application.command.ChangePasswordCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * 비밀번호 변경 요청 body입니다.
 *
 * @param currentPassword 현재 비밀번호 원문입니다. 저장하거나 응답에 노출하지 않습니다.
 * @param newPassword 새 비밀번호 원문입니다. application 계층에서 정책 검증 후 hash로만 저장합니다.
 * @param newPasswordConfirmation 새 비밀번호 확인 원문입니다. 일치 여부 검증 뒤 저장하지 않습니다.
 */
public record ChangePasswordRequest(
    @NotBlank @Schema(description = "현재 비밀번호") String currentPassword,
    @NotBlank @Schema(description = "새 비밀번호") String newPassword,
    @NotBlank @Schema(description = "새 비밀번호 확인") String newPasswordConfirmation) {

  /** DTO는 원문 password를 저장하지 않고 usecase command로만 전달하며, 정책 검증은 application/domain이 담당합니다. */
  ChangePasswordCommand toCommand(UUID accountPublicId) {
    return new ChangePasswordCommand(
        accountPublicId, currentPassword, newPassword, newPasswordConfirmation);
  }
}
