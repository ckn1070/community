package com.ronams.carmunity.catalog.internal.adapter.out.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetailCachePolicy;
import com.ronams.carmunity.catalog.internal.application.query.GetCatalogModelDetailQuery;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RedisCatalogModelDetailCacheAdapterTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-03T01:02:03Z"), ZoneOffset.UTC);
  private static final UUID MODEL_ID = UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a");
  private static final UUID MODEL_YEAR_ID = UUID.fromString("9a799fe5-3fab-466d-9fe8-ecc364f8413d");

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> values;

  @Test
  void query_identity를_versioned_key로_직렬화한다() {
    assertThat(CatalogModelDetailCacheKey.from(query(MODEL_YEAR_ID)).value())
        .isEqualTo(
            "carmunity:catalog:model-detail:v1:" + MODEL_ID + ":" + MODEL_YEAR_ID + ":2026-09-03");
    assertThat(CatalogModelDetailCacheKey.from(query(null)).value())
        .endsWith(":" + MODEL_ID + ":representative:2026-09-03");
  }

  @Test
  void JSON_envelope를_300초_이하_TTL로_저장하고_다시_읽는다() {
    when(redis.opsForValue()).thenReturn(values);
    RedisCatalogModelDetailCacheAdapter adapter = adapter(1_048_576);
    var query = query(MODEL_YEAR_ID);
    var detail = detail();

    adapter.put(query, detail);

    var json = org.mockito.ArgumentCaptor.forClass(String.class);
    var ttl = org.mockito.ArgumentCaptor.forClass(Duration.class);
    verify(values).set(anyString(), json.capture(), ttl.capture());
    assertThat(json.getValue()).contains("\"schemaVersion\":1", MODEL_ID.toString());
    assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(5));

    when(values.get(anyString())).thenReturn(json.getValue());
    assertThat(adapter.find(query)).contains(detail);
  }

  @Test
  void adapter_전용_document가_application_model을_명시적으로_왕복_mapping한다() {
    var query = query(MODEL_YEAR_ID);
    var detail = detail();

    CatalogModelDetailCacheDocument document =
        CatalogModelDetailCacheDocument.from(
            query, CLOCK.instant(), CLOCK.instant().plusSeconds(270), detail);

    assertThat(document.matches(query, CLOCK.instant())).isTrue();
    assertThat(document.toModel()).isEqualTo(detail);
  }

  @Test
  void 손상_document는_삭제하고_miss로_처리한다() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenReturn("{broken");
    RedisCatalogModelDetailCacheAdapter adapter = adapter(1_048_576);

    assertThat(adapter.find(query(null))).isEmpty();
    verify(redis).delete(CatalogModelDetailCacheKey.from(query(null)).value());
  }

  @Test
  void 필수_identity가_없는_JSON_document도_삭제하고_miss로_처리한다() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString()))
        .thenReturn(
            "{\"schemaVersion\":1,\"modelId\":null,\"requestedModelYearId\":null,"
                + "\"asOf\":null,\"cachedAt\":null,\"expiresAt\":null,\"detail\":null}");
    RedisCatalogModelDetailCacheAdapter adapter = adapter(1_048_576);

    assertThat(adapter.find(query(null))).isEmpty();
    verify(redis).delete(CatalogModelDetailCacheKey.from(query(null)).value());
  }

  @Test
  void envelope와_상세_model_identity가_다르거나_필수_field가_없으면_삭제한다() throws Exception {
    when(redis.opsForValue()).thenReturn(values);
    var query = query(MODEL_YEAR_ID);
    var valid =
        CatalogModelDetailCacheDocument.from(
            query, CLOCK.instant(), CLOCK.instant().plusSeconds(270), detail());
    var validModel = valid.detail().model();
    RedisCatalogModelDetailCacheAdapter adapter = adapter(1_048_576);

    for (boolean mismatchedIdentity : List.of(true, false)) {
      var invalidModel =
          new CatalogModelDetailCacheDocument.Model(
              mismatchedIdentity ? UUID.randomUUID() : validModel.modelId(),
              mismatchedIdentity ? validModel.brandId() : null,
              validModel.brandName(),
              validModel.brandNameEn(),
              validModel.name(),
              validModel.nameEn(),
              validModel.summary());
      var invalidDetail =
          new CatalogModelDetailCacheDocument.Detail(
              invalidModel,
              valid.detail().generations(),
              valid.detail().modelYears(),
              valid.detail().selectedModelYear(),
              valid.detail().powertrains(),
              valid.detail().variants());
      var invalid =
          new CatalogModelDetailCacheDocument(
              valid.schemaVersion(),
              valid.modelId(),
              valid.requestedModelYearId(),
              valid.asOf(),
              valid.cachedAt(),
              valid.expiresAt(),
              invalidDetail);
      when(values.get(anyString()))
          .thenReturn(JsonMapper.builder().findAndAddModules().build().writeValueAsString(invalid));

      assertThat(adapter.find(query)).isEmpty();
    }
    verify(redis, times(2)).delete(CatalogModelDetailCacheKey.from(query).value());
  }

  @Test
  void entry_상한을_넘으면_저장하지_않는다() {
    RedisCatalogModelDetailCacheAdapter adapter = adapter(10);

    adapter.put(query(null), detail());

    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void 전체_graph의_필수_field_누락과_null은_삭제하고_nullable은_허용한다() {
    when(redis.opsForValue()).thenReturn(values);
    JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    var query = query(MODEL_YEAR_ID);
    ObjectNode valid =
        (ObjectNode)
            json.valueToTree(
                CatalogModelDetailCacheDocument.from(
                    query, CLOCK.instant(), CLOCK.instant().plusSeconds(270), detail()));
    ObjectNode graph = (ObjectNode) valid.get("detail");
    graph.set(
        "generations",
        json.readTree(
            """
        [{"generationId":"11111111-1111-4111-8111-111111111111",
          "name":"1세대", "changeType":"FULL_CHANGE"}]
        """));
    graph.set(
        "powertrains",
        json.readTree(
            """
        [{"powertrainId":"22222222-2222-4222-8222-222222222222", "name":"가솔린",
          "propulsionType":"ICE", "primaryFuelType":"GASOLINE", "drivetrainType":"FWD",
          "components":[{"componentType":"ENGINE", "componentRole":"PRIMARY",
                         "position":"FRONT", "name":"엔진"}]}]
        """));
    graph.set(
        "variants",
        json.readTree(
            """
        [{"variantId":"33333333-3333-4333-8333-333333333333",
          "powertrainId":"22222222-2222-4222-8222-222222222222", "name":"세단",
          "bodyType":"SEDAN", "roofType":"FIXED", "seatingCapacity":5,
          "lengthMm":4900, "widthMm":1800, "heightMm":1400, "wheelbaseMm":2800,
          "certifications":[{"conditionCode":"BASE", "conditionName":"기본",
            "certificationType":"FUEL_EFFICIENCY", "certificationStandard":"KR",
            "builtInCamera":false, "efficiencyUnit":"KM_PER_L"}],
          "configurations":[{"configurationId":"44444444-4444-4444-8444-444444444444",
            "trimName":"기본", "salesChannel":"RETAIL", "salesStartedOn":"2026-01-01",
            "salesStatus":"ON_SALE", "displayOrder":0,
            "standardFeatures":[{"category":"SAFETY", "name":"안전장치"}],
            "warranties":[{"name":"보증"}],
            "safetyRatings":[{"organization":"KNCAP", "rating":"1"}],
            "media":[{"type":"IMAGE", "url":"https://example.com/car.png", "sortOrder":0}],
            "price":{"effectiveFrom":"2026-01-01", "priceBeforeTaxBenefit":30000000,
              "displayPrice":30000000, "priceBasis":"BEFORE_TAX_BENEFIT", "currencyCode":"KRW"},
            "options":[{"code":"OPTION", "name":"선택품목", "optionType":"SINGLE",
              "priceBeforeTaxBenefit":0, "displayPrice":0, "priceBasis":"BEFORE_TAX_BENEFIT",
              "currencyCode":"KRW", "includedItems":[], "effectiveFrom":"2026-01-01",
              "displayOrder":0}]}]}]
        """));
    RedisCatalogModelDetailCacheAdapter adapter = adapter(1_048_576);
    when(values.get(anyString())).thenReturn(json.writeValueAsString(valid));
    // Optional field를 생략한 실제 graph와 0·false 필수값은 정상입니다.
    assertThat(adapter.find(query)).isPresent();

    Map<String, List<String>> required =
        Map.ofEntries(
            Map.entry("/detail/model", List.of("modelId", "brandId", "brandName", "name")),
            Map.entry("/detail/generations/0", List.of("generationId", "name", "changeType")),
            Map.entry(
                "/detail/modelYears/0",
                List.of(
                    "modelYearId", "modelYear", "editionName", "salesStartedOn", "salesStatus")),
            Map.entry(
                "/detail/selectedModelYear",
                List.of(
                    "modelYearId", "modelYear", "editionName", "salesStartedOn", "salesStatus")),
            Map.entry(
                "/detail/powertrains/0",
                List.of(
                    "powertrainId",
                    "name",
                    "propulsionType",
                    "primaryFuelType",
                    "drivetrainType",
                    "components")),
            Map.entry(
                "/detail/powertrains/0/components/0",
                List.of("componentType", "componentRole", "position", "name")),
            Map.entry(
                "/detail/variants/0",
                List.of(
                    "variantId",
                    "powertrainId",
                    "name",
                    "bodyType",
                    "roofType",
                    "seatingCapacity",
                    "lengthMm",
                    "widthMm",
                    "heightMm",
                    "wheelbaseMm",
                    "certifications",
                    "configurations")),
            Map.entry(
                "/detail/variants/0/certifications/0",
                List.of(
                    "conditionCode",
                    "conditionName",
                    "certificationType",
                    "certificationStandard",
                    "builtInCamera",
                    "efficiencyUnit")),
            Map.entry(
                "/detail/variants/0/configurations/0",
                List.of(
                    "configurationId",
                    "trimName",
                    "salesChannel",
                    "salesStartedOn",
                    "salesStatus",
                    "displayOrder",
                    "standardFeatures",
                    "warranties",
                    "safetyRatings",
                    "media",
                    "options")),
            Map.entry(
                "/detail/variants/0/configurations/0/standardFeatures/0",
                List.of("category", "name")),
            Map.entry("/detail/variants/0/configurations/0/warranties/0", List.of("name")),
            Map.entry(
                "/detail/variants/0/configurations/0/safetyRatings/0",
                List.of("organization", "rating")),
            Map.entry(
                "/detail/variants/0/configurations/0/media/0", List.of("type", "url", "sortOrder")),
            Map.entry(
                "/detail/variants/0/configurations/0/price",
                List.of(
                    "effectiveFrom",
                    "priceBeforeTaxBenefit",
                    "displayPrice",
                    "priceBasis",
                    "currencyCode")),
            Map.entry(
                "/detail/variants/0/configurations/0/options/0",
                List.of(
                    "code",
                    "name",
                    "optionType",
                    "priceBeforeTaxBenefit",
                    "displayPrice",
                    "priceBasis",
                    "currencyCode",
                    "includedItems",
                    "effectiveFrom",
                    "displayOrder")));
    int invalidDocuments = 0;
    for (var fields : required.entrySet()) {
      for (String field : fields.getValue()) {
        for (boolean explicitNull : List.of(false, true)) {
          ObjectNode corrupt = valid.deepCopy();
          ObjectNode object = (ObjectNode) corrupt.at(fields.getKey());
          if (explicitNull) {
            object.putNull(field);
          } else {
            object.remove(field);
          }
          when(values.get(anyString())).thenReturn(json.writeValueAsString(corrupt));
          assertThat(adapter.find(query))
              .as("%s/%s null=%s", fields.getKey(), field, explicitNull)
              .isEmpty();
          invalidDocuments++;
        }
      }
    }
    verify(redis, times(invalidDocuments)).delete(CatalogModelDetailCacheKey.from(query).value());
  }

  @Test
  void Redis_operation_duration_outcome과_entry_byte_분포를_기록한다() {
    when(redis.opsForValue()).thenReturn(values);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    RedisCatalogModelDetailCacheAdapter adapter = adapter(1_048_576, meters);

    adapter.put(query(null), detail());
    when(values.get(anyString())).thenReturn(null);
    adapter.find(query(null));

    assertThat(meters.get("catalog.model.detail.cache.entry.bytes").summary().count()).isEqualTo(1);
    assertThat(
            meters
                .get("catalog.model.detail.cache.operation.duration")
                .tags("operation", "write", "outcome", "stored")
                .timer()
                .count())
        .isEqualTo(1);
    assertThat(
            meters
                .get("catalog.model.detail.cache.operation.duration")
                .tags("operation", "read", "outcome", "miss")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void Redis_read_write_실패는_retry하거나_API로_전파하지_않는다() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenThrow(new QueryTimeoutException("timeout"));
    RedisCatalogModelDetailCacheAdapter adapter = adapter(1_048_576);

    assertThat(adapter.find(query(null))).isEmpty();
    verify(values).get(anyString());

    org.mockito.Mockito.doThrow(new QueryTimeoutException("timeout"))
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));
    assertThatCode(() -> adapter.put(query(null), detail())).doesNotThrowAnyException();
  }

  @Test
  void 반복_Redis_실패_log를_rate_limit하고_민감정보를_남기지_않는다(CapturedOutput output) {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString()))
        .thenThrow(new QueryTimeoutException("SECRET-MUST-NOT-APPEAR-IN-LOG"));
    RedisCatalogModelDetailCacheAdapter adapter = adapter(1_048_576);

    adapter.find(query(null));
    adapter.find(query(null));

    assertThat(output).doesNotContain("SECRET-MUST-NOT-APPEAR-IN-LOG");
    assertThat(output.getOut() + output.getErr())
        .containsOnlyOnce("Catalog 모델 상세 Redis cache 실패 요약");
  }

  @Test
  void read_circuit가_열려도_write_circuit은_독립적으로_동작한다() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenThrow(new QueryTimeoutException("timeout"));
    CircuitBreakerConfig circuitConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofHours(1))
            .build();
    CircuitBreaker read = CircuitBreaker.of("read", circuitConfig);
    CircuitBreaker write = CircuitBreaker.of("write", circuitConfig);
    RedisCatalogModelDetailCacheAdapter adapter = adapter(1_048_576, read, write);

    assertThat(adapter.find(query(null))).isEmpty();
    assertThat(adapter.find(query(null))).isEmpty();
    assertThat(adapter.find(query(null))).isEmpty();

    assertThat(read.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    verify(values, times(2)).get(anyString());
    adapter.put(query(null), detail());
    verify(values).set(anyString(), anyString(), any(Duration.class));
    assertThat(write.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  void halfOpen_read_probe가_성공하면_closed로_복구한다() {
    when(redis.opsForValue()).thenReturn(values);
    CircuitBreakerConfig circuitConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(50)
            .permittedNumberOfCallsInHalfOpenState(1)
            .waitDurationInOpenState(Duration.ofHours(1))
            .build();
    CircuitBreaker read = CircuitBreaker.of("read-half-open", circuitConfig);
    CircuitBreaker write = CircuitBreaker.of("write-half-open", circuitConfig);
    RedisCatalogModelDetailCacheAdapter adapter = adapter(1_048_576, read, write);
    read.transitionToOpenState();
    read.transitionToHalfOpenState();

    assertThat(adapter.find(query(null))).isEmpty();

    assertThat(read.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    verify(values).get(anyString());
  }

  @Test
  void halfOpen_write_probe가_성공하면_closed로_복구한다() {
    when(redis.opsForValue()).thenReturn(values);
    CircuitBreakerConfig circuitConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(50)
            .permittedNumberOfCallsInHalfOpenState(1)
            .waitDurationInOpenState(Duration.ofHours(1))
            .build();
    CircuitBreaker read = CircuitBreaker.of("read-write-half-open", circuitConfig);
    CircuitBreaker write = CircuitBreaker.of("write-half-open", circuitConfig);
    RedisCatalogModelDetailCacheAdapter adapter = adapter(1_048_576, read, write);
    write.transitionToOpenState();
    write.transitionToHalfOpenState();

    adapter.put(query(null), detail());

    assertThat(write.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    verify(values).set(anyString(), anyString(), any(Duration.class));
  }

  private RedisCatalogModelDetailCacheAdapter adapter(int maxEntryBytes) {
    return adapter(maxEntryBytes, new SimpleMeterRegistry());
  }

  private RedisCatalogModelDetailCacheAdapter adapter(
      int maxEntryBytes, SimpleMeterRegistry meters) {
    return adapter(
        maxEntryBytes,
        meters,
        CircuitBreaker.ofDefaults("catalog-model-detail-read-test"),
        CircuitBreaker.ofDefaults("catalog-model-detail-write-test"));
  }

  private RedisCatalogModelDetailCacheAdapter adapter(
      int maxEntryBytes, CircuitBreaker readCircuit, CircuitBreaker writeCircuit) {
    return adapter(maxEntryBytes, new SimpleMeterRegistry(), readCircuit, writeCircuit);
  }

  private RedisCatalogModelDetailCacheAdapter adapter(
      int maxEntryBytes,
      SimpleMeterRegistry meters,
      CircuitBreaker readCircuit,
      CircuitBreaker writeCircuit) {
    var policy =
        new CatalogModelDetailCachePolicy(
            true,
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            maxEntryBytes,
            8,
            4,
            Duration.ofMillis(100));
    return new RedisCatalogModelDetailCacheAdapter(
        redis,
        JsonMapper.builder().findAndAddModules().build(),
        policy,
        CLOCK,
        meters,
        readCircuit,
        writeCircuit,
        bound -> bound);
  }

  private static GetCatalogModelDetailQuery query(UUID modelYearId) {
    return new GetCatalogModelDetailQuery(MODEL_ID, modelYearId, LocalDate.of(2026, 9, 3));
  }

  private static CatalogModelDetail detail() {
    CatalogModelDetail.ModelYear modelYear =
        new CatalogModelDetail.ModelYear(
            MODEL_YEAR_ID,
            2026,
            "2026 기본형",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 1),
            null,
            SalesStatus.ON_SALE);
    return new CatalogModelDetail(
        new CatalogModelDetail.Model(MODEL_ID, UUID.randomUUID(), "기아", "Kia", "EV6", null, null),
        List.of(),
        List.of(modelYear),
        modelYear,
        List.of(),
        List.of());
  }
}
