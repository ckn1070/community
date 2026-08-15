package com.ronams.carmunity.auth.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.auth.internal.application.port.in.LogoutUseCase;
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

/** 인증 로그아웃 public API Controller입니다. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth/logout")
public class LogoutController {

  private final LogoutUseCase logoutUseCase;

  /**
   * refresh token을 로그아웃 사유로 폐기하고 body 없는 204로 응답합니다.
   *
   * <p>이 endpoint는 access token이 이미 만료됐거나 클라이언트가 폐기한 뒤에도 호출되어야 하므로 Security allow-list에 포함됩니다.
   * refresh token의 존재 여부, 만료 여부, 폐기 여부는 응답으로 노출하지 않습니다.
   */
  @Operation(summary = "로그아웃", description = "refresh token을 폐기해 인증 session을 종료합니다.")
  @ApiResponse(responseCode = "204", description = "로그아웃 처리 완료")
  @ApiResponse(
      responseCode = "400",
      description = "요청 validation 실패",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping
  public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
    logoutUseCase.execute(request.toCommand());
    return ResponseEntity.noContent().build();
  }
}
