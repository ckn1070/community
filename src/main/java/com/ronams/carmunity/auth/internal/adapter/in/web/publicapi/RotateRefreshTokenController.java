package com.ronams.carmunity.auth.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.auth.internal.application.port.in.RotateRefreshTokenUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** refresh token 재발급 public API Controller입니다. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth/sessions/refresh")
public class RotateRefreshTokenController {

  private final RotateRefreshTokenUseCase rotateRefreshTokenUseCase;

  /**
   * refresh token을 rotation하고 새 access token과 refresh token을 반환합니다.
   *
   * <p>이 endpoint는 access token 만료 이후에도 호출되어야 하므로 Security allow-list에 포함됩니다. refresh token 검증 실패
   * 사유는 보안상 구체적으로 노출하지 않습니다.
   */
  @Operation(
      summary = "refresh token 재발급",
      description = "refresh token을 rotation하고 새 token 묶음을 발급합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "refresh token 재발급 성공",
      content = @Content(schema = @Schema(implementation = RotateRefreshTokenResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "요청 validation 실패",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "refresh token 유효성 실패",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping
  public ResponseEntity<RotateRefreshTokenResponse> rotate(
      @Valid @RequestBody RotateRefreshTokenRequest request) {
    return ResponseEntity.ok(
        RotateRefreshTokenResponse.from(rotateRefreshTokenUseCase.execute(request.toCommand())));
  }
}
