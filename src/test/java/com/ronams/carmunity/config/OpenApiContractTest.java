package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ronams.carmunity.account.internal.application.port.in.AcceptAdminInvitationUseCase;
import com.ronams.carmunity.account.internal.application.port.in.ChangePasswordUseCase;
import com.ronams.carmunity.account.internal.application.port.in.ConfirmMemberEmailVerificationUseCase;
import com.ronams.carmunity.account.internal.application.port.in.ConfirmPasswordResetUseCase;
import com.ronams.carmunity.account.internal.application.port.in.InviteAdminUseCase;
import com.ronams.carmunity.account.internal.application.port.in.RegisterMemberAccountUseCase;
import com.ronams.carmunity.account.internal.application.port.in.RequestMemberEmailVerificationUseCase;
import com.ronams.carmunity.account.internal.application.port.in.RequestPasswordResetUseCase;
import com.ronams.carmunity.account.internal.application.port.in.RevokeAdminInvitationUseCase;
import com.ronams.carmunity.auth.internal.application.port.in.CreatePasswordSessionUseCase;
import com.ronams.carmunity.auth.internal.application.port.in.LogoutUseCase;
import com.ronams.carmunity.auth.internal.application.port.in.RotateRefreshTokenUseCase;
import com.ronams.carmunity.catalog.internal.application.port.in.GetCatalogModelDetailUseCase;
import com.ronams.carmunity.catalog.internal.application.port.in.ListCatalogBrandsUseCase;
import com.ronams.carmunity.catalog.internal.application.port.in.SearchCatalogModelsUseCase;
import com.ronams.carmunity.community.internal.application.service.CommunityService;
import com.ronams.carmunity.media.api.MediaAvailability;
import com.ronams.carmunity.media.internal.application.MediaDeliveryBudget;
import com.ronams.carmunity.media.internal.application.MediaService;
import com.ronams.carmunity.moderation.internal.application.ModerationService;
import com.ronams.carmunity.shared.pagination.CursorTokens;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 생성 OpenAPI가 실제 Controller·예외 처리·Security 응답 계약에서 벗어나지 않도록 검증합니다. */
@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiErrorResponseFactory.class, OpenApiConfiguration.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@TestPropertySource(properties = "carmunity.community.enabled=true")
class OpenApiContractTest {

  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
  private static final String PROBLEM_SCHEMA = "#/components/schemas/ApiProblemResponse";

  private static final Map<OperationKey, Set<String>> EXPECTED_RESPONSES =
      Map.ofEntries(
          operation("post", "/api/v1/members", "201", "400", "409", "415", "500"),
          operation("post", "/api/v1/auth/sessions", "201", "400", "401", "403", "415", "500"),
          operation("post", "/api/v1/auth/sessions/refresh", "200", "400", "401", "415", "500"),
          operation("post", "/api/v1/auth/logout", "204", "400", "415", "500"),
          operation("post", "/api/v1/auth/verifications/email", "202", "400", "415", "500"),
          operation("post", "/api/v1/auth/verifications/email/confirm", "204", "400", "415", "500"),
          operation("post", "/api/v1/auth/password-resets", "202", "400", "415", "500"),
          operation("post", "/api/v1/auth/password-resets/confirm", "204", "400", "415", "500"),
          operation("put", "/api/v1/auth/password", "204", "400", "401", "403", "415", "500"),
          operation(
              "post", "/api/v1/admin/invitations", "201", "400", "401", "403", "409", "415", "500"),
          operation(
              "post",
              "/api/v1/admin/invitations/{invitationId}/acceptances",
              "201",
              "400",
              "404",
              "409",
              "415",
              "500"),
          operation(
              "delete",
              "/api/v1/admin/invitations/{invitationId}",
              "204",
              "401",
              "403",
              "404",
              "409",
              "500"),
          operation("get", "/api/v1/car/brands", "200", "405", "500"),
          operation("get", "/api/v1/car/models", "200", "400", "405", "500"),
          operation("get", "/api/v1/car/models/{carModelId}", "200", "400", "404", "405", "500"),
          operation("get", "/api/v1/boards/free/posts", "200", "500"),
          operation("post", "/api/v1/boards/free/posts", "201", "400", "401", "403", "415", "500"),
          operation("get", "/api/v1/posts/{postId}", "200", "500"),
          operation("patch", "/api/v1/posts/{postId}", "200", "400", "401", "403", "415", "500"),
          operation("delete", "/api/v1/posts/{postId}", "204", "401", "403", "500"),
          operation("get", "/api/v1/posts/{postId}/comments", "200", "500"),
          operation("get", "/api/v1/comments/{commentId}/replies", "200", "500"),
          operation(
              "post", "/api/v1/posts/{postId}/comments", "201", "400", "401", "403", "415", "500"),
          operation("delete", "/api/v1/comments/{commentId}", "204", "401", "403", "500"),
          operation(
              "patch", "/api/v1/comments/{commentId}", "200", "400", "401", "403", "415", "500"),
          operation(
              "put",
              "/api/v1/posts/{contentId}/reaction",
              "200",
              "400",
              "401",
              "403",
              "415",
              "500"),
          operation(
              "put",
              "/api/v1/comments/{contentId}/reaction",
              "200",
              "400",
              "401",
              "403",
              "415",
              "500"),
          operation("delete", "/api/v1/posts/{contentId}/reaction", "204", "401", "403", "500"),
          operation("delete", "/api/v1/comments/{contentId}/reaction", "204", "401", "403", "500"),
          operation("post", "/api/v1/posts/{targetId}/reports", "201", "401", "403", "500"),
          operation("post", "/api/v1/comments/{targetId}/reports", "201", "401", "403", "500"),
          operation("get", "/api/v1/admin/community/reports", "200", "401", "403", "500"),
          operation(
              "get", "/api/v1/admin/community/reports/{reportId}", "200", "401", "403", "500"),
          operation(
              "patch",
              "/api/v1/admin/community/reports/{reportId}",
              "200",
              "400",
              "401",
              "403",
              "415",
              "500"),
          operation("post", "/api/v1/media/images", "202", "400", "401", "403", "415", "500"),
          operation("get", "/api/v1/media/images/{mediaId}", "200", "401", "403", "500"),
          operation("get", "/api/v1/media/images/{mediaId}/content", "200", "500"),
          operation("get", "/api/v1/notifications", "200", "401", "403", "500"),
          operation(
              "patch",
              "/api/v1/notifications/{notificationId}",
              "200",
              "400",
              "401",
              "403",
              "415",
              "500"));

