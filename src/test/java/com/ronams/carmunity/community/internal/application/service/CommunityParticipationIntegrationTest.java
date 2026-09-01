package com.ronams.carmunity.community.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronams.carmunity.media.api.PostMediaUsage;
import com.ronams.carmunity.moderation.internal.application.ModerationErrorCode;
import com.ronams.carmunity.moderation.internal.application.ModerationService;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.util.Map;
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
class CommunityParticipationIntegrationTest extends PostgreSqlIntegrationTestSupport {

  @Autowired CommunityService community;
  @Autowired ModerationService moderation;
  @Autowired JdbcClient jdbc;
  @MockitoBean PostMediaUsage mediaUsage;

  private final ObjectMapper json = new ObjectMapper();
  private UUID author;
  private UUID participant;
  private UUID administrator;
  private UUID postId;

  @BeforeEach
  void setUp() throws Exception {
    author = account("participation-author");
    participant = account("participation-member");
    administrator = account("participation-admin");
    postId =
        (UUID)
            community
                .createPost(
                    author,
                    UUID.randomUUID(),
                    "참여 테스트",
                    json.readTree(
                        "{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"}]}"))
                .get("id");
  }

  @Test
  void reactionSelectionConvergesToOneValueAndCanBeRemoved() {
    assertThat(community.react(participant, postId, "LIKE"))
        .containsEntry("type", "LIKE")
        .containsEntry("likeCount", 1L)
        .containsEntry("dislikeCount", 0L);
    assertThat(community.react(participant, postId, "DISLIKE"))
        .containsEntry("type", "DISLIKE")
        .containsEntry("likeCount", 0L)
        .containsEntry("dislikeCount", 1L);

    community.removeReaction(participant, postId);
    community.removeReaction(participant, postId);

    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM community_reaction WHERE account_public_id=:actor")
                .param("actor", participant)
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void reportIsUniqueWhileOpenAndHidePreservesModerationFact() {
    Map<String, Object> report = moderation.report(participant, postId, "POST");

    assertThatThrownBy(() -> moderation.report(participant, postId, "POST"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(ModerationErrorCode.REPORT_ALREADY_OPEN));

    UUID reportId = (UUID) report.get("id");
    Map<String, Object> resolved = moderation.resolve(administrator, reportId, 0L, "HIDE_CONTENT");
    assertThat(resolved).containsEntry("status", "CONTENT_HIDDEN");
    assertThat(community.moderationSnapshot(postId, "POST").get("moderatorHiddenAt")).isNotNull();
    assertThatThrownBy(() -> moderation.resolve(administrator, reportId, 0L, "DISMISS"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(ModerationErrorCode.PRECONDITION_FAILED));
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
