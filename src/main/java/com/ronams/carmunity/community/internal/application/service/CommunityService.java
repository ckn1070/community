package com.ronams.carmunity.community.internal.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronams.carmunity.account.api.CommunityAuthorProfileLookup;
import com.ronams.carmunity.community.internal.application.CommunityErrorCode;
import com.ronams.carmunity.community.internal.domain.PostBody;
import com.ronams.carmunity.community.internal.domain.PostTitle;
import com.ronams.carmunity.media.api.PostMediaUsage;
import com.ronams.carmunity.shared.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
public class CommunityService {

  private static final UUID FREE_BOARD_ID = UUID.fromString("ed710d1c-91f1-4266-a035-bba33a7f52cb");
  private final JdbcClient jdbc;
  private final PostMediaUsage mediaUsage;
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
