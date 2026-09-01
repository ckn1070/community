package com.ronams.carmunity.community.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(
    properties = {
      "carmunity.community.enabled=true",
      "carmunity.community.cursor-hmac-secret=integration-cursor-secret-that-is-at-least-32-bytes",
      "carmunity.community.media.bucket=test-bucket",
      "carmunity.community.media.role-arn=arn:aws:iam::000000000000:role/test",
      "carmunity.community.media.token-path=/tmp/nonexistent-test-token"
    })
class CommunityConversationIntegrationTest extends PostgreSqlIntegrationTestSupport {
  @Autowired CommunityService service;
  @Autowired JdbcClient jdbc;
  private final ObjectMapper json = new ObjectMapper();
  private UUID postAuthor;
  private UUID commenter;

  @BeforeEach
  void accounts() {
    postAuthor = account("post-author");
    commenter = account("commenter");
  }

  @Test
  void createsPostReplyAndBothRequiredNotificationsAtomically() throws Exception {
    UUID postId =
        (UUID)
            service
                .createPost(
                    postAuthor,
                    UUID.randomUUID(),
                    "  첫 글  ",
                    json.readTree(
                        "{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문입니다\"}]}"))
                .get("id");
    UUID commentId =
        (UUID) service.createComment(commenter, postId, UUID.randomUUID(), "댓글", null).get("id");
    service.createComment(postAuthor, postId, UUID.randomUUID(), "답글", commentId);

    assertThat(
            jdbc.sql(
                    "SELECT COUNT(*) FROM community_content WHERE post_content_id=(SELECT id FROM community_content WHERE public_id=:post)")
                .param("post", postId)
                .query(Long.class)
                .single())
        .isEqualTo(2);
    assertThat(jdbc.sql("SELECT COUNT(*) FROM notification_inbox").query(Long.class).single())
        .isEqualTo(2);
  }

  @Test
  void commentCursorContinuesWithoutDuplicates() throws Exception {
    UUID postId =
        (UUID)
            service
                .createPost(
                    postAuthor,
                    UUID.randomUUID(),
                    "페이지 테스트",
                    json.readTree(
                        "{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"}]}"))
                .get("id");
    service.createComment(commenter, postId, UUID.randomUUID(), "첫 댓글", null);
    service.createComment(commenter, postId, UUID.randomUUID(), "둘째 댓글", null);

    Map<String, Object> first = service.comments(postId, null, null, 1);
    String cursor = (String) first.get("nextCursor");
    Map<String, Object> second = service.comments(postId, null, cursor, 1);

    assertThat(cursor).isNotBlank();
    assertThat((java.util.List<?>) first.get("items")).hasSize(1);
    assertThat((java.util.List<?>) second.get("items")).hasSize(1);
    assertThat(first.get("items")).isNotEqualTo(second.get("items"));
  }

  private UUID account(String name) {
    UUID id = UUID.randomUUID();
    Long internal =
        jdbc.sql(
                "INSERT INTO account(public_id,email,email_normalized,status,email_verified_at) VALUES(:id,:email,:email,'ACTIVE',NOW()) RETURNING id")
            .param("id", id)
            .param("email", name + "+" + id + "@example.com")
            .query(Long.class)
            .single();
    jdbc.sql(
            "INSERT INTO account_profile(account_id,display_name,display_name_normalized) VALUES(:id,:name,:name)")
        .param("id", internal)
        .param("name", name + "-" + id.toString().substring(0, 8))
        .update();
    return id;
  }
}
