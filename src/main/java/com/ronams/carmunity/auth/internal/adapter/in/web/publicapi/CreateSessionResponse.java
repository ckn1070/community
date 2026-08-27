package com.ronams.carmunity.auth.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.auth.internal.application.result.CreatedSessionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 비밀번호 session 생성 성공 응답입니다.
 *
 * <p>refresh token 원문은 이 응답으로만 전달하고, DB에 저장되는 hash나 내부 account id는 포함하지 않습니다.
 */
@Schema(description = "비밀번호 로그인 성공 응답")
public record CreateSessionResponse(
    @Schema(description = "Bearer access token", requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,
    @Schema(
            description = "HTTP Authorization scheme",
            example = "Bearer",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String tokenType,
    @Schema(description = "access token 만료 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant accessTokenExpiresAt,
    @Schema(description = "refresh token 원문", requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken,
    @Schema(description = "refresh token 만료 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant refreshTokenExpiresAt,
    @Schema(description = "회원 public id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID memberId,
    @Schema(description = "role code 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> roles,
    @Schema(description = "session 접근 범위", requiredMode = Schema.RequiredMode.REQUIRED)
        String sessionAccess) {

  /** application 결과를 외부 응답으로 변환합니다. */
  static CreateSessionResponse from(CreatedSessionResult result) {
    return new CreateSessionResponse(
        result.accessToken(),
        result.tokenType(),
        result.accessTokenExpiresAt(),
        result.refreshToken(),
        result.refreshTokenExpiresAt(),
        result.accountId(),
        result.roles(),
        result.sessionAccess().name());
  }
}
