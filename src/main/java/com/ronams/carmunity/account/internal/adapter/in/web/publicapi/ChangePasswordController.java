package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.account.internal.application.port.in.ChangePasswordUseCase;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 인증된 사용자의 비밀번호 변경 public API Controller입니다. */
@RequiredArgsConstructor
@RestController
public class ChangePasswordController {

  private final ChangePasswordUseCase changePasswordUseCase;

  /**
   * 인증된 사용자의 current password를 확인하고 새 비밀번호로 변경합니다.
   *
   * <p>성공 후 기존 refresh token family는 폐기되며, 사용자는 새 비밀번호로 다시 로그인해야 합니다.
   *
   * @param principal JWT subject를 account public id로 노출하는 인증 principal입니다.
   * @param request 비밀번호 변경 요청 body입니다.
   * @return body 없는 204 응답입니다.
   */
  @Operation(summary = "비밀번호 변경", description = "인증된 사용자의 비밀번호를 변경합니다.")
  @ApiResponse(responseCode = "204", description = "비밀번호 변경 성공")
  @ApiResponse(
      responseCode = "400",
      description = "요청 validation 실패",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "현재 비밀번호 불일치",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping("/api/v1/auth/password")
  public ResponseEntity<Void> changePassword(
      Principal principal, @Valid @RequestBody ChangePasswordRequest request) {
    changePasswordUseCase.execute(request.toCommand(accountPublicId(principal)));
    return ResponseEntity.noContent().build();
  }

  /** Security principal name은 access token subject인 account public id여야 합니다. */
  private UUID accountPublicId(Principal principal) {
    try {
      return UUID.fromString(principal.getName());
    } catch (RuntimeException exception) {
      throw new ApiException(CommonErrorCode.INVALID_REQUEST, exception);
    }
  }
}
