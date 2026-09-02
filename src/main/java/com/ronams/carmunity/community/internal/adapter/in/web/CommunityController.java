package com.ronams.carmunity.community.internal.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronams.carmunity.community.internal.application.CommunityErrorCode;
import com.ronams.carmunity.community.internal.application.service.CommunityService;
import com.ronams.carmunity.shared.error.ApiException;
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

  @GetMapping("/boards/free/posts")
  public Map<String, Object> posts(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal) {
    return service.listPosts(page, size, actorOrNull(principal));
  }

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

  @GetMapping("/posts/{postId}")
  public ResponseEntity<Map<String, Object>> post(@PathVariable UUID postId, Principal principal) {
    Map<String, Object> result = service.post(postId, actorOrNull(principal));
    return ResponseEntity.ok().eTag(etag(result)).body(result);
  }

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

  @DeleteMapping("/posts/{postId}")
  public ResponseEntity<Void> deletePost(@PathVariable UUID postId, Principal principal) {
    service.deleteContent(actor(principal), postId, "POST");
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/posts/{postId}/comments")
  public Map<String, Object> comments(
      @PathVariable UUID postId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "0") int size) {
    return service.comments(postId, null, cursor, size);
  }

  @GetMapping("/comments/{commentId}/replies")
  public Map<String, Object> replies(
      @PathVariable UUID commentId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "0") int size) {
    return service.comments(null, commentId, cursor, size);
  }

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

  @DeleteMapping("/comments/{commentId}")
  public ResponseEntity<Void> deleteComment(@PathVariable UUID commentId, Principal principal) {
    service.deleteContent(actor(principal), commentId, "COMMENT");
    return ResponseEntity.noContent().build();
  }

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

  @PutMapping({"/posts/{contentId}/reaction", "/comments/{contentId}/reaction"})
  public Map<String, Object> react(
      @PathVariable UUID contentId,
      @RequestBody ReactionWriteRequest request,
      Principal principal) {
    return service.react(actor(principal), contentId, request.type());
  }

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
