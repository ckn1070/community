package com.ronams.carmunity.auth.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.auth.internal.application.command.RotateRefreshTokenCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * refresh token 재발급 요청 DTO입니다.
 *
 * <p>refresh token 원문은 저장하지 않고 application command로만 전달합니다.
 *
 * @param refreshToken 클라이언트가 보관 중인 refresh token 원문입니다.
 */
@Schema(description = "refresh token 재발급 요청")
public record RotateRefreshTokenRequest(
    @Schema(description = "기존 refresh token 원문", maxLength = 2048) @NotBlank @Size(max = 2048)
        String refreshToken) {

  /**
   * Web 요청 DTO를 application command로 변환합니다.
   *
   * @return Web framework annotation이 없는 유스케이스 입력입니다.
   */
  RotateRefreshTokenCommand toCommand() {
    return new RotateRefreshTokenCommand(refreshToken);
  }
}
