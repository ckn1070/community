package com.ronams.carmunity.community.internal.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class CommunitySchemaIntegrationTest extends PostgreSqlIntegrationTestSupport {

  @Autowired JdbcClient jdbc;

  @Test
  void createsCoreTablesAndFixedFreeBoard() {
    assertThat(
            jdbc.sql("SELECT public_id FROM community_board WHERE slug = 'free'")
                .query(UUID.class)
                .single())
        .isEqualTo(UUID.fromString("ed710d1c-91f1-4266-a035-bba33a7f52cb"));

    assertThat(
            jdbc.sql(
                    """
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name IN ('community_board', 'community_content',
                                         'community_post', 'community_comment', 'community_reaction')
                    """)
                .query(Integer.class)
                .single())
        .isEqualTo(5);
  }

  @Test
  @Transactional
  void rejectsUnknownAccountAndInvalidPostShape() {
    var invalidInsert =
        jdbc.sql(
                """
                INSERT INTO community_content
                  (public_id, kind, account_public_id, board_id,
                   creation_request_id, creation_request_hash)
                VALUES (:publicId, 'POST', :accountId, NULL, :requestId, :hash)
                """)
            .param("publicId", UUID.randomUUID())
            .param("accountId", UUID.randomUUID())
            .param("requestId", UUID.randomUUID())
            .param("hash", "a".repeat(64));
    assertThatThrownBy(invalidInsert::update).isInstanceOf(RuntimeException.class);
  }
}
