package com.ronams.carmunity.community.internal.adapter.out.persistence;

import com.ronams.carmunity.community.internal.application.port.out.ConversationOutboxDeliveryPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 단일 SQL의 row lock으로 claim하고 만료·재할당된 owner의 갱신을 fencing합니다. */
@Repository
@RequiredArgsConstructor
class JdbcConversationOutboxDeliveryAdapter implements ConversationOutboxDeliveryPort {
  private final JdbcClient jdbc;

  @Override
  public Optional<Claim> claim() {
    UUID token = UUID.randomUUID();
    return jdbc.sql(
            """
        UPDATE community_event_outbox SET lease_token=:token, lease_until=NOW()+INTERVAL '30 seconds',
          publish_attempts=publish_attempts+1
        WHERE id=(SELECT id FROM community_event_outbox
          WHERE published_at IS NULL AND terminal_at IS NULL AND next_attempt_at<=NOW()
            AND (lease_until IS NULL OR lease_until<=NOW())
          ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1)
        RETURNING event_id, lease_token, post_id, payload::text
        """)
        .param("token", token)
        .query(
            (rs, row) ->
                new Claim(
                    rs.getObject("event_id", UUID.class),
                    rs.getObject("lease_token", UUID.class),
                    rs.getObject("post_id", UUID.class),
                    rs.getString("payload")))
        .optional();
  }

  @Override
  public boolean published(Claim claim) {
    return jdbc.sql(
                """
        UPDATE community_event_outbox SET published_at=NOW(), lease_token=NULL, lease_until=NULL, last_publish_error=NULL
        WHERE event_id=:event AND lease_token=:token AND lease_until>NOW()
        """)
            .param("event", claim.eventId())
            .param("token", claim.leaseToken())
            .update()
        == 1;
  }

  @Override
  public boolean retry(Claim claim, String failureClass) {
    return jdbc.sql(
                """
        UPDATE community_event_outbox SET lease_token=NULL, lease_until=NULL,
          next_attempt_at=NOW()+INTERVAL '1 second', last_publish_error=:failure
        WHERE event_id=:event AND lease_token=:token AND lease_until>NOW()
        """)
            .param("event", claim.eventId())
            .param("token", claim.leaseToken())
            .param("failure", failureClass)
            .update()
        == 1;
  }
}
