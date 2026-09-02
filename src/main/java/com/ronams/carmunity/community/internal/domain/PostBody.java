package com.ronams.carmunity.community.internal.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Type: Value Object. Version 1 게시글 body block 검증, preview와 media 참조 추출을 수행합니다. */
public record PostBody(JsonNode canonical, String preview, Set<UUID> mediaIds) {
  private static final String BLOCKS_FIELD = "blocks";
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  public static PostBody from(JsonNode body) {
    if (body == null || body.path("version").asInt(-1) != 1 || !body.path(BLOCKS_FIELD).isArray()) {
      throw new IllegalArgumentException("지원하지 않는 게시글 본문 형식입니다.");
    }
    StringBuilder text = new StringBuilder();
    Set<UUID> media = new LinkedHashSet<>();
    var canonicalBlocks = JsonNodeFactory.instance.arrayNode();
    int imageCount = 0;
    for (JsonNode block : body.path(BLOCKS_FIELD)) {
      String type = block.path("type").asText();
      if ("TEXT".equals(type)) {
        appendTextBlock(block, canonicalBlocks, text);
      } else if ("IMAGE".equals(type)) {
        imageCount++;
        appendImageBlock(block, canonicalBlocks, media);
      } else {
        throw new IllegalArgumentException("지원하지 않는 본문 block입니다.");
      }
    }
    if (text.isEmpty() || text.codePointCount(0, text.length()) > 20_000 || imageCount > 10) {
      throw new IllegalArgumentException("본문은 텍스트를 포함하고 이미지 10개 이하여야 합니다.");
    }
    String preview =
        text.codePoints()
            .limit(200)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    var canonical = JsonNodeFactory.instance.objectNode();
    canonical.put("version", 1);
    canonical.set(BLOCKS_FIELD, canonicalBlocks);
    return new PostBody(canonical, preview, Set.copyOf(media));
  }

  private static void appendTextBlock(
      JsonNode block,
      com.fasterxml.jackson.databind.node.ArrayNode canonicalBlocks,
      StringBuilder text) {
    if (!block.path("text").isTextual()) {
      throw new IllegalArgumentException("TEXT block의 text가 올바르지 않습니다.");
    }
    String value = block.path("text").textValue();
    if (value.isBlank()) {
      return;
    }
    canonicalBlocks.addObject().put("type", "TEXT").put("text", value);
    if (!text.isEmpty()) {
      text.append(' ');
    }
    text.append(WHITESPACE.matcher(value.strip()).replaceAll(" "));
  }

  private static void appendImageBlock(
      JsonNode block,
      com.fasterxml.jackson.databind.node.ArrayNode canonicalBlocks,
      Set<UUID> media) {
    try {
      UUID mediaId = UUID.fromString(block.path("mediaId").asText());
      media.add(mediaId);
      var image =
          canonicalBlocks.addObject().put("type", "IMAGE").put("mediaId", mediaId.toString());
      if (!block.has("alt")) {
        return;
      }
      if (!block.path("alt").isTextual()) {
        throw new IllegalArgumentException("IMAGE block의 alt가 올바르지 않습니다.");
      }
      image.put("alt", block.path("alt").textValue());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("이미지 식별자가 올바르지 않습니다.", exception);
    }
  }
}
