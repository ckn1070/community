package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.Account;
import com.ronams.carmunity.account.internal.domain.RoleCode;
import java.time.Instant;
import java.util.UUID;

/**
 * 초대 수락으로 생성된 관리자 account 저장 요청입니다.
 *
 * @param invitationId 내부 초대 id입니다.
 * @param invitationPublicId 초대 public id입니다.
 * @param invitedByAccountId 초대를 생성한 내부 account id입니다.
 * @param roleId 초대 row가 참조하는 내부 role id입니다.
 * @param roleCode 수락 시 부여할 role code입니다.
 * @param account 새로 생성할 account aggregate입니다.
 * @param acceptedAt 수락 시각입니다.
 */
public record AcceptedAdminAccountStoreRequest(
    Long invitationId,
    UUID invitationPublicId,
    Long invitedByAccountId,
    Long roleId,
    RoleCode roleCode,
    Account account,
    Instant acceptedAt) {}
