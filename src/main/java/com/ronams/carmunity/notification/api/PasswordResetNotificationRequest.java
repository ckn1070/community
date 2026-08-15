package com.ronams.carmunity.notification.api;

import java.time.Instant;
import java.util.Objects;

/**
 * 비밀번호 재설정 발송을 위한 notification 공개 요청 모델입니다.
 *
 * @param recipientNormalized 발송 대상 email snapshot입니다.
 * @param resetToken 비밀번호 재설정 화면이 confirmation API에 제출할 raw token입니다. notification 경계는 이 값을 hash하지
 *     않고 이메일 링크에만 넣습니다.
 * @param expiresAt token 만료 시각입니다.
 */
public record PasswordResetNotificationRequest(
    String recipientNormalized, String resetToken, Instant expiresAt) {

  public PasswordResetNotificationRequest {
    Objects.requireNonNull(recipientNormalized, "recipientNormalized must not be null");
    Objects.requireNonNull(resetToken, "resetToken must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }
}
