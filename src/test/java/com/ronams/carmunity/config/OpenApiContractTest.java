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
              "500"));

  private static final Set<OperationKey> PROTECTED_OPERATIONS =
      Set.of(
          new OperationKey("put", "/api/v1/auth/password"),
          new OperationKey("post", "/api/v1/admin/invitations"),
          new OperationKey("delete", "/api/v1/admin/invitations/{invitationId}"));

  private static final Set<OperationKey> LOCATION_RESPONSE_OPERATIONS =
      Set.of(
          new OperationKey("post", "/api/v1/members"),
          new OperationKey("post", "/api/v1/admin/invitations"),
          new OperationKey("post", "/api/v1/admin/invitations/{invitationId}/acceptances"));

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

  /** 새 API가 추가되면 operation 수와 응답 집합이 달라져 실제 계약 검토 없이 명세가 통과하지 못합니다. */
  @Test
  void documentsEveryOperationWithItsActualResponsesAndSecurity() throws Exception {
    JsonNode document = generatedOpenApi();
    JsonNode paths = document.get("paths");

    assertThat(operationCount(paths)).isEqualTo(EXPECTED_RESPONSES.size());
    EXPECTED_RESPONSES.forEach(
        (key, expectedResponses) -> {
          JsonNode operation = paths.get(key.path()).get(key.method());
          assertThat(operation).as(key.toString()).isNotNull();
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
    if (!schema.isMissingNode()) {
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
