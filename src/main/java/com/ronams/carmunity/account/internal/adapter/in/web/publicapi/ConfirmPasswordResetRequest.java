package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.account.internal.application.command.ConfirmPasswordResetCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 재설정 완료 요청 DTO입니다.
 *
 * <p>token은 URL-safe bearer secret이므로 Web 경계에서 명백히 잘못된 문자를 먼저 거릅니다.
 *
 * @param token reset email에서 전달된 raw token입니다.
 * @param newPassword 새 비밀번호 원문입니다. application 계층에서 정책 검증 후 hash로만 저장합니다.
 * @param newPasswordConfirmation 새 비밀번호 확인 원문입니다. 일치 여부 검증 뒤 저장하지 않습니다.
 */
@Schema(description = "비밀번호 재설정 완료 요청")
public record ConfirmPasswordResetRequest(
    @Schema(description = "비밀번호 재설정 raw token", minLength = 32, maxLength = 512)
        @NotBlank
        @Size(min = 32, max = 512)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String token,
    @NotBlank @Schema(description = "새 비밀번호") String newPassword,
    @NotBlank @Schema(description = "새 비밀번호 확인") String newPasswordConfirmation) {

  /** Web 요청 DTO를 application command로 변환합니다. */
  ConfirmPasswordResetCommand toCommand() {
    return new ConfirmPasswordResetCommand(token, newPassword, newPasswordConfirmation);
  }
}
