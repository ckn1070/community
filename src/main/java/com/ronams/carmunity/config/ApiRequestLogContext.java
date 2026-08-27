package com.ronams.carmunity.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;

/** API 완료 로그에 전달할 안전한 요청별 상관관계 값을 servlet attribute로 관리합니다. */
final class ApiRequestLogContext {

  private static final String ACTOR_ID_ATTRIBUTE =
      ApiRequestLogContext.class.getName() + ".actorId";
  private static final String ERROR_CODE_ATTRIBUTE =
      ApiRequestLogContext.class.getName() + ".errorCode";

  private ApiRequestLogContext() {}

  /** 현재 JWT subject 계약인 UUID public id만 actor id로 받아 개인정보 principal의 우발적 기록을 막습니다. */
  static void bindActor(HttpServletRequest request, String principalName) {
    try {
      request.setAttribute(ACTOR_ID_ATTRIBUTE, UUID.fromString(principalName).toString());
    } catch (IllegalArgumentException | NullPointerException ignored) {
      // UUID가 아닌 principal은 별도 공개 식별자 계약이 생기기 전까지 로그 상관관계에서 제외합니다.
    }
  }

  /** 클라이언트에 공개한 오류 코드만 완료 로그와 연결합니다. */
  static void bindErrorCode(HttpServletRequest request, String errorCode) {
    if (errorCode != null && !errorCode.isBlank()) {
      request.setAttribute(ERROR_CODE_ATTRIBUTE, errorCode);
    }
  }

  static Optional<String> actorId(HttpServletRequest request) {
    return stringAttribute(request, ACTOR_ID_ATTRIBUTE);
  }

  static Optional<String> errorCode(HttpServletRequest request) {
    return stringAttribute(request, ERROR_CODE_ATTRIBUTE);
  }

  private static Optional<String> stringAttribute(HttpServletRequest request, String name) {
    Object value = request.getAttribute(name);
    return value instanceof String stringValue && !stringValue.isBlank()
        ? Optional.of(stringValue)
        : Optional.empty();
  }
}
