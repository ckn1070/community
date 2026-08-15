package com.ronams.carmunity.auth.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.auth.internal.application.port.in.CreatePasswordSessionUseCase;
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

/** 비밀번호 기반 인증 session public API Controller입니다. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth/sessions")
public class CreatePasswordSessionController {

  private final CreatePasswordSessionUseCase createPasswordSessionUseCase;

  /**
   * email/password 로그인 요청을 command로 변환하고 usecase가 반환한 session 표현을 HTTP 응답으로 조립합니다.
   *
   * <p>이 endpoint는 access token을 받기 전 호출해야 하므로 Security allow-list에 포함됩니다.
   *
   * @param request 로그인 요청 body입니다.
   * @return access token과 refresh token 원문을 포함한 session 생성 결과입니다.
   */
  @Operation(summary = "비밀번호 로그인", description = "email과 password로 인증 session 생성을 요청합니다.")
  @ApiResponse(
      responseCode = "201",
      description = "session 생성 성공",
      content = @Content(schema = @Schema(implementation = CreateSessionResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "요청 validation 실패",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "자격증명 불일치",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "로그인할 수 없는 계정 상태",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping
  public ResponseEntity<CreateSessionResponse> createSession(
      @Valid @RequestBody CreatePasswordSessionRequest request) {
    return ResponseEntity.status(201)
        .body(
            CreateSessionResponse.from(createPasswordSessionUseCase.execute(request.toCommand())));
  }
}
