package com.ronams.carmunity.moderation.internal.adapter.in.web;

import com.ronams.carmunity.moderation.internal.application.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class ModerationController {
  private final ModerationService service;

  @Operation(summary = "게시글·댓글 신고", description = "인증된 사용자가 게시글 또는 댓글을 신고합니다.")
  @ApiResponse(
      responseCode = "201",
      description = "신고 접수 성공",
      headers = @Header(name = "Location", schema = @Schema(type = "string", format = "uri")))
  @PostMapping({"/posts/{targetId}/reports", "/comments/{targetId}/reports"})
  public ResponseEntity<Map<String, Object>> report(
      @PathVariable UUID targetId, Authentication authentication, HttpServletRequest request) {
    String kind = request.getRequestURI().contains("/posts/") ? "POST" : "COMMENT";
    Map<String, Object> result = service.report(actor(authentication), targetId, kind);
    return ResponseEntity.created(URI.create("/api/v1/admin/community/reports/" + result.get("id")))
        .body(result);
  }

  @Operation(summary = "관리자 신고 목록", description = "관리자가 처리 상태별 신고를 cursor 기반으로 조회합니다.")
  @ApiResponse(responseCode = "200", description = "신고 목록 조회 성공")
  @GetMapping("/admin/community/reports")
  public Map<String, Object> reports(
      @RequestParam(defaultValue = "OPEN") String status,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    requireAdmin(authentication);
    return service.list(status, cursor, size);
  }

  @Operation(summary = "관리자 신고 상세", description = "관리자가 신고의 대상과 현재 처리 상태를 조회합니다.")
  @ApiResponse(responseCode = "200", description = "신고 상세 조회 성공")
  @GetMapping("/admin/community/reports/{reportId}")
  public ResponseEntity<Map<String, Object>> report(
      @PathVariable UUID reportId, Authentication authentication) {
    requireAdmin(authentication);
    Map<String, Object> result = service.detail(reportId);
    return ResponseEntity.ok().eTag("\"" + result.get("version") + "\"").body(result);
  }

  @Operation(summary = "관리자 신고 처리", description = "관리자가 현재 ETag와 일치하는 신고에 처리 결과를 기록합니다.")
  @ApiResponse(responseCode = "200", description = "신고 처리 성공")
  @PatchMapping("/admin/community/reports/{reportId}")
  public ResponseEntity<Map<String, Object>> resolve(
      @PathVariable UUID reportId,
      @RequestHeader(HttpHeaders.IF_MATCH) String tag,
      @RequestBody ResolutionRequest request,
      Authentication authentication) {
    requireAdmin(authentication);
    long version;
    try {
      version = Long.parseLong(tag.replace("\"", ""));
    } catch (RuntimeException exception) {
      throw new com.ronams.carmunity.shared.error.ApiException(
          com.ronams.carmunity.moderation.internal.application.ModerationErrorCode
              .PRECONDITION_FAILED);
    }
    Map<String, Object> result =
        service.resolve(actor(authentication), reportId, version, request.resolution());
    return ResponseEntity.ok().eTag("\"" + result.get("version") + "\"").body(result);
  }

  private void requireAdmin(Authentication authentication) {
    boolean allowed =
        authentication != null
            && authentication.getAuthorities().stream()
                .anyMatch(
                    authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority())
                            || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    if (!allowed) throw new AccessDeniedException("관리자 권한이 필요합니다.");
  }

  private UUID actor(Authentication authentication) {
    return UUID.fromString(authentication.getName());
  }

  public record ResolutionRequest(String resolution) {}
}