  private static final Set<OperationKey> CATALOG_OPERATIONS =
      Set.of(
          new OperationKey("get", "/api/v1/car/brands"),
          new OperationKey("get", "/api/v1/car/models"),
          new OperationKey("get", "/api/v1/car/models/{carModelId}"));

  private static final Map<String, Set<String>> CATALOG_DETAIL_NULLABLE_PROPERTIES =
      Map.ofEntries(
          Map.entry("CatalogModelDetailResponse", Set.of()),
          Map.entry("ModelResponse", Set.of("brandNameEn", "nameEn", "summary")),
          Map.entry("GenerationResponse", Set.of()),
          Map.entry("ModelYearResponse", Set.of("releasedOn", "salesEndedOn")),
          Map.entry(
              "PowertrainResponse",
              Set.of(
                  "secondaryFuelType",
                  "transmissionType",
                  "transmissionGears",
                  "systemMaxPowerKw",
                  "systemMaxPowerPs",
                  "systemMaxTorqueNm",
                  "zeroToHundredSeconds",
                  "topSpeedKmh",
                  "maxAcChargeKw",
                  "acChargeConnector",
                  "acChargeMinutes",
                  "acChargeFromPercent",
                  "acChargeToPercent",
                  "acChargePowerKw",
                  "maxDcChargeKw",
                  "dcChargeConnector",
                  "fastChargeMinutes",
                  "fastChargeFromPercent",
                  "fastChargeToPercent",
                  "fastChargePowerKw")),
          Map.entry(
              "ComponentResponse",
              Set.of(
                  "fuelType",
                  "displacementCc",
                  "cylinderCount",
                  "aspirationType",
                  "maxPowerKw",
                  "maxPowerPs",
                  "maxPowerRpm",
                  "maxTorqueNm",
                  "maxTorqueKgfM",
                  "maxTorqueRpmFrom",
                  "maxTorqueRpmTo",
                  "capacityKwh",
                  "usableCapacityKwh",
                  "capacityAh",
                  "voltageV",
                  "capacityL",
                  "capacityKg",
                  "pressureBar",
                  "manufacturerName",
                  "cellManufacturerName",
                  "productionCountryCode",
                  "cellType",
                  "chemistry")),
          Map.entry(
              "VariantResponse",
              Set.of(
                  "doorCount",
                  "frontTrackMm",
                  "rearTrackMm",
                  "groundClearanceMm",
                  "cargoCapacityL",
                  "maxCargoCapacityL",
                  "curbWeightKg",
                  "grossVehicleWeightKg",
                  "maxBrakedTowingCapacityKg",
                  "maxUnbrakedTowingCapacityKg",
                  "maxRoofLoadKg",
                  "minimumTurningRadiusM",
                  "wheelSizeInch",
                  "frontTire",
                  "rearTire",
                  "frontSuspension",
                  "rearSuspension",
                  "steeringType",
                  "frontBrake",
                  "rearBrake")),
          Map.entry(
              "CertificationResponse",
              Set.of(
                  "temperatureCondition",
                  "wheelSizeInch",
                  "tireSpecification",
                  "curbWeightKg",
                  "efficiencyGrade",
                  "cityEfficiency",
                  "highwayEfficiency",
                  "combinedEfficiency",
                  "cityRangeKm",
                  "highwayRangeKm",
                  "combinedRangeKm",
                  "co2GPerKm")),
          Map.entry("ConfigurationResponse", Set.of("salesEndedOn", "price")),
          Map.entry("PriceResponse", Set.of("priceAfterTaxBenefit")),
          Map.entry("OptionResponse", Set.of("category", "priceAfterTaxBenefit")),
          Map.entry("StandardFeatureResponse", Set.of("description")),
          Map.entry("WarrantyResponse", Set.of("years", "distanceKm", "note")),
          Map.entry("SafetyRatingResponse", Set.of("assessedOn", "note")),
          Map.entry("MediaResponse", Set.of("alt")));

