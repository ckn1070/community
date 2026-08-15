package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.EmailAddress;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import java.time.Instant;
import java.util.UUID;

/**
 * 관리자 초대 저장 요청입니다.
 *
 * @param inviterPublicId 초대를 생성하는 관리자 account public id입니다.
 * @param email 정규화가 끝난 초대 대상 email 값 객체입니다.
 * @param roleCode 초대할 관리자 role입니다.
 * @param tokenHash raw token의 SHA-256 lowercase hex hash입니다.
 * @param issuedAt 초대 발급 시각입니다.
 * @param expiresAt 초대 만료 시각입니다.
 */
public record AdminInvitationSaveRequest(
    UUID inviterPublicId,
    EmailAddress email,
    RoleCode roleCode,
    String tokenHash,
    Instant issuedAt,
    Instant expiresAt) {}
