package com.ronams.carmunity.account.internal.application.result;

import com.ronams.carmunity.account.internal.domain.RoleCode;
import java.time.Instant;
import java.util.UUID;

/**
 * 관리자 초대 생성 결과입니다.
 *
 * @param invitationId 외부 API에 노출할 초대 public id입니다.
 * @param email 정규화되어 저장된 초대 대상 email입니다.
 * @param roleCode 초대된 관리자 role입니다.
 * @param expiresAt 초대 token 만료 시각입니다.
 */
public record AdminInvitationResult(
    UUID invitationId, String email, RoleCode roleCode, Instant expiresAt) {}
