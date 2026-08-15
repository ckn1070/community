package com.ronams.carmunity.account.internal.application.result;

import com.ronams.carmunity.account.internal.domain.RoleCode;
import java.util.UUID;

/**
 * 관리자 초대 수락 결과입니다.
 *
 * @param accountId 수락으로 생성된 account public id입니다.
 * @param email 정규화되어 저장된 관리자 email입니다.
 * @param roleCode 부여된 관리자 role입니다.
 */
public record AcceptedAdminInvitationResult(UUID accountId, String email, RoleCode roleCode) {}
