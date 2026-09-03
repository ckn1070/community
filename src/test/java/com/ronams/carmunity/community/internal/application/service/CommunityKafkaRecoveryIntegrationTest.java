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
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class CommunityKafkaRecoveryIntegrationTest extends KafkaIntegrationTestSupport {
  @Autowired CommunityService service;
  @Autowired JdbcClient jdbc;
  @Autowired org.springframework.kafka.config.KafkaListenerEndpointRegistry listeners;
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

  @Autowired ConversationOutboxLifecycleService lifecycle;
  @Autowired com.ronams.carmunity.community.internal.adapter.in.worker.ConversationEventRelay relay;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("communityKafkaProducerFactory")
  org.springframework.kafka.core.DefaultKafkaProducerFactory<String, String> relayFactory;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("notificationDltProducerFactory")
  org.springframework.kafka.core.DefaultKafkaProducerFactory<String, String> dltFactory;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.ronams.carmunity.notification.api.CommunityConversationNotification notifications;

  @Test
  @org.junit.jupiter.api.Order(1)
  void replaysTheOriginalDltEventAndKeepsReceiptAndInboxUnique() throws Exception {
    UUID post = post();
    java.util.concurrent.atomic.AtomicBoolean failed =
        new java.util.concurrent.atomic.AtomicBoolean(true);
    org.mockito.Mockito.doAnswer(
            call -> {
              if (failed.get() && post.equals(call.getArgument(3)))
                throw new org.springframework.dao.DataIntegrityViolationException("격리 시험");
              return call.callRealMethod();
            })
        .when(notifications)
        .store(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyBoolean());
    UUID comment = comment(post);
    UUID event = event(comment);
    try (var consumer =
        new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
            Map.of(
                "bootstrap.servers",
                kafkaBootstrapServers(),
                "group.id",
                "recovery-dlt-" + UUID.randomUUID(),
                "auto.offset.reset",
                "earliest",
                "enable.auto.commit",
                false,
                "key.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer"))) {
      consumer.subscribe(List.of("community.conversation-created.v1.dlt"));
      await()
          .atMost(Duration.ofSeconds(30))
          .until(
              () ->
                  java.util.stream.StreamSupport.stream(
                          consumer.poll(Duration.ofMillis(200)).spliterator(), false)
                      .anyMatch(record -> post.toString().equals(record.key())));
    }
    assertThat(recipients(comment)).isEmpty();
    failed.set(false);
    replay(java.util.Set.of(event), 1, true);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(recipients(comment)).containsExactly(postAuthor));
    assertThat(lifecycle.preview(java.util.Set.of(event))).isEmpty();
    replay(java.util.Set.of(event), 0, false);
    assertThat(receipts(java.util.Set.of(event))).isEqualTo(1);
  }

  @Test
  @org.junit.jupiter.api.Order(2)
  void survivesBrokerInterruptionAndRestoresOnlyMissingReceiptsAfterBrokerDataLoss()
      throws Exception {
    UUID post = post();
    UUID first = comment(post);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(recipients(first)).containsExactly(postAuthor));
    listeners.stop();
    pauseKafka();
    UUID restarted = comment(post), restartedEvent = event(restarted);
    assertThat(recipients(restarted)).isEmpty();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.sql(
                                "SELECT publish_attempts FROM community_event_outbox WHERE event_id=:id")
                            .param("id", restartedEvent)
                            .query(Integer.class)
                            .single())
                    .isPositive());
    resumeKafka();
    await()
        .atMost(Duration.ofSeconds(45))
        .until(
            () -> {
              try (var admin =
                  AdminClient.create(Map.of("bootstrap.servers", kafkaBootstrapServers()))) {
                return admin
                    .listTopics()
                    .names()
                    .get(2, java.util.concurrent.TimeUnit.SECONDS)
                    .contains("community.conversation-created.v1");
              } catch (Exception exception) {
                return false;
              }
            });
    relayFactory.reset();
    dltFactory.reset();
    listeners.start();
    await()
        .atMost(Duration.ofSeconds(45))
        .untilAsserted(() -> assertThat(recipients(restarted)).containsExactly(postAuthor));
    listeners.stop();
    UUID missing = comment(post), missingEvent = event(missing);
    await()
        .atMost(Duration.ofSeconds(20))
        .until(
            () ->
                jdbc.sql(
                        "SELECT published_at IS NOT NULL FROM community_event_outbox WHERE event_id=:id")
                    .param("id", missingEvent)
                    .query(Boolean.class)
                    .single());
    relay.stop();
    UUID terminal = comment(post), terminalEvent = event(terminal);
    lifecycle.terminate(
        java.util.Set.of(terminalEvent),
        1,
        "test operator",
        "permanent failure",
        "no inbox",
        "unrecoverable evidence");
    pauseKafka();
    UUID duringOutage = comment(post); // Broker가 없는 동안에도 업무와 outbox를 함께 commit합니다.
    assertThat(recipients(duringOutage)).isEmpty();
    resumeKafka();
    try (var admin = AdminClient.create(Map.of("bootstrap.servers", kafkaBootstrapServers()))) {
      admin
          .deleteTopics(
              List.of("community.conversation-created.v1", "community.conversation-created.v1.dlt"))
          .all()
          .get(20, java.util.concurrent.TimeUnit.SECONDS);
      try {
        admin
            .deleteConsumerGroups(List.of("notification.community-conversation.v1"))
            .all()
            .get(20, java.util.concurrent.TimeUnit.SECONDS);
      } catch (java.util.concurrent.ExecutionException ignored) {
        // 이미 유실됐거나 만들어지지 않은 group도 목표 상태입니다.
      }
      await()
          .atMost(Duration.ofSeconds(20))
          .until(
              () ->
                  admin
                      .listTopics()
                      .names()
                      .get(2, java.util.concurrent.TimeUnit.SECONDS)
                      .isEmpty());
    }
    topics();
    relayFactory.reset();
    dltFactory.reset();
    var all =
        java.util.Set.of(
            event(first), event(restarted), missingEvent, event(duringOutage), terminalEvent);
    assertThat(lifecycle.preview(all).stream().map(e -> e.eventId()))
        .containsExactlyInAnyOrder(missingEvent, event(duringOutage));
    replay(all, 2, false);
    assertThat(receipts(all)).isEqualTo(2);
    listeners.start();
    replay(all, 2, true);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(receipts(all)).isEqualTo(4);
              assertThat(recipients(first)).containsExactly(postAuthor);
              assertThat(recipients(restarted)).containsExactly(postAuthor);
              assertThat(recipients(missing)).containsExactly(postAuthor);
              assertThat(recipients(duringOutage)).containsExactly(postAuthor);
              assertThat(recipients(terminal)).isEmpty();
            });
    relay.start();
    assertThat(lifecycle.preview(all)).isEmpty();
  }

  private UUID post() throws Exception {
    return (UUID)
        service
            .createPost(
                postAuthor,
                UUID.randomUUID(),
                "복구",
                json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"}]}"))
            .get("id");
  }

  private UUID comment(UUID post) {
    return (UUID) service.createComment(commenter, post, UUID.randomUUID(), "댓글", null).get("id");
  }

  private UUID event(UUID comment) {
    return jdbc.sql("SELECT event_id FROM community_event_outbox WHERE conversation_id=:id")
        .param("id", comment)
        .query(UUID.class)
        .single();
  }

  private long receipts(java.util.Set<UUID> ids) {
    return jdbc.sql("SELECT COUNT(*) FROM notification_event_receipt WHERE event_id IN (:ids)")
        .param("ids", ids)
        .query(Long.class)
        .single();
  }

  private void replay(java.util.Set<UUID> ids, int expected, boolean approved) throws Exception {
    var factory =
        new org.springframework.kafka.core.DefaultKafkaProducerFactory<String, String>(
            Map.of(
                "bootstrap.servers",
                kafkaBootstrapServers(),
                "key.serializer",
                "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer",
                "org.apache.kafka.common.serialization.StringSerializer",
                "acks",
                "all",
                "enable.idempotence",
                true));
    try {
      var template = new org.springframework.kafka.core.KafkaTemplate<String, String>(factory);
      var provider =
          new org.springframework.beans.factory.ObjectProvider<
              org.springframework.kafka.core.KafkaTemplate<String, String>>() {
            @Override
            public org.springframework.kafka.core.KafkaTemplate<String, String> getObject() {
              if (!approved) throw new AssertionError("dry-run은 producer를 요청하면 안 됩니다");
              return template;
            }
          };
      var args =
          new java.util.ArrayList<>(
              List.of(
                  "--command=kafka-events",
                  "--mode=replay",
                  "--reason=복구 시험",
                  "--event-ids="
                      + ids.stream()
                          .map(UUID::toString)
                          .collect(java.util.stream.Collectors.joining(","))));
      if (approved)
        args.addAll(List.of("--approved=true", "--expected-count=" + expected, "--operator=test"));
      new com.ronams.carmunity.community.internal.adapter.in.command.ConversationEventOperation(
              lifecycle, provider)
          .run(
              new org.springframework.boot.DefaultApplicationArguments(
                  args.toArray(String[]::new)));
    } finally {
      factory.destroy();
    }
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
