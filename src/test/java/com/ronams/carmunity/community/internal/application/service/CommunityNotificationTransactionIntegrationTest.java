package com.ronams.carmunity.community.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronams.carmunity.notification.api.CommunityConversationNotification;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "carmunity.community.enabled=true",
      "carmunity.community.cursor-hmac-secret=integration-cursor-secret-that-is-at-least-32-bytes",
      "carmunity.community.media.bucket=test-bucket",
      "carmunity.community.media.role-arn=arn:aws:iam::000000000000:role/test",
      "carmunity.community.media.token-path=/tmp/nonexistent-test-token"
    })
class CommunityNotificationTransactionIntegrationTest extends PostgreSqlIntegrationTestSupport {

  @Autowired CommunityService service;
  @Autowired JdbcClient jdbc;
  @MockitoBean CommunityConversationNotification notifications;

  private final ObjectMapper json = new ObjectMapper();
  private UUID postAuthor;
  private UUID commenter;
  private UUID postId;

  @BeforeEach
  void setUp() throws Exception {
    postAuthor = account("notify-author");
    commenter = account("notify-commenter");
    postId =
        (UUID)
            service
                .createPost(
                    postAuthor,
                    UUID.randomUUID(),
                    "알림 원자성",
                    json.readTree(
                        "{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"}]}"))
                .get("id");
  }

  @Test
  void rollsBackCommentWhenInboxStorageFails() {
    doThrow(new IllegalStateException("inbox 저장 실패"))
        .when(notifications)
        .store(any(UUID.class), anySet(), any(UUID.class), any(UUID.class), anyBoolean());

    assertThatThrownBy(
            () -> service.createComment(commenter, postId, UUID.randomUUID(), "저장되면 안 됨", null))
        .isInstanceOf(IllegalStateException.class);

    assertThat(
            jdbc.sql(
                    "SELECT COUNT(*) FROM community_content WHERE kind='COMMENT' AND post_content_id=(SELECT id FROM community_content WHERE public_id=:post)")
                .param("post", postId)
                .query(Long.class)
                .single())
        .isZero();
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
