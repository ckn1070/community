package com.ronams.carmunity.community.internal.domain;

import java.util.Objects;

/** Type: Value Object. 게시글 제목의 Unicode 공백 정규화와 길이 불변식을 소유합니다. */
public record PostTitle(String value) {

  public PostTitle {
    Objects.requireNonNull(value, "value must not be null");
    value = normalize(value);
    int length = value.codePointCount(0, value.length());
    if (length < 1 || length > 120 || value.codePoints().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("게시글 제목은 제어 문자를 제외한 1~120자여야 합니다.");
    }
  }

  private static String normalize(String source) {
    StringBuilder result = new StringBuilder();
    boolean pendingSpace = false;
    for (int offset = 0; offset < source.length(); ) {
      int codePoint = source.codePointAt(offset);
      offset += Character.charCount(codePoint);
      if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
        pendingSpace = result.length() > 0;
      } else {
        if (pendingSpace) {
          result.append(' ');
          pendingSpace = false;
        }
        result.appendCodePoint(codePoint);
      }
    }
    return result.toString();
  }
}
