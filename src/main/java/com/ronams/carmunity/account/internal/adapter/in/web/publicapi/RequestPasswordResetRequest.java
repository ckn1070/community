package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.account.internal.application.command.RequestPasswordResetCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 재설정 요청 DTO입니다.
 *
 * <p>email 형식 정규화와 상세 검증은 application/domain 경계에서 수행하고, Web DTO는 빈 값과 저장 한계를 먼저 거릅니다.
 *
 * @param email reset token 발송을 요청한 email입니다.
 */
@Schema(description = "비밀번호 재설정 요청")
public record RequestPasswordResetRequest(
    @Schema(description = "reset token을 받을 email", example = "user@example.com", maxLength = 254)
        @NotBlank
        @Size(max = 254)
        String email) {

  /** Web 요청 DTO를 application command로 변환합니다. */
  RequestPasswordResetCommand toCommand() {
    return new RequestPasswordResetCommand(email);
  }
}
