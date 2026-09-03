package com.ronams.carmunity.community.internal.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronams.carmunity.community.internal.application.CommunityErrorCode;
import com.ronams.carmunity.community.internal.application.service.CommunityService;
import com.ronams.carmunity.shared.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class CommunityController {
  private static final String TITLE_FIELD = "title";

  private final CommunityService service;

  @Operation(summary = "자유게시판 게시글 목록", description = "자유게시판 게시글을 최신순으로 페이지 조회합니다.")
  @ApiResponse(responseCode = "200", description = "게시글 목록 조회 성공")
  @GetMapping("/boards/free/posts")
  public Map<String, Object> posts(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal) {
    return service.listPosts(page, size, actorOrNull(principal));
  }

  @Operation(summary = "자유게시판 게시글 작성", description = "인증된 사용자가 멱등성 키로 새 게시글을 작성합니다.")
  @ApiResponse(
      responseCode = "201",
      description = "게시글 작성 성공",
      headers = @Header(name = "Location", schema = @Schema(type = "string", format = "uri")))
  @PostMapping("/boards/free/posts")
  public ResponseEntity<Map<String, Object>> createPost(
      @RequestHeader("Idempotency-Key") UUID key,
      @RequestBody PostWriteRequest request,
      Principal principal) {
    Map<String, Object> result =
        service.createPost(actor(principal), key, request.title(), request.body());
    UUID id = (UUID) result.get("id");
    return ResponseEntity.created(URI.create("/api/v1/posts/" + id))
        .eTag(etag(result))
        .body(result);
  }

  @Operation(summary = "게시글 상세 조회", description = "게시글 식별자로 공개 게시글의 현재 내용을 조회합니다.")
  @ApiResponse(responseCode = "200", description = "게시글 상세 조회 성공")
  @GetMapping("/posts/{postId}")
  public ResponseEntity<Map<String, Object>> post(@PathVariable UUID postId, Principal principal) {
    Map<String, Object> result = service.post(postId);
    return ResponseEntity.ok().eTag(etag(result)).body(result);
  }

  @Operation(summary = "게시글 수정", description = "작성자가 현재 ETag와 일치하는 게시글의 제목 또는 본문을 수정합니다.")
  @ApiResponse(responseCode = "200", description = "게시글 수정 성공")
  @PatchMapping("/posts/{postId}")
  public ResponseEntity<Map<String, Object>> updatePost(
      @PathVariable UUID postId,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @RequestBody JsonNode request,
      Principal principal) {
    if (!request.isObject()
        || (!request.has(TITLE_FIELD) && !request.has("body"))
        || (request.has(TITLE_FIELD)
            && (request.get(TITLE_FIELD).isNull() || !request.get(TITLE_FIELD).isTextual()))
        || (request.has("body") && request.get("body").isNull())) {
      throw new ApiException(CommunityErrorCode.INVALID_REQUEST);
    }
    Map<String, Object> result =
        service.updatePost(
            actor(principal),
            postId,
            version(ifMatch),
            request.has(TITLE_FIELD) ? request.get(TITLE_FIELD).textValue() : null,
            request.get("body"));
    return ResponseEntity.ok().eTag(etag(result)).body(result);
  }

  @Operation(summary = "게시글 삭제", description = "작성자가 자신의 게시글을 삭제 상태로 전환합니다.")
  @ApiResponse(responseCode = "204", description = "게시글 삭제 성공")
  @DeleteMapping("/posts/{postId}")
  public ResponseEntity<Void> deletePost(@PathVariable UUID postId, Principal principal) {
    service.deleteContent(actor(principal), postId, "POST");
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "게시글 댓글 목록", description = "게시글의 최상위 댓글을 cursor 기반으로 조회합니다.")
  @ApiResponse(responseCode = "200", description = "댓글 목록 조회 성공")
  @GetMapping("/posts/{postId}/comments")
  public Map<String, Object> comments(
      @PathVariable UUID postId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "0") int size) {
    return service.comments(postId, null, cursor, size);
  }

  @Operation(summary = "댓글 답글 목록", description = "댓글에 달린 답글을 cursor 기반으로 조회합니다.")
  @ApiResponse(responseCode = "200", description = "답글 목록 조회 성공")
  @GetMapping("/comments/{commentId}/replies")
  public Map<String, Object> replies(
      @PathVariable UUID commentId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "0") int size) {
    return service.comments(null, commentId, cursor, size);
  }

  @Operation(summary = "댓글 작성", description = "인증된 사용자가 게시글에 댓글 또는 답글을 작성합니다.")
  @ApiResponse(
      responseCode = "201",
      description = "댓글 작성 성공",
      headers = @Header(name = "Location", schema = @Schema(type = "string", format = "uri")))
  @PostMapping("/posts/{postId}/comments")
  public ResponseEntity<Map<String, Object>> createComment(
      @PathVariable UUID postId,
      @RequestHeader("Idempotency-Key") UUID key,
      @RequestBody CommentWriteRequest request,
      Principal principal) {
    Map<String, Object> result =
        service.createComment(
            actor(principal), postId, key, request.body(), request.parentCommentId());
    UUID id = (UUID) result.get("id");
    return ResponseEntity.created(URI.create("/api/v1/comments/" + id))
        .eTag(etag(result))
        .body(result);
  }

  @Operation(summary = "댓글 삭제", description = "작성자가 자신의 댓글을 삭제 상태로 전환합니다.")
  @ApiResponse(responseCode = "204", description = "댓글 삭제 성공")
  @DeleteMapping("/comments/{commentId}")
  public ResponseEntity<Void> deleteComment(@PathVariable UUID commentId, Principal principal) {
    service.deleteContent(actor(principal), commentId, "COMMENT");
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "댓글 수정", description = "작성자가 현재 ETag와 일치하는 댓글 본문을 수정합니다.")
  @ApiResponse(responseCode = "200", description = "댓글 수정 성공")
  @PatchMapping("/comments/{commentId}")
  public ResponseEntity<Map<String, Object>> updateComment(
      @PathVariable UUID commentId,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @RequestBody CommentWriteRequest request,
      Principal principal) {
    Map<String, Object> result =
        service.updateComment(actor(principal), commentId, version(ifMatch), request.body());
    return ResponseEntity.ok().eTag(etag(result)).body(result);
  }

  @Operation(summary = "게시글·댓글 반응 등록", description = "인증된 사용자가 게시글 또는 댓글에 반응을 등록하거나 변경합니다.")
  @ApiResponse(responseCode = "200", description = "반응 등록 성공")
  @PutMapping({"/posts/{contentId}/reaction", "/comments/{contentId}/reaction"})
  public Map<String, Object> react(
      @PathVariable UUID contentId,
      @RequestBody ReactionWriteRequest request,
      Principal principal) {
    return service.react(actor(principal), contentId, request.type());
  }

  @Operation(summary = "게시글·댓글 반응 취소", description = "인증된 사용자가 게시글 또는 댓글에 남긴 반응을 취소합니다.")
  @ApiResponse(responseCode = "204", description = "반응 취소 성공")
  @DeleteMapping({"/posts/{contentId}/reaction", "/comments/{contentId}/reaction"})
  public ResponseEntity<Void> removeReaction(@PathVariable UUID contentId, Principal principal) {
    service.removeReaction(actor(principal), contentId);
    return ResponseEntity.noContent().build();
  }

  private UUID actor(Principal principal) {
    if (principal == null) throw new ApiException(CommunityErrorCode.CONTENT_FORBIDDEN);
    try {
      return UUID.fromString(principal.getName());
    } catch (IllegalArgumentException e) {
      throw new ApiException(CommunityErrorCode.CONTENT_FORBIDDEN);
    }
  }

  private UUID actorOrNull(Principal principal) {
    return principal == null ? null : actor(principal);
  }

  private long version(String value) {
    try {
      return Long.parseLong(value.replace("\"", ""));
    } catch (RuntimeException e) {
      throw new ApiException(CommunityErrorCode.PRECONDITION_FAILED);
    }
  }

  private String etag(Map<String, Object> value) {
    return "\"" + value.get("version") + "\"";
  }

  public record PostWriteRequest(String title, JsonNode body) {}

  public record CommentWriteRequest(String body, UUID parentCommentId) {}

  public record ReactionWriteRequest(String type) {}
}
