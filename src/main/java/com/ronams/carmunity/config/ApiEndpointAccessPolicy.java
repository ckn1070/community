package com.ronams.carmunity.config;

import java.util.List;
import org.springframework.http.HttpMethod;

/** Security와 OpenAPI가 함께 사용하는 public API allow-list입니다. */
final class ApiEndpointAccessPolicy {

  private static final List<PublicOperation> PUBLIC_OPERATIONS =
      List.of(
          new PublicOperation(HttpMethod.GET, "/api/v1/boards/free/posts"),
          new PublicOperation(HttpMethod.GET, "/api/v1/posts/{postId}"),
          new PublicOperation(HttpMethod.GET, "/api/v1/posts/{postId}/comments"),
          new PublicOperation(HttpMethod.GET, "/api/v1/comments/{commentId}/replies"),
          new PublicOperation(HttpMethod.GET, "/api/v1/media/images/{mediaId}/content"),
          new PublicOperation(HttpMethod.POST, "/api/v1/members"),
          new PublicOperation(HttpMethod.POST, "/api/v1/auth/sessions"),
          new PublicOperation(HttpMethod.POST, "/api/v1/auth/sessions/refresh"),
          new PublicOperation(HttpMethod.POST, "/api/v1/auth/verifications/email"),
          new PublicOperation(HttpMethod.POST, "/api/v1/auth/verifications/email/confirm"),
          new PublicOperation(
              HttpMethod.POST, "/api/v1/admin/invitations/{invitationId}/acceptances"),
          new PublicOperation(HttpMethod.POST, "/api/v1/auth/password-resets"),
          new PublicOperation(HttpMethod.POST, "/api/v1/auth/password-resets/confirm"),
          new PublicOperation(HttpMethod.POST, "/api/v1/auth/logout"),
          new PublicOperation(HttpMethod.GET, "/api/v1/car/brands"),
          new PublicOperation(HttpMethod.GET, "/api/v1/car/models"),
          new PublicOperation(HttpMethod.GET, "/api/v1/car/models/{carModelId}"));

  private ApiEndpointAccessPolicy() {}

  static List<PublicOperation> publicOperations() {
    return PUBLIC_OPERATIONS;
  }

  static boolean isPublic(HttpMethod method, String openApiPath) {
    return PUBLIC_OPERATIONS.stream()
        .anyMatch(
            operation -> operation.method() == method && operation.path().equals(openApiPath));
  }

  /** OpenAPI path template을 Spring Security 단일 segment matcher로 변환합니다. */
  record PublicOperation(HttpMethod method, String path) {

    String securityPath() {
      return path.replaceAll("\\{[^/]+}", "*");
    }
  }
}
