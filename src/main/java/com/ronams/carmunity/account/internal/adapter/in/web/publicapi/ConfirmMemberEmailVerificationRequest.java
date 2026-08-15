package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.account.internal.application.command.ConfirmMemberEmailVerificationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 일반 회원 이메일 인증 완료 요청 DTO입니다.
 *
 * <p>token은 URL-safe bearer secret이므로 Web 경계에서 명백히 잘못된 문자를 먼저 거릅니다.
 *
 * @param token 이메일 인증 링크에서 전달된 raw token입니다.
 */
@Schema(description = "일반 회원 이메일 인증 완료 요청")
public record ConfirmMemberEmailVerificationRequest(
    @Schema(description = "이메일 인증 raw token", minLength = 32, maxLength = 512)
        @NotBlank
        @Size(min = 32, max = 512)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String token) {

  /** Web 요청 DTO를 application command로 변환합니다. */
  ConfirmMemberEmailVerificationCommand toCommand() {
    return new ConfirmMemberEmailVerificationCommand(token);
  }
}
