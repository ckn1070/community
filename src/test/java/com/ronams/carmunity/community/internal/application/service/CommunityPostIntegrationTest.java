package com.ronams.carmunity.community.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronams.carmunity.community.internal.application.CommunityErrorCode;
import com.ronams.carmunity.media.api.PostMediaUsage;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "carmunity.community.enabled=true",
      "carmunity.community.cursor-hmac-secret=integration-cursor-secret-that-is-at-least-32-bytes",
      "carmunity.community.media.bucket=test-bucket",
      "carmunity.community.media.role-arn=arn:aws:iam::000000000000:role/test",
      "carmunity.community.media.token-path=/tmp/nonexistent-test-token"
    })
class CommunityPostIntegrationTest extends PostgreSqlIntegrationTestSupport {

  @Autowired CommunityService service;
  @Autowired JdbcClient jdbc;
  @MockitoBean PostMediaUsage mediaUsage;

  private final ObjectMapper json = new ObjectMapper();
  private UUID author;
  private JsonNode body;

  @BeforeEach
  void setUp() throws Exception {
    author = account("post-lifecycle");
    body = json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"첫 본문\"}]}");
  }

  @Test
  void createsListsUpdatesAndDeletesPostWithVersionContract() throws Exception {
    UUID requestId = UUID.randomUUID();
    Map<String, Object> created = service.createPost(author, requestId, "  첫   제목  ", body);
    UUID postId = (UUID) created.get("id");

    assertThat(created).containsEntry("title", "첫 제목").containsEntry("version", 0L);
    assertThat(service.createPost(author, requestId, "첫 제목", body).get("id")).isEqualTo(postId);
    List<?> listedPosts = (List<?>) service.listPosts(0, 20, null).get("items");
    assertThat(
            listedPosts.stream().map(item -> ((Map<?, ?>) item).get("id")).anyMatch(postId::equals))
        .isTrue();

    JsonNode updatedBody =
        json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"바뀐 본문\"}]}");
    Map<String, Object> updated = service.updatePost(author, postId, 0L, "바뀐 제목", updatedBody);
    assertThat(updated)
        .containsEntry("title", "바뀐 제목")
        .containsEntry("version", 1L)
        .containsEntry("visibility", "VISIBLE");

    assertThatThrownBy(() -> service.updatePost(author, postId, 0L, "오래된 수정", null))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(CommunityErrorCode.PRECONDITION_FAILED));

    service.deleteContent(author, postId, "POST");
    assertThatThrownBy(() -> service.post(postId, null))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo(CommunityErrorCode.CONTENT_NOT_FOUND));
    service.deleteContent(author, postId, "POST");
  }

  @Test
  void rejectsReusedCreationKeyAndInvalidPage() {
    UUID requestId = UUID.randomUUID();
    service.createPost(author, requestId, "첫 제목", body);

    assertThatThrownBy(() -> service.createPost(author, requestId, "다른 제목", body))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(CommunityErrorCode.IDEMPOTENCY_KEY_REUSED));
    assertThatThrownBy(() -> service.listPosts(-1, 20, null))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(CommunityErrorCode.INVALID_PAGINATION_PARAMETER));
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
