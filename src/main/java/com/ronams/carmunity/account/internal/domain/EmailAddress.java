package com.ronams.carmunity.account.internal.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * account email 값 객체입니다.
 *
 * <p>Type: Value Object.
 *
 * <p>현재 가입 정책은 email 소유권 확인 전 단계이므로, 여기서는 저장과 중복 판단에 필요한 정규화와 기본 형식 검증만 수행합니다.
 *
 * @param value 저장과 응답에 사용할 trim/lowercase 이후 email입니다.
 * @param normalized 중복 판단에 사용할 email입니다. 현재 정책에서는 {@code value}와 같습니다.
 */
public record EmailAddress(String value, String normalized) {

  private static final int MAX_LENGTH = 254;
  private static final Pattern SIMPLE_EMAIL_PATTERN =
      Pattern.compile("^[^@\\s]++@[^@.\\s]++(?:\\.[^@.\\s]++)++$");

  /**
   * 사용자 입력 email을 저장 기준으로 정규화합니다.
   *
   * @param rawEmail 정규화 전 사용자 입력입니다.
   * @return trim/lowercase 이후 검증된 email 값입니다.
   * @throws IllegalArgumentException email이 비어 있거나 저장 길이와 기본 형식을 만족하지 못할 때 발생합니다.
   */
  public static EmailAddress from(String rawEmail) {
    String value = Objects.requireNonNullElse(rawEmail, "").trim().toLowerCase(Locale.ROOT);
    if (value.isBlank()) {
      throw new IllegalArgumentException("email must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("email must not exceed 254 characters");
    }
    if (!SIMPLE_EMAIL_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("email format is invalid");
    }
    return new EmailAddress(value, value);
  }
}
