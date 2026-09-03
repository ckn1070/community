package com.ronams.carmunity.community.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;

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
class CommunityOutboxClaimIntegrationTest extends PostgreSqlIntegrationTestSupport {
  @Autowired CommunityService service;
  @Autowired JdbcClient jdbc;

  @Autowired
  com.ronams.carmunity.community.internal.application.port.out.ConversationOutboxDeliveryPort
      delivery;

  private final ObjectMapper json = new ObjectMapper();
  private UUID postAuthor;
  private UUID commenter;

  @BeforeEach
  void accounts() {
    postAuthor = account("post-author");
    commenter = account("commenter");
  }

  @Test
  void rejectsExpiredOwnerAfterAnotherWorkerReclaimsTheEvent() throws Exception {
    UUID post =
        (UUID)
            service
                .createPost(
                    postAuthor,
                    UUID.randomUUID(),
                    "작업 소유권",
                    json.readTree(
                        "{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"}]}"))
                .get("id");
    service.createComment(commenter, post, UUID.randomUUID(), "전달 원본", null);
    var first = delivery.claim().orElseThrow();
    assertThat(delivery.claim()).isEmpty();
    jdbc.sql(
            "UPDATE community_event_outbox SET lease_until=NOW()-INTERVAL '1 second' WHERE event_id=:event")
        .param("event", first.eventId())
        .update();
    assertThat(delivery.published(first)).isFalse();
    var next = delivery.claim().orElseThrow();
    assertThat(next.eventId()).isEqualTo(first.eventId());
    assertThat(next.leaseToken()).isNotEqualTo(first.leaseToken());
    assertThat(delivery.published(first)).isFalse();
    assertThat(delivery.published(next)).isTrue();
    assertThat(delivery.claim()).isEmpty();
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM community_event_outbox WHERE event_id=:event")
                .param("event", first.eventId())
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void onlyOneOfTwoWorkersClaimsTheSameEvent() throws Exception {
    UUID post =
        (UUID)
            service
                .createPost(
                    postAuthor,
                    UUID.randomUUID(),
                    "동시 claim",
                    json.readTree(
                        "{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"}]}"))
                .get("id");
    service.createComment(commenter, post, UUID.randomUUID(), "원본", null);
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var tasks =
          java.util.List.of(
              pool.submit(
                  () -> {
                    start.await();
                    return delivery.claim();
                  }),
              pool.submit(
                  () -> {
                    start.await();
                    return delivery.claim();
                  }));
      start.countDown();
      assertThat(
              java.util.List.of(tasks.get(0).get(), tasks.get(1).get()).stream()
                  .filter(java.util.Optional::isPresent)
                  .count())
          .isEqualTo(1);
    }
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
