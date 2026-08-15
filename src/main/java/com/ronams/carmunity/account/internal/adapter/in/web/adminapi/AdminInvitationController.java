package com.ronams.carmunity.account.internal.adapter.in.web.adminapi;

import com.ronams.carmunity.account.internal.application.command.RevokeAdminInvitationCommand;
import com.ronams.carmunity.account.internal.application.port.in.AcceptAdminInvitationUseCase;
import com.ronams.carmunity.account.internal.application.port.in.InviteAdminUseCase;
import com.ronams.carmunity.account.internal.application.port.in.RevokeAdminInvitationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 초대 생성, 수락, 회수 API Controller입니다. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/invitations")
public class AdminInvitationController {

  private final InviteAdminUseCase inviteAdminUseCase;
  private final AcceptAdminInvitationUseCase acceptAdminInvitationUseCase;
  private final RevokeAdminInvitationUseCase revokeAdminInvitationUseCase;

  /** 최고 관리자가 새 관리자 초대를 생성합니다. */
  @Operation(summary = "관리자 초대 생성", description = "최고 관리자가 ROLE_ADMIN 초대를 생성합니다.")
  @ApiResponse(
      responseCode = "201",
      description = "초대 생성 성공",
      content = @Content(schema = @Schema(implementation = InviteAdminResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "요청 validation 실패 또는 허용되지 않은 role",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "기존 account email 중복",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping
  public ResponseEntity<InviteAdminResponse> inviteAdmin(
      Principal principal, @Valid @RequestBody InviteAdminRequest request) {
    InviteAdminResponse response =
        InviteAdminResponse.from(
            inviteAdminUseCase.execute(request.toCommand(principalId(principal))));
    return ResponseEntity.created(
            URI.create("/api/v1/admin/invitations/" + response.invitationId()))
        .body(response);
  }

  /** 초대받은 사용자가 public token으로 관리자 초대를 수락합니다. */
  @Operation(summary = "관리자 초대 수락", description = "초대 token으로 새 관리자 account를 생성합니다.")
  @ApiResponse(
      responseCode = "201",
      description = "초대 수락 성공",
      content = @Content(schema = @Schema(implementation = AcceptedAdminInvitationResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "요청 validation 실패, token 불일치, token 만료",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "초대 종료 또는 account/display name 중복",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/{invitationId}/acceptances")
  public ResponseEntity<AcceptedAdminInvitationResponse> acceptAdminInvitation(
      @PathVariable UUID invitationId, @Valid @RequestBody AcceptAdminInvitationRequest request) {
    AcceptedAdminInvitationResponse response =
        AcceptedAdminInvitationResponse.from(
            acceptAdminInvitationUseCase.execute(request.toCommand(invitationId)));
    return ResponseEntity.created(URI.create("/api/v1/accounts/" + response.accountId()))
        .body(response);
  }

  /** 최고 관리자가 아직 open 상태인 관리자 초대를 회수합니다. */
  @Operation(summary = "관리자 초대 회수", description = "아직 수락되지 않은 관리자 초대를 회수합니다.")
  @ApiResponse(responseCode = "204", description = "초대 회수 성공")
  @ApiResponse(
      responseCode = "404",
      description = "초대 없음",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "이미 종료된 초대",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @DeleteMapping("/{invitationId}")
  public ResponseEntity<Void> revokeAdminInvitation(
      Principal principal, @PathVariable UUID invitationId) {
    revokeAdminInvitationUseCase.execute(
        new RevokeAdminInvitationCommand(principalId(principal), invitationId));
    return ResponseEntity.noContent().build();
  }

  private UUID principalId(Principal principal) {
    return UUID.fromString(principal.getName());
  }
}
