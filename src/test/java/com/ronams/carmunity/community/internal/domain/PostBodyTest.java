package com.ronams.carmunity.community.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostBodyTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void canonicalizesTextAndImageBlocks() throws Exception {
    UUID mediaId = UUID.randomUUID();
    JsonNode source =
        json.readTree(
            """
            {"version":1,"blocks":[
              {"type":"TEXT","text":" 첫   문장 "},
              {"type":"TEXT","text":"둘째 문장"},
              {"type":"IMAGE","mediaId":"%s","alt":"설명"}
            ]}
            """
                .formatted(mediaId));

    PostBody body = PostBody.from(source);

    assertThat(body.preview()).isEqualTo("첫 문장 둘째 문장");
    assertThat(body.mediaIds()).containsExactly(mediaId);
    assertThat(body.canonical().path("blocks")).hasSize(3);
    assertThat(body.canonical().path("blocks").get(2).path("alt").asText()).isEqualTo("설명");
  }

  @Test
  void rejectsUnsupportedShapeAndBlock() throws Exception {
    JsonNode missingBlocks = json.readTree("{\"version\":1}");
    JsonNode unsupportedBlock = json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"VIDEO\"}]}");
    JsonNode invalidText =
        json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":1}]}");

    assertThatThrownBy(() -> PostBody.from(missingBlocks))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PostBody.from(unsupportedBlock))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PostBody.from(invalidText))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBodyWithoutTextAndInvalidImageMetadata() throws Exception {
    JsonNode blankText =
        json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"   \"}]}");
    JsonNode invalidMediaId =
        json.readTree(
            "{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"},{\"type\":\"IMAGE\",\"mediaId\":\"invalid\"}]}");
    JsonNode invalidAlt =
        json.readTree(
            "{\"version\":1,\"blocks\":[{\"type\":\"TEXT\",\"text\":\"본문\"},{\"type\":\"IMAGE\",\"mediaId\":\""
                + UUID.randomUUID()
                + "\",\"alt\":1}]}");

    assertThatThrownBy(() -> PostBody.from(blankText)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PostBody.from(invalidMediaId))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PostBody.from(invalidAlt))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
