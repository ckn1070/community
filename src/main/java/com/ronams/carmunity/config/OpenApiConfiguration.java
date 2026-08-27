package com.ronams.carmunity.config;

import com.ronams.carmunity.shared.error.ApiProblemResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/** 실제 MVC·Security 응답 규칙을 생성 OpenAPI의 모든 operation에 적용합니다. */
@Configuration
public class OpenApiConfiguration {

  static final String BEARER_AUTH = "bearerAuth";
  private static final String PROBLEM_SCHEMA_NAME = "ApiProblemResponse";
  private static final String PROBLEM_SCHEMA_REF = "#/components/schemas/" + PROBLEM_SCHEMA_NAME;

  /** Bearer JWT scheme을 재사용 가능한 component로 등록합니다. */
  @Bean
  OpenAPI apiOpenApi() {
    Components components = new Components();
    components.addSecuritySchemes(
        BEARER_AUTH,
        new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("Bearer JWT access token"));
    return new OpenAPI().components(components);
  }

  /** 공통 오류와 인증 계약을 operation별 annotation 누락과 무관하게 생성 명세에 합성합니다. */
  @Bean
  OpenApiCustomizer actualApiResponseCustomizer() {
    return openApi -> {
      ModelConverters.getInstance()
          .readAll(ApiProblemResponse.class)
          .forEach(openApi.getComponents()::addSchemas);
      openApi
          .getPaths()
          .forEach(
              (path, pathItem) ->
                  pathItem
                      .readOperationsMap()
                      .forEach(
                          (method, operation) -> {
                            boolean publicOperation = isPublic(method, path);
                            if (!publicOperation) {
                              operation.setSecurity(
                                  List.of(new SecurityRequirement().addList(BEARER_AUTH)));
                            }

                            ApiResponses responses = operation.getResponses();
                            if (operation.getRequestBody() != null) {
                              responses.putIfAbsent("400", problemResponse("요청 형식 또는 값이 올바르지 않음"));
                              responses.putIfAbsent("415", problemResponse("지원하지 않는 Content-Type"));
                            }
                            if (!publicOperation) {
                              responses.putIfAbsent("401", problemResponse("인증 필요"));
                              responses.putIfAbsent("403", problemResponse("접근 권한 부족"));
                            }
                            responses.putIfAbsent("500", problemResponse("예상하지 못한 서버 오류"));
                            replaceErrorSchemas(responses);
                          }));
    };
  }

  private boolean isPublic(PathItem.HttpMethod method, String path) {
    return ApiEndpointAccessPolicy.isPublic(HttpMethod.valueOf(method.name()), path);
  }

  private ApiResponse problemResponse(String description) {
    return new ApiResponse().description(description).content(problemContent());
  }

  private void replaceErrorSchemas(ApiResponses responses) {
    responses.entrySet().stream()
        .filter(entry -> !entry.getKey().startsWith("2"))
        .map(Map.Entry::getValue)
        .forEach(response -> response.setContent(problemContent()));
  }

  private Content problemContent() {
    return new Content()
        .addMediaType(
            org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA_REF)));
  }
}
