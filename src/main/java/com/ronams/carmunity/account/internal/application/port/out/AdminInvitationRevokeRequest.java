package com.ronams.carmunity.account.internal.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * 관리자 초대 회수 저장 요청입니다.
 *
 * @param revokerPublicId 회수하는 최고 관리자 account public id입니다.
 * @param invitationPublicId 회수할 초대 public id입니다.
 * @param revokedAt 회수 시각입니다.
 */
public record AdminInvitationRevokeRequest(
    UUID revokerPublicId, UUID invitationPublicId, Instant revokedAt) {}
