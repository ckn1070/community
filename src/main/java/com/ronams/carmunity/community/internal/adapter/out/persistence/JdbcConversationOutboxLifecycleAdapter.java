package com.ronams.carmunity.community.internal.adapter.out.persistence;

import com.ronams.carmunity.community.internal.application.port.out.ConversationOutboxLifecyclePort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcConversationOutboxLifecycleAdapter implements ConversationOutboxLifecyclePort {
  private final JdbcClient jdbc;

  @Override
  public Set<UUID> nextCompletionBatch() {
    // 미완료인 첫 batch가 뒤의 완료 event를 영구히 가리지 않도록 조회 순서를 순환합니다.
    return Set.copyOf(
        jdbc.sql(
                "SELECT event_id FROM community_event_outbox WHERE eligible_at IS NULL ORDER BY completion_checked_at NULLS FIRST,id LIMIT 100")
            .query(UUID.class)
            .list());
  }

  @Override
  public void checked(Set<UUID> checked, Set<UUID> completed, Instant now) {
    if (!checked.isEmpty())
      jdbc.sql(
              "UPDATE community_event_outbox SET completion_checked_at=:now WHERE event_id IN (:ids)")
          .param("now", Timestamp.from(now))
          .param("ids", checked)
          .update();
    if (!completed.isEmpty())
      jdbc.sql(
              "UPDATE community_event_outbox SET eligible_at=COALESCE(eligible_at,:now) WHERE event_id IN (:ids)")
          .param("now", Timestamp.from(now))
          .param("ids", completed)
          .update();
  }

  @Override
  public int deleteEligible(Instant cutoff) {
    return jdbc.sql(
            "DELETE FROM community_event_outbox WHERE id IN (SELECT id FROM community_event_outbox WHERE eligible_at<=:cutoff AND (lease_until IS NULL OR lease_until<=NOW()) ORDER BY eligible_at,id FOR UPDATE SKIP LOCKED LIMIT 100)")
        .param("cutoff", Timestamp.from(cutoff))
        .update();
  }

  @Override
  public List<Original> originals(Set<UUID> ids) {
    return jdbc.sql(
            "SELECT event_id,post_id,payload::text,terminal_at IS NOT NULL AS terminal FROM community_event_outbox WHERE event_id IN (:ids) ORDER BY id")
        .param("ids", ids)
        .query(
            (rs, row) ->
                new Original(
                    rs.getObject("event_id", UUID.class),
                    rs.getObject("post_id", UUID.class),
                    rs.getString("payload"),
                    rs.getBoolean("terminal")))
        .list();
  }

  @Override
  public int terminate(
      Set<UUID> ids, Instant now, String operator, String reason, String impact, String evidence) {
    return jdbc.sql(
            "UPDATE community_event_outbox SET terminal_at=:now,eligible_at=COALESCE(eligible_at,:now),terminal_operator=:operator,terminal_reason=:reason,terminal_impact=:impact,terminal_evidence=:evidence WHERE event_id IN (:ids) AND terminal_at IS NULL AND (lease_until IS NULL OR lease_until<=NOW())")
        .param("now", Timestamp.from(now))
        .param("ids", ids)
        .param("operator", operator)
        .param("reason", reason)
        .param("impact", impact)
        .param("evidence", evidence)
        .update();
  }
}
