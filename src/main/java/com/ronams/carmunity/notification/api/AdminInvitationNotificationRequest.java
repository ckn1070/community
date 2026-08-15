package com.ronams.carmunity.notification.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 관리자 초대 발송을 위한 notification 공개 요청 모델입니다.
 *
 * @param recipientNormalized 발송 대상 email snapshot입니다.
 * @param invitationId 초대 수락 API 경로에 사용할 초대 public id입니다.
 * @param invitationToken 초대 수락 API에 제출할 raw token입니다. notification 경계는 이 값을 hash하지 않고 이메일 링크에만
 *     넣습니다.
 * @param expiresAt 초대 token 만료 시각입니다.
 */
public record AdminInvitationNotificationRequest(
    String recipientNormalized, UUID invitationId, String invitationToken, Instant expiresAt) {

  public AdminInvitationNotificationRequest {
    Objects.requireNonNull(recipientNormalized, "recipientNormalized must not be null");
    Objects.requireNonNull(invitationId, "invitationId must not be null");
    Objects.requireNonNull(invitationToken, "invitationToken must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }
}
