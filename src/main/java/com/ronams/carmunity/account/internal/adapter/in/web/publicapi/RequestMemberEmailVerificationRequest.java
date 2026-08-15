package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.account.internal.application.command.RequestMemberEmailVerificationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 일반 회원 이메일 인증 요청 DTO입니다.
 *
 * <p>email 형식 정규화와 상세 검증은 application/domain 경계에서 수행하고, Web DTO는 빈 값과 저장 한계를 먼저 거릅니다.
 *
 * @param email 인증 메일을 요청한 email입니다.
 */
@Schema(description = "일반 회원 이메일 인증 요청")
public record RequestMemberEmailVerificationRequest(
    @Schema(description = "인증 메일을 받을 email", example = "user@example.com", maxLength = 254)
        @NotBlank
        @Size(max = 254)
        String email) {

  /** Web 요청 DTO를 application command로 변환합니다. */
  RequestMemberEmailVerificationCommand toCommand() {
    return new RequestMemberEmailVerificationCommand(email);
  }
}
