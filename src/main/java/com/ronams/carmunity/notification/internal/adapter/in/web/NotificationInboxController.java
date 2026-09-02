package com.ronams.carmunity.notification.internal.adapter.in.web;

import com.ronams.carmunity.notification.internal.application.NotificationErrorCode;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.pagination.CursorTokens;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class NotificationInboxController {
  private static final String ACTOR_PARAMETER = "actor";
  private static final String NOTIFICATION_PAGE_AFTER_CURSOR =
      "SELECT id AS cursor_id,public_id AS id,type,source_content_public_id,post_public_id,created_at,read_at,version FROM notification_inbox WHERE recipient_account_public_id=:actor AND (created_at,id)<(:createdAt,:internalId) ORDER BY created_at DESC,id DESC LIMIT :limit";
  private static final String NOTIFICATION_PAGE_FIRST =
      "SELECT id AS cursor_id,public_id AS id,type,source_content_public_id,post_public_id,created_at,read_at,version FROM notification_inbox WHERE recipient_account_public_id=:actor ORDER BY created_at DESC,id DESC LIMIT :limit";
  private final JdbcClient jdbc;
  private final CursorTokens cursors;

  @GetMapping
  @Transactional(readOnly = true)
  public Map<String, Object> list(
      Principal principal,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    if (size < 1 || size > 50) throw invalidRequest();
    CursorTokens.Position position = decode(cursor);
    var query =
        jdbc.sql(position == null ? NOTIFICATION_PAGE_FIRST : NOTIFICATION_PAGE_AFTER_CURSOR)
            .param(ACTOR_PARAMETER, actor(principal))
            .param("limit", size + 1);
    if (position != null)
      query =
          query
              .param("createdAt", java.sql.Timestamp.from(position.createdAt()))
              .param("internalId", position.internalId());
    var rows = new ArrayList<>(query.query().listOfRows());
    boolean hasNext = rows.size() > size;
    if (hasNext) rows.remove(rows.size() - 1);
    String next = null;
    if (hasNext) {
      var last = rows.get(rows.size() - 1);
      next =
          cursors.encode(
              "NOTIFICATION_DESC", instant(last.get("created_at")), (Long) last.get("cursor_id"));
    }
    rows.forEach(row -> row.remove("cursor_id"));
    rows.forEach(this::camelCase);
    var result = new java.util.LinkedHashMap<String, Object>();
    result.put("items", rows);
    result.put("nextCursor", next);
    return result;
  }

  @PatchMapping("/{notificationId}")
  @Transactional
  public Map<String, Object> read(
      @PathVariable UUID notificationId, @RequestBody ReadRequest request, Principal principal) {
    if (!request.read()) throw invalidRequest();
    UUID actor = actor(principal);
    jdbc.sql(
            "UPDATE notification_inbox SET read_at=COALESCE(read_at,NOW()),updated_at=NOW(),version=CASE WHEN read_at IS NULL THEN version+1 ELSE version END WHERE public_id=:id AND recipient_account_public_id=:actor")
        .param("id", notificationId)
        .param(ACTOR_PARAMETER, actor)
        .update();
    Map<String, Object> result =
        jdbc
            .sql(
                "SELECT public_id AS id,type,source_content_public_id,post_public_id,created_at,read_at,version FROM notification_inbox WHERE public_id=:id AND recipient_account_public_id=:actor")
            .param("id", notificationId)
            .param(ACTOR_PARAMETER, actor)
            .query()
            .listOfRows()
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    camelCase(result);
    return result;
  }

  private CursorTokens.Position decode(String cursor) {
    if (cursor == null) return null;
    try {
      return cursors.decode(cursor, "NOTIFICATION_DESC");
    } catch (IllegalArgumentException exception) {
      throw invalidRequest();
    }
  }

  private Instant instant(Object value) {
    if (value instanceof Instant instant) return instant;
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
    return ((java.time.OffsetDateTime) value).toInstant();
  }

  private ApiException invalidRequest() {
    return new ApiException(NotificationErrorCode.INVALID_REQUEST);
  }

  private void camelCase(Map<String, Object> row) {
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    row.forEach(
        (key, value) -> {
          String[] parts = key.split("_");
          StringBuilder name = new StringBuilder(parts[0]);
          for (int index = 1; index < parts.length; index++) {
            name.append(Character.toUpperCase(parts[index].charAt(0)))
                .append(parts[index].substring(1));
          }
          result.put(name.toString(), value);
        });
    row.clear();
    row.putAll(result);
  }

  private UUID actor(Principal principal) {
    return UUID.fromString(principal.getName());
  }

  public record ReadRequest(boolean read) {}
}
