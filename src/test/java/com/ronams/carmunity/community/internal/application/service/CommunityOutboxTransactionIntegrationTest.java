package com.ronams.carmunity.community.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
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
class CommunityOutboxTransactionIntegrationTest extends PostgreSqlIntegrationTestSupport {
  @Autowired CommunityService service;
  @Autowired JdbcClient jdbc;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  private final ObjectMapper json = new ObjectMapper();
  private UUID postAuthor;
  private UUID commenter;

  @BeforeEach
  void accounts() {
    postAuthor = account("post-author");
    commenter = account("commenter");
  }

  @Test
  void storesOneImmutableEventForRepeatedCommentRequest() throws Exception {
    UUID post = post();
    UUID request = UUID.randomUUID();
    UUID comment = (UUID) service.createComment(commenter, post, request, "댓글", null).get("id");
    UUID repeated = (UUID) service.createComment(commenter, post, request, "댓글", null).get("id");

    assertThat(repeated).isEqualTo(comment);
    var rows =
        jdbc.sql("SELECT payload::text FROM community_event_outbox WHERE conversation_id=:id")
            .param("id", comment)
            .query(String.class)
            .list();
    assertThat(rows).hasSize(1);
    var event = json.readTree(rows.getFirst());
    assertThat(event.get("eventType").asText()).isEqualTo("CommunityConversationCreated");
    assertThat(event.get("eventVersion").asInt()).isEqualTo(1);
    assertThat(event.get("conversationKind").asText()).isEqualTo("COMMENT");
    assertThat(event.get("postId").asText()).isEqualTo(post.toString());
    assertThat(event.get("conversationId").asText()).isEqualTo(comment.toString());
    assertThat(event.get("actorAccountId").asText()).isEqualTo(commenter.toString());
    assertThat(event.get("postAuthorAccountId").asText()).isEqualTo(postAuthor.toString());
    assertThat(event.get("directParentAuthorAccountId").isNull()).isTrue();
    assertThat(UUID.fromString(event.get("eventId").asText())).isNotNull();
    assertThat(java.time.Instant.parse(event.get("occurredAt").asText())).isNotNull();
    assertThat(event.size()).isEqualTo(10);
  }

  @Test
  void snapshotsTheDirectParentAuthorForReplies() throws Exception {
    UUID post = post();
    UUID parent =
        (UUID) service.createComment(commenter, post, UUID.randomUUID(), "부모 댓글", null).get("id");
    UUID replier = account("replier");
    UUID reply =
        (UUID) service.createComment(replier, post, UUID.randomUUID(), "답글", parent).get("id");
    // 원 콘텐츠 변경 이후에도 생성 시점의 전달 원본은 바뀌지 않습니다.
    service.deleteContent(commenter, parent, "COMMENT");
    var payload =
        json.readTree(
            jdbc.sql("SELECT payload::text FROM community_event_outbox WHERE conversation_id=:id")
                .param("id", reply)
                .query(String.class)
                .single());
    assertThat(payload.get("conversationKind").asText()).isEqualTo("REPLY");
    assertThat(payload.get("directParentAuthorAccountId").asText()).isEqualTo(commenter.toString());
    assertThat(payload.get("actorAccountId").asText()).isEqualTo(replier.toString());
    assertThat(payload.get("postAuthorAccountId").asText()).isEqualTo(postAuthor.toString());
  }

  @Test
  void rollsBackCommentWhenTheDatabaseRejectsTheOutbox() throws Exception {
    UUID post = post();
    // 이 test DB에서만 특정 게시글의 outbox INSERT를 거부해 실제 persistence 실패를 만듭니다.
    jdbc.sql(
            "ALTER TABLE community_event_outbox ADD CONSTRAINT test_outbox_failure CHECK (post_id <> '"
                + post
                + "'::uuid)")
        .update();
    try {
      assertThatThrownBy(
              () -> service.createComment(commenter, post, UUID.randomUUID(), "실패할 댓글", null))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(commentCount(post)).isZero();
      assertThat(outboxCount(post)).isZero();
      assertThat(
              jdbc.sql("SELECT COUNT(*) FROM notification_inbox WHERE post_public_id=:post")
                  .param("post", post)
                  .query(Long.class)
                  .single())
          .isZero();
    } finally {
      jdbc.sql("ALTER TABLE community_event_outbox DROP CONSTRAINT test_outbox_failure").update();
    }
  }

  @Test
  void rollsBackTheEventWhenTheEnclosingTransactionFails() throws Exception {
    UUID post = post();
    var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      service.createComment(commenter, post, UUID.randomUUID(), "원자 저장", null);
                      assertThat(commentCount(post)).isEqualTo(1);
                      assertThat(outboxCount(post)).isEqualTo(1);
                      throw new IllegalStateException("업무 transaction 실패");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("업무 transaction 실패");
    assertThat(commentCount(post)).isZero();
    assertThat(outboxCount(post)).isZero();
  }

  @Test
  void concurrentDuplicateRequestsKeepOneLogicalEvent() throws Exception {
    UUID post = post();
    UUID request = UUID.randomUUID();
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<UUID> create =
          () -> {
            ready.countDown();
            if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS))
              throw new IllegalStateException("동시 시작 실패");
            try {
              return (UUID)
                  service.createComment(commenter, post, request, "동일 요청", null).get("id");
            } catch (org.springframework.dao.DuplicateKeyException conflict) {
              // 기존 API의 DB unique 충돌 후 같은 요청을 재시도해도 새 사건을 만들지 않아야 합니다.
              return (UUID)
                  service.createComment(commenter, post, request, "동일 요청", null).get("id");
            }
          };
      var first = workers.submit(create);
      var second = workers.submit(create);
      assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(first.get(20, java.util.concurrent.TimeUnit.SECONDS))
          .isEqualTo(second.get(20, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      start.countDown();
    }
    assertThat(commentCount(post)).isEqualTo(1);
    assertThat(outboxCount(post)).isEqualTo(1);
  }

  private long commentCount(UUID post) {
    return jdbc.sql(
            "SELECT COUNT(*) FROM community_content WHERE post_content_id=(SELECT id FROM community_content WHERE public_id=:post)")
        .param("post", post)
        .query(Long.class)
        .single();
  }

  private long outboxCount(UUID post) {
    return jdbc.sql("SELECT COUNT(*) FROM community_event_outbox WHERE post_id=:post")
        .param("post", post)
        .query(Long.class)
        .single();
  }

  private UUID post() throws Exception {
    return (UUID)
        service
            .createPost(
                postAuthor,
                UUID.randomUUID(),
                "원자 저장",
                json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"}]}"))
            .get("id");
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
