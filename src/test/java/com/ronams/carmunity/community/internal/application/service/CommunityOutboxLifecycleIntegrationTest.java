package com.ronams.carmunity.community.internal.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ronams.carmunity.notification.api.ConversationEventCompletion;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class CommunityOutboxLifecycleIntegrationTest extends PostgreSqlIntegrationTestSupport {
  @Autowired ConversationOutboxLifecycleService lifecycle;
  @Autowired JdbcClient jdbc;
  @MockitoBean Clock clock;
  @MockitoSpyBean ConversationEventCompletion completion;
  private final Instant now = Instant.parse("2026-09-03T00:00:00Z");

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(now);
    jdbc.sql("DELETE FROM community_event_outbox").update();
    jdbc.sql("DELETE FROM notification_event_receipt").update();
  }

  @Test
  void preservesPendingAndPublishedUntilReceiptThenSevenDays() {
    UUID pending = event(false), published = event(true);
    assertThat(lifecycle.cleanup()).isZero();
    assertThat(lifecycle.preview(Set.of(pending, published))).hasSize(2);
    receipt(published);
    assertThat(lifecycle.cleanup()).isZero();
    when(clock.instant()).thenReturn(now.plus(Duration.ofDays(7)).minusNanos(1000));
    assertThat(lifecycle.cleanup()).isZero();
    when(clock.instant()).thenReturn(now.plus(Duration.ofDays(7)));
    assertThat(lifecycle.cleanup()).isEqualTo(1);
    assertThat(lifecycle.preview(Set.of(pending))).hasSize(1);
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM notification_event_receipt").query(Long.class).single())
        .isEqualTo(1);
  }

  @Test
  void completionFailureOrForeignIdentityPreservesEvenPreviouslyEligibleRows() {
    UUID id = event(true);
    receipt(id);
    lifecycle.cleanup();
    when(clock.instant()).thenReturn(now.plus(Duration.ofDays(8)));
    UUID pending = event(false);
    doThrow(new org.springframework.dao.DataAccessResourceFailureException("조회 장애"))
        .when(completion)
        .completed(anySet());
    assertThatThrownBy(lifecycle::cleanup)
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(count()).isEqualTo(2);
    doReturn(Set.of(UUID.randomUUID())).when(completion).completed(anySet());
    assertThatThrownBy(lifecycle::cleanup).isInstanceOf(IllegalStateException.class);
    assertThat(count()).isEqualTo(2);
  }

  @Test
  void boundsBatchesAndDoesNotStarveCompletionsBehindPendingRows() {
    for (int i = 0; i < 100; i++) event(false);
    for (int i = 0; i < 101; i++) receipt(event(true));
    lifecycle.cleanup();
    lifecycle.cleanup();
    lifecycle.cleanup();
    when(clock.instant()).thenReturn(now.plus(Duration.ofDays(7)));
    assertThat(lifecycle.cleanup()).isEqualTo(100);
    assertThat(lifecycle.cleanup()).isEqualTo(1);
    assertThat(count()).isEqualTo(100);
  }

  @Test
  void terminalNeedsApprovalEvidenceAndExcludesReplayWithoutInventingReceipt() {
    UUID id = event(true);
    assertThatThrownBy(() -> lifecycle.terminate(Set.of(id), 1, "operator", "reason", "impact", ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(lifecycle.preview(Set.of(id))).hasSize(1);
    lifecycle.terminate(Set.of(id), 1, "operator", "reason", "impact", "복구 불가 확인");
    assertThat(lifecycle.preview(Set.of(id))).isEmpty();
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM notification_event_receipt").query(Long.class).single())
        .isZero();
    when(clock.instant()).thenReturn(now.plus(Duration.ofDays(7)));
    assertThat(lifecycle.cleanup()).isEqualTo(1);
  }

  @Test
  void rejectsUnknownOrChangedApprovedSetWithoutPartialDisposition() {
    UUID id = event(false);
    assertThatThrownBy(() -> lifecycle.preview(Set.of(id, UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> lifecycle.terminate(Set.of(id), 2, "operator", "reason", "impact", "evidence"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(lifecycle.preview(Set.of(id))).hasSize(1);
  }

  private UUID event(boolean published) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO community_event_outbox(event_id,event_type,event_version,post_id,conversation_id,payload,occurred_at,published_at) VALUES(:id,'CommunityConversationCreated',1,:post,:conversation,'{}'::jsonb,NOW(),CASE WHEN :published THEN NOW() ELSE NULL END)")
        .param("id", id)
        .param("post", UUID.randomUUID())
        .param("conversation", UUID.randomUUID())
        .param("published", published)
        .update();
    return id;
  }

  private void receipt(UUID id) {
    jdbc.sql("INSERT INTO notification_event_receipt(event_id) VALUES(:id)")
        .param("id", id)
        .update();
  }

  private long count() {
    return jdbc.sql("SELECT COUNT(*) FROM community_event_outbox").query(Long.class).single();
  }
}
