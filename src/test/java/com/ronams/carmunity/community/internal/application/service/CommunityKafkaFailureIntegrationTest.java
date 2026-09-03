package com.ronams.carmunity.community.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronams.carmunity.support.KafkaIntegrationTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
class CommunityKafkaFailureIntegrationTest extends KafkaIntegrationTestSupport {
  @Autowired CommunityService service;
  @Autowired JdbcClient jdbc;

  @Autowired
  com.ronams.carmunity.notification.internal.application.service.ConversationEventInboxService
      inbox;

  @Autowired tools.jackson.databind.json.JsonMapper wireJson;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.ronams.carmunity.community.internal.application.port.out.ConversationOutboxDeliveryPort
      delivery;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.ronams.carmunity.notification.api.CommunityConversationNotification notifications;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean(
      name = "notificationDltKafkaTemplate")
  org.springframework.kafka.core.KafkaTemplate<String, String> dltProducer;

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

  @Test
  void quarantinesPoisonBeforeContinuingTheSamePartition() throws Exception {
    UUID post =
        (UUID)
            service
                .createPost(
                    postAuthor,
                    UUID.randomUUID(),
                    "실패 격리",
                    json.readTree(
                        "{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"}]}"))
                .get("id");
    try (var producer =
            new org.apache.kafka.clients.producer.KafkaProducer<String, String>(
                Map.of(
                    "bootstrap.servers",
                    kafkaBootstrapServers(),
                    "key.serializer",
                    "org.apache.kafka.common.serialization.StringSerializer",
                    "value.serializer",
                    "org.apache.kafka.common.serialization.StringSerializer"));
        var dlt =
            new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
                Map.of(
                    "bootstrap.servers",
                    kafkaBootstrapServers(),
                    "group.id",
                    "dlt-oracle-" + UUID.randomUUID(),
                    "auto.offset.reset",
                    "earliest",
                    "enable.auto.commit",
                    "false",
                    "key.deserializer",
                    "org.apache.kafka.common.serialization.StringDeserializer",
                    "value.deserializer",
                    "org.apache.kafka.common.serialization.StringDeserializer"))) {
      var sent =
          producer
              .send(
                  new org.apache.kafka.clients.producer.ProducerRecord<>(
                      "community.conversation-created.v1",
                      post.toString(),
                      "{\"eventVersion\":999}"))
              .get(10, java.util.concurrent.TimeUnit.SECONDS);
      UUID comment =
          (UUID)
              service.createComment(commenter, post, UUID.randomUUID(), "후속 정상 댓글", null).get("id");
      dlt.subscribe(List.of("community.conversation-created.v1.dlt"));
      var records =
          new java.util.ArrayList<
              org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
      await()
          .atMost(Duration.ofSeconds(30))
          .until(
              () -> {
                dlt.poll(Duration.ofMillis(200))
                    .forEach(
                        record -> {
                          if (post.toString().equals(record.key())) records.add(record);
                        });
                return !records.isEmpty();
              });
      var isolated = records.getFirst();
      assertThat(isolated.value()).isEqualTo("{\"eventVersion\":999}");
      assertThat(isolated.partition()).isEqualTo(sent.partition());
      assertThat(isolated.headers().lastHeader("delivery-failure-class")).isNotNull();
      await()
          .atMost(Duration.ofSeconds(20))
          .untilAsserted(() -> assertThat(recipients(comment)).containsExactly(postAuthor));
    }
  }

  @Test
  void brokerAckBeforeDatabaseFailureReplaysWithoutDuplicatingTheInbox() throws Exception {
    UUID post = post();
    var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              var claim =
                  invocation.getArgument(
                      0,
                      com.ronams.carmunity.community.internal.application.port.out
                          .ConversationOutboxDeliveryPort.Claim.class);
              if (claim.postId().equals(post) && failOnce.getAndSet(false))
                throw new org.springframework.dao.DataAccessResourceFailureException(
                    "ack 이후 DB 기록 실패");
              return invocation.callRealMethod();
            })
        .when(delivery)
        .published(org.mockito.ArgumentMatchers.any());
    UUID comment =
        (UUID) service.createComment(commenter, post, UUID.randomUUID(), "재발행", null).get("id");
    await()
        .atMost(Duration.ofSeconds(25))
        .untilAsserted(
            () -> {
              assertThat(
                      jdbc.sql(
                              "SELECT publish_attempts FROM community_event_outbox WHERE conversation_id=:id AND published_at IS NOT NULL")
                          .param("id", comment)
                          .query(Integer.class)
                          .optional())
                  .hasValueSatisfying(attempts -> assertThat(attempts).isGreaterThanOrEqualTo(2));
              assertThat(recipients(comment)).containsExactly(postAuthor);
              assertThat(receipts(comment)).isEqualTo(1);
            });
  }

  @Test
  void retriesTransientDatabaseFailuresThenRollsBackReceiptAndInboxBeforeDlt() throws Exception {
    UUID post = post();
    var attempts = new java.util.concurrent.atomic.AtomicInteger();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              attempts.incrementAndGet();
              invocation.callRealMethod();
              throw new org.springframework.dao.TransientDataAccessResourceException("DB 일시 오류");
            })
        .when(notifications)
        .store(
            org.mockito.ArgumentMatchers.eq(commenter),
            org.mockito.ArgumentMatchers.anySet(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(post),
            org.mockito.ArgumentMatchers.anyBoolean());
    UUID comment =
        (UUID) service.createComment(commenter, post, UUID.randomUUID(), "재시도 소진", null).get("id");
    awaitDlt(post);
    assertThat(attempts.get()).isEqualTo(4);
    assertThat(receipts(comment)).isZero();
    assertThat(recipients(comment)).isEmpty();
    UUID healthy =
        (UUID)
            service
                .createComment(account("healthy"), post, UUID.randomUUID(), "정상 후속", null)
                .get("id");
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(recipients(healthy)).containsExactly(postAuthor));
  }

  @Test
  void dltFailureKeepsTheOriginalOffsetAndBlocksOnlyUntilRecovery() throws Exception {
    UUID post = post();
    var unavailable = new java.util.concurrent.atomic.AtomicBoolean(true);
    var failedCalls = new java.util.concurrent.atomic.AtomicInteger();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              org.apache.kafka.clients.producer.ProducerRecord<String, String> record =
                  invocation.getArgument(0);
              if (post.toString().equals(record.key()) && unavailable.get()) {
                failedCalls.incrementAndGet();
                return java.util.concurrent.CompletableFuture.failedFuture(
                    new org.apache.kafka.common.KafkaException("DLT ack 실패"));
              }
              return invocation.callRealMethod();
            })
        .when(dltProducer)
        .send(
            org.mockito.ArgumentMatchers
                .<org.apache.kafka.clients.producer.ProducerRecord<String, String>>any());
    try (var producer = producer();
        var admin = AdminClient.create(Map.of("bootstrap.servers", kafkaBootstrapServers()))) {
      var poison =
          producer
              .send(
                  new org.apache.kafka.clients.producer.ProducerRecord<>(
                      "community.conversation-created.v1", post.toString(), "invalid-json"))
              .get(10, java.util.concurrent.TimeUnit.SECONDS);
      UUID comment =
          (UUID)
              service
                  .createComment(commenter, post, UUID.randomUUID(), "DLT 복구 뒤 전달", null)
                  .get("id");
      await().atMost(Duration.ofSeconds(20)).until(() -> failedCalls.get() >= 2);
      var partition =
          new org.apache.kafka.common.TopicPartition(poison.topic(), poison.partition());
      var committed =
          admin
              .listConsumerGroupOffsets("notification.community-conversation.v1")
              .partitionsToOffsetAndMetadata()
              .get(10, java.util.concurrent.TimeUnit.SECONDS)
              .get(partition);
      assertThat(committed == null || committed.offset() <= poison.offset()).isTrue();
      assertThat(recipients(comment)).isEmpty();
      unavailable.set(false);
      awaitDlt(post);
      await()
          .atMost(Duration.ofSeconds(20))
          .untilAsserted(() -> assertThat(recipients(comment)).containsExactly(postAuthor));
    } finally {
      unavailable.set(false);
    }
  }

  @Test
  void databaseCommitBeforeOffsetFailureIsIdempotentOnRedelivery() throws Exception {
    UUID post = post();
    listeners.stop();
    try (var consumer = consumer("manual-offset-" + UUID.randomUUID())) {
      UUID comment =
          (UUID)
              service
                  .createComment(commenter, post, UUID.randomUUID(), "offset 실패", null)
                  .get("id");
      consumer.subscribe(List.of("community.conversation-created.v1"));
      var matched =
          new java.util.concurrent.atomic.AtomicReference<
              org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
      await()
          .atMost(Duration.ofSeconds(20))
          .until(
              () -> {
                consumer
                    .poll(Duration.ofMillis(200))
                    .forEach(
                        record -> {
                          if (post.toString().equals(record.key())) matched.set(record);
                        });
                return matched.get() != null;
              });
      var record = matched.get();
      var listener =
          new com.ronams.carmunity.notification.internal.adapter.in.kafka.ConversationEventListener(
              inbox, wireJson);
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  listener.receive(
                      record,
                      () -> {
                        throw new IllegalStateException("offset 기록 전 중단");
                      }))
          .hasMessage("offset 기록 전 중단");
      var partition =
          new org.apache.kafka.common.TopicPartition(record.topic(), record.partition());
      assertThat(consumer.committed(Set.of(partition)).get(partition)).isNull();
      assertThat(receipts(comment)).isEqualTo(1);
      listener.receive(
          record,
          () ->
              consumer.commitSync(
                  Map.of(
                      partition,
                      new org.apache.kafka.clients.consumer.OffsetAndMetadata(
                          record.offset() + 1))));
      assertThat(consumer.committed(Set.of(partition)).get(partition).offset())
          .isEqualTo(record.offset() + 1);
      assertThat(receipts(comment)).isEqualTo(1);
      assertThat(recipients(comment)).containsExactly(postAuthor);
    } finally {
      listeners.start();
    }
  }

  private UUID post() throws Exception {
    return (UUID)
        service
            .createPost(
                postAuthor,
                UUID.randomUUID(),
                "실패 경계",
                json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"}]}"))
            .get("id");
  }

  private long receipts(UUID comment) {
    return jdbc.sql(
            "SELECT COUNT(*) FROM notification_event_receipt r JOIN community_event_outbox o ON o.event_id=r.event_id WHERE o.conversation_id=:id")
        .param("id", comment)
        .query(Long.class)
        .single();
  }

  private void awaitDlt(UUID post) {
    try (var consumer = consumer("dlt-boundary-" + UUID.randomUUID())) {
      consumer.subscribe(List.of("community.conversation-created.v1.dlt"));
      await()
          .atMost(Duration.ofSeconds(30))
          .until(
              () -> {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                  if (post.toString().equals(record.key())) return true;
                }
                return false;
              });
    }
  }

  private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer() {
    return new org.apache.kafka.clients.producer.KafkaProducer<>(
        Map.of(
            "bootstrap.servers",
            kafkaBootstrapServers(),
            "key.serializer",
            "org.apache.kafka.common.serialization.StringSerializer",
            "value.serializer",
            "org.apache.kafka.common.serialization.StringSerializer"));
  }

  private org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer(String group) {
    return new org.apache.kafka.clients.consumer.KafkaConsumer<>(
        Map.of(
            "bootstrap.servers",
            kafkaBootstrapServers(),
            "group.id",
            group,
            "auto.offset.reset",
            "earliest",
            "enable.auto.commit",
            "false",
            "key.deserializer",
            "org.apache.kafka.common.serialization.StringDeserializer",
            "value.deserializer",
            "org.apache.kafka.common.serialization.StringDeserializer"));
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
