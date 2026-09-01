package com.ronams.carmunity.community.internal.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronams.carmunity.account.api.CommunityAuthorProfileLookup;
import com.ronams.carmunity.community.api.CommunityContentVisibility;
import com.ronams.carmunity.community.internal.application.CommunityErrorCode;
import com.ronams.carmunity.community.internal.application.CursorCodec;
import com.ronams.carmunity.community.internal.domain.PostBody;
import com.ronams.carmunity.community.internal.domain.PostTitle;
import com.ronams.carmunity.media.api.PostMediaUsage;
import com.ronams.carmunity.notification.api.CommunityConversationNotification;
import com.ronams.carmunity.shared.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "carmunity.community", name = "enabled", havingValue = "true")
public class CommunityService implements CommunityContentVisibility {

  private static final UUID FREE_BOARD_ID = UUID.fromString("ed710d1c-91f1-4266-a035-bba33a7f52cb");
  private final JdbcClient jdbc;
  private final PostMediaUsage mediaUsage;
  private final CommunityConversationNotification notifications;
  private final CursorCodec cursorCodec;
  private final CommunityAuthorProfileLookup authorProfiles;

  @Transactional
  public Map<String, Object> createPost(UUID actor, UUID key, String rawTitle, JsonNode rawBody) {
    PostTitle title = title(rawTitle);
    PostBody body = body(rawBody);
    String hash = hash("POST:" + title.value() + ":" + canonical(body.canonical()));
    Map<String, Object> existing = existingCreation(actor, key);
    if (existing != null) {
      if (!hash.equals(existing.get("requestHash")))
        throw error(CommunityErrorCode.IDEMPOTENCY_KEY_REUSED);
      return post((UUID) existing.get("id"), actor);
    }
    UUID id = UUID.randomUUID();
    Long contentId =
        jdbc.sql(
                """
            INSERT INTO community_content
              (public_id, kind, account_public_id, board_id, creation_request_id, creation_request_hash)
            SELECT :id, 'POST', :actor, id, :requestId, :hash FROM community_board WHERE public_id=:board
            RETURNING id
            """)
            .param("id", id)
            .param("actor", actor)
            .param("requestId", key)
            .param("hash", hash)
            .param("board", FREE_BOARD_ID)
            .query(Long.class)
            .single();
    jdbc.sql(
            "INSERT INTO community_post(content_id,title,body_preview,body_schema_version,body) VALUES (:id,:title,:preview,1,CAST(:body AS JSONB))")
        .param("id", contentId)
        .param("title", title.value())
        .param("preview", body.preview())
        .param("body", canonical(body.canonical()))
        .update();
    mediaUsage.replace(actor, id, body.mediaIds());
    return post(id, actor);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> listPosts(int page, int size, UUID viewer) {
    if (page < 0 || size < 1 || size > 50)
      throw error(CommunityErrorCode.INVALID_PAGINATION_PARAMETER);
    long total =
        jdbc.sql(
                "SELECT COUNT(*) FROM community_content c JOIN community_board b ON b.id=c.board_id WHERE b.slug='free' AND c.kind='POST' AND c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL")
            .query(Long.class)
            .single();
    List<Map<String, Object>> items =
        jdbc.sql(
                """
            SELECT c.public_id AS id, c.account_public_id AS author_account_id, b.slug AS board, p.title, p.body_preview AS preview,
                   c.created_at, c.edited_at, c.version,
                   COUNT(r.id) FILTER (WHERE r.reaction_type='LIKE') AS like_count,
                   COUNT(r.id) FILTER (WHERE r.reaction_type='DISLIKE') AS dislike_count
            FROM community_content c JOIN community_board b ON b.id=c.board_id
            JOIN community_post p ON p.content_id=c.id
            LEFT JOIN community_reaction r ON r.content_id=c.id
            WHERE b.slug='free' AND c.kind='POST' AND c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL
            GROUP BY c.id,b.slug,p.title,p.body_preview ORDER BY c.created_at DESC,c.id DESC LIMIT :size OFFSET :offset
            """)
            .param("size", size)
            .param("offset", (long) page * size)
            .query()
            .listOfRows();
    attachAuthors(items);
    items.forEach(this::camelCase);
    int pages = total == 0 ? 0 : (int) ((total + size - 1) / size);
    return Map.of(
        "items",
        items,
        "number",
        page,
        "size",
        size,
        "totalElements",
        total,
        "totalPages",
        pages,
        "first",
        page == 0,
        "last",
        pages == 0 || page >= pages - 1);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> post(UUID id, UUID viewer) {
    return jdbc
        .sql(
            """
            SELECT c.public_id AS id,b.slug AS board,
              CASE WHEN c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL THEN p.title END AS title,
              CASE WHEN c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL THEN p.body END AS body,
              CASE WHEN c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL THEN c.account_public_id END AS author_account_id,
              CASE WHEN c.author_deleted_at IS NOT NULL THEN 'AUTHOR_DELETED' WHEN c.moderator_hidden_at IS NOT NULL THEN 'MODERATOR_HIDDEN' ELSE 'VISIBLE' END AS visibility,
              c.created_at,c.edited_at,c.version,
              c.author_deleted_at,c.moderator_hidden_at,
              COUNT(r.id) FILTER(WHERE r.reaction_type='LIKE') AS like_count,
              COUNT(r.id) FILTER(WHERE r.reaction_type='DISLIKE') AS dislike_count
            FROM community_content c JOIN community_board b ON b.id=c.board_id JOIN community_post p ON p.content_id=c.id
            LEFT JOIN community_reaction r ON r.content_id=c.id WHERE c.public_id=:id AND c.kind='POST'
              AND ((c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL)
                OR EXISTS(SELECT 1 FROM community_content child WHERE child.post_content_id=c.id AND child.author_deleted_at IS NULL AND child.moderator_hidden_at IS NULL))
            GROUP BY c.id,b.slug,p.title,p.body
            """)
        .param("id", id)
        .query()
        .listOfRows()
        .stream()
        .findFirst()
        .map(
            row -> {
              attachAuthors(List.of(row));
              redactTombstone(row);
              camelCase(row);
              return row;
            })
        .orElseThrow(() -> error(CommunityErrorCode.CONTENT_NOT_FOUND));
  }

  @Transactional
  public Map<String, Object> updatePost(
      UUID actor, UUID id, long expectedVersion, String rawTitle, JsonNode rawBody) {
    if (rawTitle == null && rawBody == null) throw error(CommunityErrorCode.INVALID_REQUEST);
    Map<String, Object> current = ownedActive(id, actor, "POST");
    PostTitle title = rawTitle == null ? null : title(rawTitle);
    PostBody body = rawBody == null ? null : body(rawBody);
    Map<String, Object> stored =
        jdbc.sql(
                "SELECT title,(CAST(:body AS TEXT) IS NULL OR body=CAST(:body AS JSONB)) AS body_same FROM community_post WHERE content_id=:id")
            .param("body", body == null ? null : canonical(body.canonical()))
            .param("id", current.get("internal_id"))
            .query()
            .singleRow();
    boolean titleSame = title == null || title.value().equals(stored.get("title"));
    boolean bodySame = Boolean.TRUE.equals(stored.get("body_same"));
    if (titleSame && bodySame) return post(id, actor);
    int changed =
        jdbc.sql(
                """
            UPDATE community_content SET edited_at=NOW(),updated_at=NOW(),version=version+1
            WHERE id=:internal AND version=:version
            """)
            .param("internal", current.get("internal_id"))
            .param("version", expectedVersion)
            .update();
    if (changed == 0) throw error(CommunityErrorCode.PRECONDITION_FAILED);
    jdbc.sql(
            "UPDATE community_post SET title=COALESCE(:title,title),body_preview=COALESCE(:preview,body_preview),body=COALESCE(CAST(:body AS JSONB),body) WHERE content_id=:id")
        .param("title", title == null ? null : title.value())
        .param("preview", body == null ? null : body.preview())
        .param("body", body == null ? null : canonical(body.canonical()))
        .param("id", current.get("internal_id"))
        .update();
    if (body != null) mediaUsage.replace(actor, id, body.mediaIds());
    return post(id, actor);
  }

  @Transactional
  public void deleteContent(UUID actor, UUID id, String kind) {
    Map<String, Object> current = owned(id, actor, kind);
    jdbc.sql(
            "UPDATE community_content SET author_deleted_at=COALESCE(author_deleted_at,NOW()),updated_at=NOW() WHERE id=:id")
        .param("id", current.get("internal_id"))
        .update();
    if ("POST".equals(kind)) mediaUsage.release(id);
  }

  @Transactional
  public Map<String, Object> createComment(
      UUID actor, UUID postId, UUID key, String rawBody, UUID parentId) {
    String text = rawBody == null ? "" : rawBody.strip().replaceAll("\\s+", " ");
    if (text.isEmpty() || text.codePointCount(0, text.length()) > 2000)
      throw error(CommunityErrorCode.INVALID_REQUEST);
    String requestHash = hash("COMMENT:" + text + ":" + parentId);
    Map<String, Object> existing = existingCreation(actor, key);
    if (existing != null) {
      if (!requestHash.equals(existing.get("requestHash")))
        throw error(CommunityErrorCode.IDEMPOTENCY_KEY_REUSED);
      return comment((UUID) existing.get("id"));
    }
    Map<String, Object> post =
        jdbc
            .sql(
                "SELECT id AS internal_id,account_public_id AS author FROM community_content WHERE public_id=:id AND kind='POST' AND author_deleted_at IS NULL AND moderator_hidden_at IS NULL")
            .param("id", postId)
            .query()
            .listOfRows()
            .stream()
            .findFirst()
            .orElseThrow(() -> error(CommunityErrorCode.CONTENT_NOT_FOUND));
    Long parentInternal = null;
    Long rootInternal = null;
    UUID parentAuthor = null;
    if (parentId != null) {
      Map<String, Object> parent =
          jdbc
              .sql(
                  "SELECT id,account_public_id,COALESCE(thread_root_comment_id,id) AS root_id FROM community_content WHERE public_id=:id AND kind='COMMENT' AND post_content_id=:post AND author_deleted_at IS NULL AND moderator_hidden_at IS NULL")
              .param("id", parentId)
              .param("post", post.get("internal_id"))
              .query()
              .listOfRows()
              .stream()
              .findFirst()
              .orElseThrow(() -> error(CommunityErrorCode.CONTENT_NOT_FOUND));
      parentInternal = (Long) parent.get("id");
      rootInternal = (Long) parent.get("root_id");
      parentAuthor = (UUID) parent.get("account_public_id");
    }
    UUID id = UUID.randomUUID();
    Long internal =
        jdbc.sql(
                "INSERT INTO community_content(public_id,kind,account_public_id,post_content_id,parent_comment_id,thread_root_comment_id,creation_request_id,creation_request_hash) VALUES (:id,'COMMENT',:actor,:post,:parent,:root,:request,:hash) RETURNING id")
            .param("id", id)
            .param("actor", actor)
            .param("post", post.get("internal_id"))
            .param("parent", parentInternal)
            .param("root", rootInternal)
            .param("request", key)
            .param("hash", requestHash)
            .query(Long.class)
            .single();
    jdbc.sql("INSERT INTO community_comment(content_id,body) VALUES (:id,:body)")
        .param("id", internal)
        .param("body", text)
        .update();
    Set<UUID> recipients = new LinkedHashSet<>();
    recipients.add((UUID) post.get("author"));
    if (parentAuthor != null) recipients.add(parentAuthor);
    notifications.store(actor, recipients, id, postId, parentId != null);
    return comment(id);
  }

  @Transactional
  public Map<String, Object> updateComment(
      UUID actor, UUID commentId, long expectedVersion, String rawBody) {
    String text = rawBody == null ? "" : rawBody.strip().replaceAll("\\s+", " ");
    if (text.isEmpty() || text.codePointCount(0, text.length()) > 2_000) {
      throw error(CommunityErrorCode.INVALID_REQUEST);
    }
    Map<String, Object> current = ownedActive(commentId, actor, "COMMENT");
    String stored =
        jdbc.sql("SELECT body FROM community_comment WHERE content_id=:id")
            .param("id", current.get("internal_id"))
            .query(String.class)
            .single();
    if (text.equals(stored)) return comment(commentId);
    int changed =
        jdbc.sql(
                "UPDATE community_content SET edited_at=NOW(),updated_at=NOW(),version=version+1 WHERE id=:id AND version=:version")
            .param("id", current.get("internal_id"))
            .param("version", expectedVersion)
            .update();
    if (changed == 0) throw error(CommunityErrorCode.PRECONDITION_FAILED);
    jdbc.sql("UPDATE community_comment SET body=:body WHERE content_id=:id")
        .param("body", text)
        .param("id", current.get("internal_id"))
        .update();
    return comment(commentId);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> comments(UUID postId, UUID rootId, String cursor, int size) {
    boolean replies = rootId != null;
    int maximum = replies ? 100 : 50;
    int effectiveSize = size == 0 ? (replies ? 50 : 20) : size;
    if (effectiveSize < 1 || effectiveSize > maximum)
      throw error(CommunityErrorCode.INVALID_PAGINATION_PARAMETER);
    String sort = replies ? "REPLY_ASC" : "COMMENT_DESC";
    CursorCodec.Position position = cursor == null ? null : decodeCursor(cursor, sort);
    String boundary =
        position == null
            ? ""
            : replies
                ? " AND (c.created_at,c.id) > (:createdAt,:internalId)"
                : " AND (c.created_at,c.id) < (:createdAt,:internalId)";
    String sql =
        replies
            ? "SELECT c.id AS cursor_id,c.public_id AS id,CASE WHEN c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL THEN c.account_public_id END AS author_account_id,CASE WHEN c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL THEN m.body END AS body,CASE WHEN c.author_deleted_at IS NOT NULL THEN 'AUTHOR_DELETED' WHEN c.moderator_hidden_at IS NOT NULL THEN 'MODERATOR_HIDDEN' ELSE 'VISIBLE' END AS visibility,c.created_at,c.edited_at,c.version,p.public_id AS parent_comment_id,r.public_id AS thread_root_comment_id,(SELECT COUNT(*) FROM community_reaction cr WHERE cr.content_id=c.id AND cr.reaction_type='LIKE') AS like_count,(SELECT COUNT(*) FROM community_reaction cr WHERE cr.content_id=c.id AND cr.reaction_type='DISLIKE') AS dislike_count FROM community_content c JOIN community_comment m ON m.content_id=c.id JOIN community_content p ON p.id=c.parent_comment_id JOIN community_content r ON r.id=c.thread_root_comment_id WHERE r.public_id=:id AND ((c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL) OR EXISTS(SELECT 1 FROM community_content child WHERE child.parent_comment_id=c.id AND child.author_deleted_at IS NULL AND child.moderator_hidden_at IS NULL))"
                + boundary
                + " ORDER BY c.created_at,c.id LIMIT :limit"
            : "SELECT c.id AS cursor_id,c.public_id AS id,CASE WHEN c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL THEN c.account_public_id END AS author_account_id,CASE WHEN c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL THEN m.body END AS body,CASE WHEN c.author_deleted_at IS NOT NULL THEN 'AUTHOR_DELETED' WHEN c.moderator_hidden_at IS NOT NULL THEN 'MODERATOR_HIDDEN' ELSE 'VISIBLE' END AS visibility,c.created_at,c.edited_at,c.version,(SELECT COUNT(*) FROM community_reaction cr WHERE cr.content_id=c.id AND cr.reaction_type='LIKE') AS like_count,(SELECT COUNT(*) FROM community_reaction cr WHERE cr.content_id=c.id AND cr.reaction_type='DISLIKE') AS dislike_count FROM community_content c JOIN community_comment m ON m.content_id=c.id JOIN community_content p ON p.id=c.post_content_id WHERE p.public_id=:id AND c.parent_comment_id IS NULL AND ((c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL) OR EXISTS(SELECT 1 FROM community_content child WHERE child.thread_root_comment_id=c.id AND child.author_deleted_at IS NULL AND child.moderator_hidden_at IS NULL))"
                + boundary
                + " ORDER BY c.created_at DESC,c.id DESC LIMIT :limit";
    var query =
        jdbc.sql(sql).param("id", replies ? rootId : postId).param("limit", effectiveSize + 1);
    if (position != null)
      query =
          query
              .param("createdAt", java.sql.Timestamp.from(position.createdAt()))
              .param("internalId", position.internalId());
    List<Map<String, Object>> rows = query.query().listOfRows();
    boolean hasNext = rows.size() > effectiveSize;
    if (hasNext) rows = new java.util.ArrayList<>(rows.subList(0, effectiveSize));
    else rows = new java.util.ArrayList<>(rows);
    String next = null;
    if (hasNext) {
      Map<String, Object> last = rows.get(rows.size() - 1);
      next =
          cursorCodec.encode(sort, instant(last.get("created_at")), (Long) last.get("cursor_id"));
    }
    rows.forEach(row -> row.remove("cursor_id"));
    attachAuthors(rows);
    rows.forEach(this::redactTombstone);
    rows.forEach(this::camelCase);
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("items", rows);
    result.put("nextCursor", next);
    return result;
  }

  private CursorCodec.Position decodeCursor(String cursor, String sort) {
    try {
      return cursorCodec.decode(cursor, sort);
    } catch (IllegalArgumentException exception) {
      throw error(CommunityErrorCode.INVALID_PAGINATION_PARAMETER);
    }
  }

  private Instant instant(Object value) {
    if (value instanceof Instant instant) return instant;
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
    return ((java.time.OffsetDateTime) value).toInstant();
  }

  private void attachAuthors(List<Map<String, Object>> rows) {
    Set<UUID> ids =
        rows.stream()
            .map(row -> (UUID) row.get("author_account_id"))
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    var profiles = authorProfiles.findAll(ids);
    if (!profiles.keySet().containsAll(ids)) {
      throw new IllegalStateException("Community 작성자 표시 정보를 모두 조회하지 못했습니다.");
    }
    rows.forEach(
        row -> {
          UUID id = (UUID) row.remove("author_account_id");
          if (id != null && profiles.containsKey(id)) {
            var profile = profiles.get(id);
            row.put(
                "author",
                Map.of("displayName", profile.displayable() ? profile.displayName() : "탈퇴한 사용자"));
          }
        });
  }

  private void redactTombstone(Map<String, Object> row) {
    if (!"VISIBLE".equals(row.get("visibility"))) {
      row.remove("like_count");
      row.remove("dislike_count");
    }
  }

  private void camelCase(Map<String, Object> row) {
    Map<String, Object> converted = new java.util.LinkedHashMap<>();
    row.forEach((key, value) -> converted.put(camelCase(key), value));
    row.clear();
    row.putAll(converted);
  }

  private String camelCase(String key) {
    StringBuilder result = new StringBuilder();
    boolean upper = false;
    for (char character : key.toCharArray()) {
      if (character == '_') {
        upper = true;
      } else {
        result.append(upper ? Character.toUpperCase(character) : character);
        upper = false;
      }
    }
    return result.toString();
  }

  @Transactional
  public Map<String, Object> react(UUID actor, UUID contentId, String type) {
    if (!Set.of("LIKE", "DISLIKE").contains(type)) throw error(CommunityErrorCode.INVALID_REQUEST);
    Long internal = internal(contentId);
    jdbc.sql(
            "INSERT INTO community_reaction(content_id,account_public_id,reaction_type) VALUES (:content,:actor,:type) ON CONFLICT(content_id,account_public_id) DO UPDATE SET reaction_type=EXCLUDED.reaction_type,updated_at=NOW()")
        .param("content", internal)
        .param("actor", actor)
        .param("type", type)
        .update();
    return reactionState(internal, actor);
  }

  @Transactional
  public void removeReaction(UUID actor, UUID contentId) {
    jdbc.sql(
            "DELETE FROM community_reaction WHERE content_id=(SELECT id FROM community_content WHERE public_id=:content) AND account_public_id=:actor")
        .param("content", contentId)
        .param("actor", actor)
        .update();
  }

  @Override
  @Transactional(readOnly = true)
  public void requireVisible(UUID contentId, String kind) {
    int count =
        jdbc.sql(
                "SELECT COUNT(*) FROM community_content WHERE public_id=:id AND kind=:kind AND author_deleted_at IS NULL AND moderator_hidden_at IS NULL")
            .param("id", contentId)
            .param("kind", kind)
            .query(Integer.class)
            .single();
    if (count != 1) throw error(CommunityErrorCode.CONTENT_NOT_FOUND);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> moderationSnapshot(UUID contentId, String kind) {
    String sql =
        "POST".equals(kind)
            ? "SELECT c.public_id AS id,c.kind,p.title,p.body,c.created_at,c.edited_at,c.author_deleted_at,c.moderator_hidden_at FROM community_content c JOIN community_post p ON p.content_id=c.id WHERE c.public_id=:id AND c.kind='POST'"
            : "SELECT c.public_id AS id,c.kind,m.body,c.created_at,c.edited_at,c.author_deleted_at,c.moderator_hidden_at FROM community_content c JOIN community_comment m ON m.content_id=c.id WHERE c.public_id=:id AND c.kind='COMMENT'";
    Map<String, Object> snapshot =
        jdbc.sql(sql).param("id", contentId).query().listOfRows().stream()
            .findFirst()
            .orElseThrow(() -> error(CommunityErrorCode.CONTENT_NOT_FOUND));
    camelCase(snapshot);
    return snapshot;
  }

  @Override
  @Transactional
  public void hide(UUID contentId) {
    int changed =
        jdbc.sql(
                "UPDATE community_content SET moderator_hidden_at=COALESCE(moderator_hidden_at,NOW()),updated_at=NOW() WHERE public_id=:id")
            .param("id", contentId)
            .update();
    if (changed == 0) throw error(CommunityErrorCode.CONTENT_NOT_FOUND);
    mediaUsage.hide(contentId);
  }

  private Map<String, Object> owned(UUID id, UUID actor, String kind) {
    Map<String, Object> state =
        jdbc
            .sql(
                "SELECT id AS internal_id,version FROM community_content WHERE public_id=:id AND account_public_id=:actor AND kind=:kind")
            .param("id", id)
            .param("actor", actor)
            .param("kind", kind)
            .query()
            .listOfRows()
            .stream()
            .findFirst()
            .orElseThrow(() -> error(CommunityErrorCode.CONTENT_FORBIDDEN));
    return state;
  }

  private Map<String, Object> ownedActive(UUID id, UUID actor, String kind) {
    return jdbc
        .sql(
            "SELECT id AS internal_id,version FROM community_content WHERE public_id=:id AND account_public_id=:actor AND kind=:kind AND author_deleted_at IS NULL AND moderator_hidden_at IS NULL")
        .param("id", id)
        .param("actor", actor)
        .param("kind", kind)
        .query()
        .listOfRows()
        .stream()
        .findFirst()
        .orElseThrow(() -> error(CommunityErrorCode.CONTENT_FORBIDDEN));
  }

  private Long internal(UUID id) {
    return jdbc.sql(
            "SELECT id FROM community_content WHERE public_id=:id AND author_deleted_at IS NULL AND moderator_hidden_at IS NULL")
        .param("id", id)
        .query(Long.class)
        .optional()
        .orElseThrow(() -> error(CommunityErrorCode.CONTENT_NOT_FOUND));
  }

  private Map<String, Object> reactionState(Long contentId, UUID actor) {
    Map<String, Object> state =
        jdbc.sql(
                "SELECT (SELECT reaction_type FROM community_reaction WHERE content_id=:content AND account_public_id=:actor) AS type,COUNT(*) FILTER(WHERE reaction_type='LIKE') AS like_count,COUNT(*) FILTER(WHERE reaction_type='DISLIKE') AS dislike_count FROM community_reaction WHERE content_id=:content")
            .param("content", contentId)
            .param("actor", actor)
            .query()
            .singleRow();
    camelCase(state);
    return state;
  }

  private Map<String, Object> comment(UUID id) {
    return jdbc
        .sql(
            "SELECT c.public_id AS id,CASE WHEN c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL THEN c.account_public_id END AS author_account_id,CASE WHEN c.author_deleted_at IS NULL AND c.moderator_hidden_at IS NULL THEN m.body END AS body,CASE WHEN c.author_deleted_at IS NOT NULL THEN 'AUTHOR_DELETED' WHEN c.moderator_hidden_at IS NOT NULL THEN 'MODERATOR_HIDDEN' ELSE 'VISIBLE' END AS visibility,c.created_at,c.edited_at,c.version,p.public_id AS parent_comment_id,r.public_id AS thread_root_comment_id,(SELECT COUNT(*) FROM community_reaction cr WHERE cr.content_id=c.id AND cr.reaction_type='LIKE') AS like_count,(SELECT COUNT(*) FROM community_reaction cr WHERE cr.content_id=c.id AND cr.reaction_type='DISLIKE') AS dislike_count FROM community_content c JOIN community_comment m ON m.content_id=c.id LEFT JOIN community_content p ON p.id=c.parent_comment_id LEFT JOIN community_content r ON r.id=c.thread_root_comment_id WHERE c.public_id=:id")
        .param("id", id)
        .query()
        .listOfRows()
        .stream()
        .findFirst()
        .map(
            row -> {
              attachAuthors(List.of(row));
              redactTombstone(row);
              camelCase(row);
              return row;
            })
        .orElseThrow(() -> error(CommunityErrorCode.CONTENT_NOT_FOUND));
  }

  private Map<String, Object> existingCreation(UUID actor, UUID key) {
    return jdbc
        .sql(
            "SELECT public_id AS id,creation_request_hash AS request_hash FROM community_content WHERE account_public_id=:actor AND creation_request_id=:key")
        .param("actor", actor)
        .param("key", key)
        .query()
        .listOfRows()
        .stream()
        .findFirst()
        .map(row -> Map.of("id", row.get("id"), "requestHash", row.get("request_hash")))
        .orElse(null);
  }

  private PostTitle title(String raw) {
    try {
      if (raw == null || raw.length() > 512) throw new IllegalArgumentException();
      return new PostTitle(raw);
    } catch (RuntimeException e) {
      throw error(CommunityErrorCode.INVALID_REQUEST);
    }
  }

  private PostBody body(JsonNode raw) {
    try {
      return PostBody.from(raw);
    } catch (RuntimeException e) {
      throw error(CommunityErrorCode.INVALID_REQUEST);
    }
  }

  private String canonical(JsonNode node) {
    return node.toString();
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private ApiException error(CommunityErrorCode code) {
    return new ApiException(code);
  }
}
