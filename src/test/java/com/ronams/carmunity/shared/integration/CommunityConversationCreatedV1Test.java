package com.ronams.carmunity.shared.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CommunityConversationCreatedV1Test {
  @Test
  void preservesTheVersionOneJsonContract() {
    var mapper = JsonMapper.builder().build();
    // Java 식별자나 record 구성에서 기대값을 생성하지 않고 공개 wire 계약을 literal로 고정합니다.
    String wire =
        """
        {"eventId":"00000000-0000-0000-0000-000000000001",
         "eventType":"CommunityConversationCreated","eventVersion":1,
         "occurredAt":"2026-09-03T00:00:00Z",
         "postId":"00000000-0000-0000-0000-000000000002",
         "conversationId":"00000000-0000-0000-0000-000000000003",
         "conversationKind":"COMMENT",
         "actorAccountId":"00000000-0000-0000-0000-000000000004",
         "postAuthorAccountId":"00000000-0000-0000-0000-000000000005",
         "directParentAuthorAccountId":null}
        """;
    var event =
        new CommunityConversationCreatedV1(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "CommunityConversationCreated",
            1,
            Instant.parse("2026-09-03T00:00:00Z"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            CommunityConversationCreatedV1.ConversationKind.COMMENT,
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            UUID.fromString("00000000-0000-0000-0000-000000000005"),
            null);
    assertThat(mapper.readTree(mapper.writeValueAsString(event))).isEqualTo(mapper.readTree(wire));
    assertThat(mapper.readValue(wire, CommunityConversationCreatedV1.class)).isEqualTo(event);
  }
}
