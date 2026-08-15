package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.account.internal.application.port.in.ConfirmPasswordResetUseCase;
import com.ronams.carmunity.account.internal.application.port.in.RequestPasswordResetUseCase;
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

/** 비밀번호 재설정 public API Controller입니다. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth/password-resets")
public class PasswordResetController {

  private final RequestPasswordResetUseCase requestPasswordResetUseCase;
  private final ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

  /**
   * reset token 발송 요청을 접수합니다.
   *
   * <p>account 존재 여부, 상태, cooldown 상태, provider 실패 세부사항을 public 응답에 노출하지 않습니다.
   */
  @Operation(summary = "비밀번호 재설정 요청", description = "비밀번호 재설정 메일 발송 요청을 접수합니다.")
  @ApiResponse(responseCode = "202", description = "요청 접수")
  @ApiResponse(
      responseCode = "400",
      description = "요청 validation 실패",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping
  public ResponseEntity<Void> requestPasswordReset(
      @Valid @RequestBody RequestPasswordResetRequest request) {
    requestPasswordResetUseCase.execute(request.toCommand());
    return ResponseEntity.accepted().build();
  }

  /**
   * reset token을 소비해 새 비밀번호를 저장합니다.
   *
   * <p>reset email 링크 자체는 상태를 바꾸지 않고, frontend가 사용자 입력 후 이 POST API를 호출합니다.
   */
  @Operation(summary = "비밀번호 재설정 완료", description = "reset token을 소비해 새 비밀번호를 저장합니다.")
  @ApiResponse(responseCode = "204", description = "재설정 완료")
  @ApiResponse(
      responseCode = "400",
      description = "요청 validation 실패 또는 token 오류",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/confirm")
  public ResponseEntity<Void> confirmPasswordReset(
      @Valid @RequestBody ConfirmPasswordResetRequest request) {
    confirmPasswordResetUseCase.execute(request.toCommand());
    return ResponseEntity.noContent().build();
  }
}
