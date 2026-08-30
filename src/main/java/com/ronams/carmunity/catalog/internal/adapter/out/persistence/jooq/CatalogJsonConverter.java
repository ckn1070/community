package com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Media;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.SafetyRating;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.StandardFeature;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Warranty;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 검증된 catalog JSONB 배열을 application read model 값으로 변환합니다. */
@Component
final class CatalogJsonConverter {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  List<StandardFeature> standardFeatures(String json) {
    return jsonArray(json).stream()
        .map(
            node ->
                new StandardFeature(
                    text(node, "category"), text(node, "name"), optionalText(node, "description")))
        .toList();
  }

  List<Warranty> warranties(String json) {
    return jsonArray(json).stream()
        .map(
            node ->
                new Warranty(
                    text(node, "name"),
                    optionalInteger(node, "years"),
                    optionalInteger(node, "distanceKm"),
                    optionalText(node, "note")))
        .toList();
  }

  List<SafetyRating> safetyRatings(String json) {
    return jsonArray(json).stream()
        .map(
            node ->
                new SafetyRating(
                    text(node, "organization"),
                    text(node, "rating"),
                    optionalDate(node, "assessedOn"),
                    optionalText(node, "note")))
        .toList();
  }

  List<Media> media(String json) {
    return jsonArray(json).stream()
        .map(
            node ->
                new Media(
                    text(node, "type"),
                    text(node, "url"),
                    text(node, "alt"),
                    node.get("sortOrder").intValue()))
        .toList();
  }

  List<String> stringArray(String json) {
    return jsonArray(json).stream().map(JsonNode::stringValue).toList();
  }

  private List<JsonNode> jsonArray(String json) {
    try {
      return jsonMapper.readTree(json).valueStream().toList();
    } catch (Exception exception) {
      throw new IllegalStateException("검증을 통과한 catalog JSON을 읽을 수 없습니다.", exception);
    }
  }

  private String text(JsonNode node, String field) {
    return node.get(field).stringValue();
  }

  private String optionalText(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.stringValue();
  }

  private Integer optionalInteger(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.intValue();
  }

  private LocalDate optionalDate(JsonNode node, String field) {
    String value = optionalText(node, field);
    return value == null ? null : LocalDate.parse(value);
  }
}
