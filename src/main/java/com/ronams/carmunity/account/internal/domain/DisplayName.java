package com.ronams.carmunity.account.internal.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * 커뮤니티에 공개되는 표시 이름 값 객체입니다.
 *
 * <p>Type: Value Object.
 *
 * <p>표시값은 사용자가 보는 대소문자를 보존하고, 중복 판단용 값만 lowercase로 분리합니다.
 *
 * @param value 응답과 공개 프로필에 사용할 표시 이름입니다.
 * @param normalized 중복 판단에 사용할 trim/collapse/lowercase 이후 값입니다.
 */
public record DisplayName(String value, String normalized) {

  private static final int MAX_LENGTH = 30;

  /**
   * 사용자 입력 display name을 표시값과 중복 판단값으로 정규화합니다.
   *
   * @param rawDisplayName 정규화 전 사용자 입력입니다.
   * @return 저장 가능한 표시 이름입니다.
   * @throws IllegalArgumentException 표시 이름이 비어 있거나 저장 길이를 초과할 때 발생합니다.
   */
  public static DisplayName from(String rawDisplayName) {
    String value = collapseWhitespace(Objects.requireNonNullElse(rawDisplayName, "").trim());
    if (value.isBlank()) {
      throw new IllegalArgumentException("display name must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("display name must not exceed 30 characters");
    }
    return new DisplayName(value, value.toLowerCase(Locale.ROOT));
  }

  private static String collapseWhitespace(String value) {
    return value.replaceAll("\\s+", " ");
  }
}
