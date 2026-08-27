package com.ronams.carmunity.auth.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.auth.internal.application.result.RotatedRefreshTokenResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * refresh token 재발급 성공 응답입니다.
 *
 * <p>refresh token hash, token family id, 내부 account id는 응답에 포함하지 않습니다.
 *
 * @param accessToken 새 access token 원문입니다.
 * @param tokenType Authorization header에 사용할 token type입니다.
 * @param accessTokenExpiresAt 새 access token 만료 시각입니다.
 * @param refreshToken 새 refresh token 원문입니다.
 * @param refreshTokenExpiresAt 새 refresh token 만료 시각입니다.
 * @param memberId 외부 API에 노출할 회원 식별자입니다.
 * @param roles access token에 반영된 role code 목록입니다.
 */
@Schema(description = "refresh token 재발급 성공 응답")
public record RotateRefreshTokenResponse(
    @Schema(description = "새 access token 원문", requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,
    @Schema(description = "Authorization scheme", requiredMode = Schema.RequiredMode.REQUIRED)
        String tokenType,
    @Schema(description = "새 access token 만료 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant accessTokenExpiresAt,
    @Schema(description = "새 refresh token 원문", requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken,
    @Schema(description = "새 refresh token 만료 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant refreshTokenExpiresAt,
    @Schema(description = "회원 public id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID memberId,
    @Schema(
            description = "access token에 포함된 role code 목록",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> roles) {

  /**
   * application 결과를 외부 응답으로 변환합니다.
   *
   * <p>저장용 hash와 token family 정보는 application 결과에 포함하지 않아 이 응답 경계에서도 노출되지 않습니다.
   */
  static RotateRefreshTokenResponse from(RotatedRefreshTokenResult result) {
    return new RotateRefreshTokenResponse(
        result.accessToken(),
        result.tokenType(),
        result.accessTokenExpiresAt(),
        result.refreshToken(),
        result.refreshTokenExpiresAt(),
        result.memberId(),
        result.roles());
  }
}
