package com.ronams.carmunity.community.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronams.carmunity.support.KafkaIntegrationTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(
    properties = {
      "carmunity.community.enabled=true",
      "carmunity.kafka.relay-enabled=true",
      "carmunity.kafka.consumer-enabled=true",
      "carmunity.kafka.sync-notifications-enabled=false",
      "carmunity.community.cursor-hmac-secret=integration-cursor-secret-that-is-at-least-32-bytes",
      "carmunity.community.media.bucket=test-bucket",
      "carmunity.community.media.role-arn=arn:aws:iam::000000000000:role/test",
      "carmunity.community.media.token-path=/tmp/nonexistent-test-token"
    })
class CommunityKafkaDeliveryIntegrationTest extends KafkaIntegrationTestSupport {
  @Autowired CommunityService service;
  @Autowired JdbcClient jdbc;
  @Autowired org.springframework.kafka.config.KafkaListenerEndpointRegistry listeners;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("communityKafkaTemplate")
  org.springframework.kafka.core.KafkaTemplate<String, String> kafka;

  private final ObjectMapper json = new ObjectMapper();
  private UUID postAuthor;
  private UUID commenter;

  @BeforeEach
  void accounts() {
    postAuthor = account("post-author");
    commenter = account("commenter");
  }

  @BeforeAll
  static void topics() throws Exception {
    try (var admin = AdminClient.create(Map.of("bootstrap.servers", kafkaBootstrapServers()))) {
      admin
          .createTopics(
              List.of(
                  new NewTopic("community.conversation-created.v1", 3, (short) 1),
                  new NewTopic("community.conversation-created.v1.dlt", 3, (short) 1)))
          .all()
          .get(20, java.util.concurrent.TimeUnit.SECONDS);
    }
  }

  @Test
  void createsTheExactInboxRecipientsThroughKafkaOnly() throws Exception {
    listeners.stop();
    UUID post =
        (UUID)
            service
                .createPost(
                    postAuthor,
                    UUID.randomUUID(),
                    "비동기 전달",
                    json.readTree(
                        "{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"}]}"))
                .get("id");
    UUID comment =
        (UUID) service.createComment(commenter, post, UUID.randomUUID(), "댓글", null).get("id");
    UUID third = account("third");
    UUID reply =
        (UUID) service.createComment(third, post, UUID.randomUUID(), "답글", comment).get("id");
    UUID self =
        (UUID) service.createComment(postAuthor, post, UUID.randomUUID(), "자기 댓글", null).get("id");
    UUID overlap =
        (UUID) service.createComment(third, post, UUID.randomUUID(), "중복 관계", self).get("id");

    assertThat(recipients(comment)).isEmpty();
    assertThat(recipients(reply)).isEmpty();
    assertThat(recipients(self)).isEmpty();
    assertThat(recipients(overlap)).isEmpty();
    listeners.start();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(
                      jdbc.sql(
                              "SELECT COUNT(*) FROM notification_event_receipt r JOIN community_event_outbox o ON o.event_id=r.event_id WHERE o.post_id=:post")
                          .param("post", post)
                          .query(Long.class)
                          .single())
                  .isEqualTo(4);
              assertThat(recipients(comment)).containsExactlyInAnyOrder(postAuthor);
              assertThat(recipients(reply)).containsExactlyInAnyOrder(postAuthor, commenter);
              assertThat(recipients(self)).isEmpty();
              assertThat(recipients(overlap)).containsExactlyInAnyOrder(postAuthor);
            });
  }

  @Test
  void acceptsAnUnknownOptionalFieldWithTheActualConsumerMapper() throws Exception {
    UUID post = post();
    UUID event = UUID.randomUUID();
    UUID conversation = UUID.randomUUID();
    String payload =
        """
        {"eventId":"%s","eventType":"CommunityConversationCreated","eventVersion":1,
         "occurredAt":"2026-09-03T00:00:00Z","postId":"%s","conversationId":"%s",
         "conversationKind":"COMMENT","actorAccountId":"%s","postAuthorAccountId":"%s",
         "directParentAuthorAccountId":null,"futureOptionalField":"ignored"}
        """
            .formatted(event, post, conversation, commenter, postAuthor);
    kafka.send("community.conversation-created.v1", post.toString(), payload).get();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(recipients(conversation)).containsExactly(postAuthor));
  }

  private UUID post() throws Exception {
    return (UUID)
        service
            .createPost(
                postAuthor,
                UUID.randomUUID(),
                "호환성",
                json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"}]}"))
            .get("id");
  }

  private List<UUID> recipients(UUID source) {
    return jdbc.sql(
            "SELECT recipient_account_public_id FROM notification_inbox WHERE source_content_public_id=:source")
        .param("source", source)
        .query(UUID.class)
        .list();
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