  private static final Set<OperationKey> PROTECTED_OPERATIONS =
      Set.of(
          new OperationKey("put", "/api/v1/auth/password"),
          new OperationKey("post", "/api/v1/admin/invitations"),
          new OperationKey("delete", "/api/v1/admin/invitations/{invitationId}"),
          new OperationKey("post", "/api/v1/boards/free/posts"),
          new OperationKey("patch", "/api/v1/posts/{postId}"),
          new OperationKey("delete", "/api/v1/posts/{postId}"),
          new OperationKey("post", "/api/v1/posts/{postId}/comments"),
          new OperationKey("delete", "/api/v1/comments/{commentId}"),
          new OperationKey("patch", "/api/v1/comments/{commentId}"),
          new OperationKey("put", "/api/v1/posts/{contentId}/reaction"),
          new OperationKey("put", "/api/v1/comments/{contentId}/reaction"),
          new OperationKey("delete", "/api/v1/posts/{contentId}/reaction"),
          new OperationKey("delete", "/api/v1/comments/{contentId}/reaction"),
          new OperationKey("post", "/api/v1/posts/{targetId}/reports"),
          new OperationKey("post", "/api/v1/comments/{targetId}/reports"),
          new OperationKey("get", "/api/v1/admin/community/reports"),
          new OperationKey("get", "/api/v1/admin/community/reports/{reportId}"),
          new OperationKey("patch", "/api/v1/admin/community/reports/{reportId}"),
          new OperationKey("post", "/api/v1/media/images"),
          new OperationKey("get", "/api/v1/media/images/{mediaId}"),
          new OperationKey("get", "/api/v1/notifications"),
          new OperationKey("patch", "/api/v1/notifications/{notificationId}"));

  private static final Set<OperationKey> LOCATION_RESPONSE_OPERATIONS =
      Set.of(
          new OperationKey("post", "/api/v1/members"),
          new OperationKey("post", "/api/v1/admin/invitations"),
          new OperationKey("post", "/api/v1/admin/invitations/{invitationId}/acceptances"),
          new OperationKey("post", "/api/v1/boards/free/posts"),
          new OperationKey("post", "/api/v1/posts/{postId}/comments"),
          new OperationKey("post", "/api/v1/posts/{targetId}/reports"),
          new OperationKey("post", "/api/v1/comments/{targetId}/reports"));

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RegisterMemberAccountUseCase registerMemberAccountUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;
  @MockitoBean private RequestMemberEmailVerificationUseCase requestMemberEmailVerificationUseCase;
  @MockitoBean private ConfirmMemberEmailVerificationUseCase confirmMemberEmailVerificationUseCase;
  @MockitoBean private RequestPasswordResetUseCase requestPasswordResetUseCase;
  @MockitoBean private ConfirmPasswordResetUseCase confirmPasswordResetUseCase;
  @MockitoBean private InviteAdminUseCase inviteAdminUseCase;
  @MockitoBean private AcceptAdminInvitationUseCase acceptAdminInvitationUseCase;
  @MockitoBean private RevokeAdminInvitationUseCase revokeAdminInvitationUseCase;
  @MockitoBean private CreatePasswordSessionUseCase createPasswordSessionUseCase;
  @MockitoBean private RotateRefreshTokenUseCase rotateRefreshTokenUseCase;
  @MockitoBean private LogoutUseCase logoutUseCase;
  @MockitoBean private ListCatalogBrandsUseCase listCatalogBrandsUseCase;
  @MockitoBean private SearchCatalogModelsUseCase searchCatalogModelsUseCase;
  @MockitoBean private GetCatalogModelDetailUseCase getCatalogModelDetailUseCase;
  @MockitoBean private CommunityService communityService;
  @MockitoBean private MediaService mediaService;
  @MockitoBean private MediaDeliveryBudget mediaDeliveryBudget;
  @MockitoBean private MediaAvailability mediaAvailability;
  @MockitoBean private ModerationService moderationService;
  @MockitoBean private JdbcClient jdbcClient;
  @MockitoBean private CursorTokens cursorTokens;
  @MockitoBean private MeterRegistry meterRegistry;

