package com.ronams.carmunity.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * API 요청 상관관계에 사용할 request id 규칙과 저장 위치를 한곳에 모읍니다.
 *
 * <p>request id는 HTTP 응답 header, servlet request attribute, MDC에서 같은 값을 공유해야 하므로 이름과 검증 규칙을 분산하지
 * 않습니다.
 *
 * <p>이 타입은 config 패키지 내부 filter/handler가 공유하는 package-private utility입니다. 외부 계층에 공개하지 않고, 인스턴스 생성이나
 * 상속 없이 static helper로만 사용합니다.
 */
final class ApiRequestIdContext {

  static final String REQUEST_ID_HEADER = "X-Request-Id";
  static final String REQUEST_ID_REQUEST_ATTRIBUTE =
      ApiRequestIdContext.class.getName() + ".requestId";
  static final String REQUEST_ID_MDC_KEY = "requestId";

  private static final Pattern SAFE_REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

  // 인스턴스 상태가 없는 규칙 모음이므로 객체 생성 경로를 닫습니다.
  private ApiRequestIdContext() {}

  /**
   * 안전한 inbound request id만 재사용하고, 없거나 허용 형식이 아니면 서버 생성 UUID로 대체합니다.
   *
   * <p>잘못된 header 값 때문에 요청을 실패시키지 않고, 로그 상관관계에 쓸 안전한 값만 선택합니다.
   */
  static String resolve(HttpServletRequest request) {
    String candidate = request.getHeader(REQUEST_ID_HEADER);
    if (candidate != null && SAFE_REQUEST_ID_PATTERN.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }

  /**
   * 하나의 request id를 request attribute, response header, MDC에 동시에 연결합니다.
   *
   * <p>filter, exception handler, logger가 같은 값을 보게 하는 config 패키지 내부 contract입니다.
   */
  static void bind(HttpServletRequest request, HttpServletResponse response, String requestId) {
    request.setAttribute(REQUEST_ID_REQUEST_ATTRIBUTE, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    MDC.put(REQUEST_ID_MDC_KEY, requestId);
  }

  /**
   * 예외 로그에 사용할 request id를 request attribute에서 꺼냅니다.
   *
   * <p>request id가 아직 없으면 로그 기록을 막지 않기 위해 빈 문자열을 반환합니다.
   */
  static String from(HttpServletRequest request) {
    Object requestId = request.getAttribute(REQUEST_ID_REQUEST_ATTRIBUTE);
    if (requestId instanceof String stringValue && !stringValue.isBlank()) {
      return stringValue;
    }
    return "";
  }

  /** servlet thread 재사용 시 이전 요청의 request id가 다음 요청 로그에 섞이지 않도록 MDC를 정리합니다. */
  static void clear() {
    MDC.remove(REQUEST_ID_MDC_KEY);
  }
}
