package com.ronams.carmunity.moderation.internal.application;

import com.ronams.carmunity.community.api.CommunityContentVisibility;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.pagination.CursorTokens;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class ModerationService {
  private static final String ACTOR_PARAMETER = "actor";
  private static final String HIDE_CONTENT = "HIDE_CONTENT";
  private static final String REPORT_PAGE_AFTER_CURSOR =
      "SELECT id AS cursor_id,public_id AS id,target_content_public_id,target_kind,status,created_at,resolved_at,version FROM moderation_report WHERE status=:status AND (created_at,id)<(:createdAt,:internalId) ORDER BY created_at DESC,id DESC LIMIT :limit";
  private static final String REPORT_PAGE_FIRST =
      "SELECT id AS cursor_id,public_id AS id,target_content_public_id,target_kind,status,created_at,resolved_at,version FROM moderation_report WHERE status=:status ORDER BY created_at DESC,id DESC LIMIT :limit";
  private static final String TARGET_PARAMETER = "target";
  private final JdbcClient jdbc;
  private final CommunityContentVisibility visibility;
  private final CursorTokens cursors;

  @Transactional
  public Map<String, Object> report(UUID actor, UUID target, String kind) {
    visibility.requireVisible(target, kind);
    UUID id = UUID.randomUUID();
    try {
      jdbc.sql(
              "INSERT INTO moderation_report(public_id,target_content_public_id,target_kind,reporter_account_public_id,status) VALUES(:id,:target,:kind,:actor,'OPEN')")
          .param("id", id)
          .param(TARGET_PARAMETER, target)
          .param("kind", kind)
          .param(ACTOR_PARAMETER, actor)
          .update();
    } catch (DuplicateKeyException exception) {
      throw new ApiException(ModerationErrorCode.REPORT_ALREADY_OPEN, exception);
    }
    return detailView(id);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> list(String status, String cursor, int size) {
    if (size < 1 || size > 50) throw new ApiException(ModerationErrorCode.INVALID_RESOLUTION);
    CursorTokens.Position position = decode(cursor);
    var query =
        jdbc.sql(position == null ? REPORT_PAGE_FIRST : REPORT_PAGE_AFTER_CURSOR)
            .param("status", status)
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
              "REPORT_DESC", instant(last.get("created_at")), (Long) last.get("cursor_id"));
    }
    rows.forEach(row -> row.remove("cursor_id"));
    rows.forEach(this::camelCase);
    var result = new java.util.LinkedHashMap<String, Object>();
    result.put("items", rows);
    result.put("nextCursor", next);
    return result;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> detail(UUID id) {
    return detailView(id);
  }

  private Map<String, Object> detailView(UUID id) {
    Map<String, Object> result =
        jdbc
            .sql(
                "SELECT public_id AS id,target_content_public_id,target_kind,status,created_at,resolved_at,version FROM moderation_report WHERE public_id=:id")
            .param("id", id)
            .query()
            .listOfRows()
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(ModerationErrorCode.REPORT_NOT_FOUND));
    UUID targetId = (UUID) result.get("target_content_public_id");
    String targetKind = (String) result.get("target_kind");
    camelCase(result);
    result.put("target", visibility.moderationSnapshot(targetId, targetKind));
    return result;
  }

  @Transactional
  public Map<String, Object> resolve(UUID actor, UUID id, long version, String resolution) {
    if (!HIDE_CONTENT.equals(resolution) && !"DISMISS".equals(resolution))
      throw new ApiException(ModerationErrorCode.INVALID_RESOLUTION);
    Map<String, Object> report = detailView(id);
    String status = HIDE_CONTENT.equals(resolution) ? "CONTENT_HIDDEN" : "DISMISSED";
    int changed =
        jdbc.sql(
                "UPDATE moderation_report SET status=:status,resolved_by_account_public_id=:actor,resolved_at=NOW(),updated_at=NOW(),version=version+1 WHERE public_id=:id AND status='OPEN' AND version=:version")
            .param("status", status)
            .param(ACTOR_PARAMETER, actor)
            .param("id", id)
            .param("version", version)
            .update();
    if (changed == 0) throw new ApiException(ModerationErrorCode.PRECONDITION_FAILED);
    if (HIDE_CONTENT.equals(resolution)) {
      UUID target = (UUID) report.get("targetContentPublicId");
      visibility.hide(target);
      jdbc.sql(
              "UPDATE moderation_report SET status='CONTENT_HIDDEN',resolved_by_account_public_id=:actor,resolved_at=NOW(),updated_at=NOW(),version=version+1 WHERE target_content_public_id=:target AND status='OPEN'")
          .param(ACTOR_PARAMETER, actor)
          .param(TARGET_PARAMETER, target)
          .update();
    }
    return detailView(id);
  }

  private CursorTokens.Position decode(String cursor) {
    if (cursor == null) return null;
    try {
      return cursors.decode(cursor, "REPORT_DESC");
    } catch (IllegalArgumentException exception) {
      throw new ApiException(ModerationErrorCode.INVALID_RESOLUTION);
    }
  }

  private Instant instant(Object value) {
    if (value instanceof Instant instant) return instant;
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
    return ((java.time.OffsetDateTime) value).toInstant();
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
}
