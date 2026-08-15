package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.account.internal.application.port.in.ConfirmMemberEmailVerificationUseCase;
import com.ronams.carmunity.account.internal.application.port.in.RequestMemberEmailVerificationUseCase;
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

/** 일반 회원 이메일 인증 public API Controller입니다. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth/verifications/email")
public class MemberEmailVerificationController {

  private final RequestMemberEmailVerificationUseCase requestMemberEmailVerificationUseCase;
  private final ConfirmMemberEmailVerificationUseCase confirmMemberEmailVerificationUseCase;

  /**
   * 인증 메일 발송 요청을 접수합니다.
   *
   * <p>account 존재 여부, 이미 인증된 상태, cooldown 상태, provider 실패 세부사항을 public 응답에 노출하지 않습니다.
   */
  @Operation(summary = "이메일 인증 요청", description = "일반 회원 이메일 인증 메일 발송 요청을 접수합니다.")
  @ApiResponse(responseCode = "202", description = "요청 접수")
  @ApiResponse(
      responseCode = "400",
      description = "요청 validation 실패",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping
  public ResponseEntity<Void> requestVerification(
      @Valid @RequestBody RequestMemberEmailVerificationRequest request) {
    requestMemberEmailVerificationUseCase.execute(request.toCommand());
    return ResponseEntity.accepted().build();
  }

  /**
   * 이메일 인증 token을 소비해 account를 활성화합니다.
   *
   * <p>메일 링크 자체는 상태를 바꾸지 않고, frontend가 사용자 동작 후 이 POST API를 호출합니다.
   */
  @Operation(summary = "이메일 인증 완료", description = "이메일 인증 token을 소비해 계정을 활성화합니다.")
  @ApiResponse(responseCode = "204", description = "인증 완료")
  @ApiResponse(
      responseCode = "400",
      description = "요청 validation 실패 또는 token 오류",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/confirm")
  public ResponseEntity<Void> confirmVerification(
      @Valid @RequestBody ConfirmMemberEmailVerificationRequest request) {
    confirmMemberEmailVerificationUseCase.execute(request.toCommand());
    return ResponseEntity.noContent().build();
  }
}