  /** 새 API의 설명·응답·보안 계약을 검토하지 않으면 생성 명세 검증이 실패합니다. */
  @Test
  void documentsEveryOperationWithItsActualResponsesAndSecurity() throws Exception {
    JsonNode document = generatedOpenApi();
    JsonNode paths = document.get("paths");

    assertThat(operationCount(paths)).isEqualTo(EXPECTED_RESPONSES.size());
    EXPECTED_RESPONSES.forEach(
        (key, expectedResponses) -> {
          JsonNode operation = paths.get(key.path()).get(key.method());
          assertThat(operation).as(key.toString()).isNotNull();
          assertThat(operation.path("summary").stringValue()).as("%s summary", key).isNotBlank();
          assertThat(operation.path("description").stringValue())
              .as("%s description", key)
              .isNotBlank();
          assertThat(fieldNames(operation.get("responses")))
              .as("%s responses", key)
              .containsExactlyInAnyOrderElementsOf(expectedResponses);

          if (PROTECTED_OPERATIONS.contains(key)) {
            assertThat(operation.at("/security/0/bearerAuth").isArray())
                .as("%s bearer security", key)
                .isTrue();
          } else {
            assertThat(operation.has("security")).as("%s public security", key).isFalse();
          }

          operation
              .get("responses")
              .properties()
              .forEach(
                  response -> {
                    if (!response.getKey().startsWith("2")) {
                      assertThat(
                              response
                                  .getValue()
                                  .at("/content/application~1problem+json/schema/$ref")
                                  .stringValue())
                          .as("%s %s schema", key, response.getKey())
                          .isEqualTo(PROBLEM_SCHEMA);
                    }
                  });

          verifySuccessResponseContract(document, key, operation);
        });
  }

