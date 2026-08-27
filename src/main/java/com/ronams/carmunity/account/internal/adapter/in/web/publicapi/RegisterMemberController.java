package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.account.internal.application.port.in.RegisterMemberAccountUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 일반 회원 자체 가입 public API Controller입니다. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class RegisterMemberController {

  private final RegisterMemberAccountUseCase registerMemberAccountUseCase;

  /**
   * 미인증 사용자가 일반 회원 account를 생성합니다.
   *
   * <p>이 endpoint는 Security allow-list에 포함된 public business API이며, 자동 로그인이나 token 발급은 수행하지 않습니다.
   *
   * @param request 가입 요청 body입니다.
   * @return 생성된 member 리소스 표현과 Location header입니다.
   */
  @Operation(summary = "일반 회원 가입", description = "미인증 사용자가 일반 회원 계정을 생성합니다.")
  @ApiResponse(
      responseCode = "201",
      description = "회원 가입 성공",
      headers =
          @Header(
              name = "Location",
              description = "생성된 member resource URI",
              schema = @Schema(type = "string", format = "uri")),
      content = @Content(schema = @Schema(implementation = RegisterMemberResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "요청 validation 실패",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "email 또는 표시 이름 중복",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "500",
      description = "필수 role seed 누락 또는 예상하지 못한 오류",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping
  public ResponseEntity<RegisterMemberResponse> registerMember(
      @Valid @RequestBody RegisterMemberRequest request) {
    RegisterMemberResponse response =
        RegisterMemberResponse.from(registerMemberAccountUseCase.execute(request.toCommand()));
    return ResponseEntity.created(URI.create("/api/v1/members/" + response.memberId()))
        .body(response);
  }
}