  /** 실제 Problem Details의 고정 필드와 validation 전용 errors extension을 schema로 노출합니다. */
  @Test
  void documentsActualProblemDetailExtensions() throws Exception {
    JsonNode document = generatedOpenApi();
    JsonNode problemSchema = document.at("/components/schemas/ApiProblemResponse");

    assertThat(fieldNames(problemSchema.get("properties")))
        .containsExactlyInAnyOrder(
            "type", "title", "status", "detail", "instance", "code", "errors");
    assertThat(arrayValues(problemSchema.get("required")))
        .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "code");
    assertThat(problemSchema.at("/properties/errors/items/$ref").stringValue())
        .isEqualTo("#/components/schemas/FieldErrorDetail");
    JsonNode fieldErrorSchema = document.at("/components/schemas/FieldErrorDetail");
    assertThat(fieldNames(fieldErrorSchema.get("properties")))
        .containsExactlyInAnyOrder("field", "code", "message");
    assertThat(arrayValues(fieldErrorSchema.get("required")))
        .containsExactlyInAnyOrder("field", "code", "message");
    assertThat(document.at("/components/securitySchemes/bearerAuth/type").stringValue())
        .isEqualTo("http");
    assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").stringValue())
        .isEqualTo("bearer");
    assertThat(document.at("/components/securitySchemes/bearerAuth/bearerFormat").stringValue())
        .isEqualTo("JWT");
  }

  /** 카탈로그 목록·상세 응답은 대표 연식과 실제 nullable 값의 구분을 생성 schema에 유지합니다. */
  @Test
  void documentsCatalogResponseShapeAndNullability() throws Exception {
    JsonNode document = generatedOpenApi();
    JsonNode brandSchema = document.at("/components/schemas/CatalogBrandResponse");
    assertThat(arrayValues(brandSchema.get("required")))
        .contains("brandId", "name", "countryCode", "onSaleModelCount")
        .doesNotContain("nameEn");
    assertThat(arrayValues(brandSchema.at("/properties/nameEn/type")))
        .containsExactlyInAnyOrder("string", "null");

    JsonNode pageSchema = document.at("/components/schemas/CatalogModelPageResponse");
    assertThat(arrayValues(pageSchema.get("required")))
        .containsExactlyInAnyOrder("items", "page", "size", "totalElements", "totalPages");
    JsonNode summarySchema = document.at("/components/schemas/CatalogModelSummaryResponse");
    assertThat(arrayValues(summarySchema.get("required")))
        .contains("modelId", "brandId", "name", "representativeModelYearId", "modelYear")
        .doesNotContain("nameEn", "releasedOn", "startPrice", "priceBasis", "currencyCode");
    assertThat(arrayValues(summarySchema.at("/properties/startPrice/type")))
        .containsExactlyInAnyOrder("integer", "null");

    JsonNode detailOperation = document.at("/paths/~1api~1v1~1car~1models~1{carModelId}/get");
    assertThat(detailOperation.at("/parameters/0/name").stringValue()).isEqualTo("carModelId");
    assertThat(
            detailOperation
                .at("/responses/200/content/application~1json/schema/$ref")
                .stringValue())
        .isEqualTo("#/components/schemas/CatalogModelDetailResponse");

    CATALOG_DETAIL_NULLABLE_PROPERTIES.forEach(
        (schemaName, nullableProperties) -> {
          JsonNode schema = document.at("/components/schemas/" + schemaName);
          Set<String> properties = fieldNames(schema.get("properties"));
          assertThat(arrayValues(schema.get("required")))
              .as("%s required fields", schemaName)
              .containsExactlyInAnyOrderElementsOf(
                  properties.stream()
                      .filter(property -> !nullableProperties.contains(property))
                      .collect(java.util.stream.Collectors.toSet()));
          nullableProperties.forEach(
              property ->
                  assertThat(arrayValues(schema.at("/properties/" + property + "/type")))
                      .as("%s.%s nullable type", schemaName, property)
                      .contains("null"));
        });
  }

  private JsonNode generatedOpenApi() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON_MAPPER.readTree(body);
  }

  private static void verifySuccessResponseContract(
      JsonNode document, OperationKey key, JsonNode operation) {
    JsonNode successResponse =
        operation.get("responses").properties().stream()
            .filter(response -> response.getKey().startsWith("2"))
            .findFirst()
            .orElseThrow()
            .getValue();
    JsonNode schema = successResponse.at("/content/application~1json/schema");
    if (!schema.isMissingNode() && schema.has("$ref") && !CATALOG_OPERATIONS.contains(key)) {
      String componentName = schema.get("$ref").stringValue().replace("#/components/schemas/", "");
      JsonNode component = document.at("/components/schemas/" + componentName);
      assertThat(arrayValues(component.get("required")))
          .as("%s required response fields", key)
          .containsExactlyInAnyOrderElementsOf(fieldNames(component.get("properties")));
    }

    if (LOCATION_RESPONSE_OPERATIONS.contains(key)) {
      assertThat(successResponse.at("/headers/Location/schema/type").stringValue())
          .as("%s Location header", key)
          .isEqualTo("string");
      assertThat(successResponse.at("/headers/Location/schema/format").stringValue())
          .isEqualTo("uri");
    } else {
      assertThat(successResponse.at("/headers/Location").isMissingNode())
          .as("%s unexpected Location header", key)
          .isTrue();
    }
  }

  private static Map.Entry<OperationKey, Set<String>> operation(
      String method, String path, String... responseCodes) {
    return Map.entry(new OperationKey(method, path), Set.of(responseCodes));
  }

  private static int operationCount(JsonNode paths) {
    int count = 0;
    for (Map.Entry<String, JsonNode> path : paths.properties()) {
      count += path.getValue().size();
    }
    return count;
  }

  private static Set<String> fieldNames(JsonNode node) {
    return node == null
        ? Set.of()
        : node.propertyStream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
  }

  private static Set<String> arrayValues(JsonNode node) {
    return node == null
        ? Set.of()
        : node.valueStream()
            .map(JsonNode::stringValue)
            .collect(java.util.stream.Collectors.toSet());
  }

  private record OperationKey(String method, String path) {}
}
