package com.ronams.carmunity.account.internal.application.result;

import com.ronams.carmunity.account.internal.domain.AccountStatus;
import java.util.UUID;

/**
 * 일반 회원 자체 가입 결과입니다.
 *
 * @param accountPublicId 외부 API에 노출할 account public id입니다.
 * @param email 정규화되어 저장된 email입니다.
 * @param displayName 공개 표시 이름입니다.
 * @param status 가입 직후 account 상태입니다.
 */
public record RegisterMemberAccountResult(
    UUID accountPublicId, String email, String displayName, AccountStatus status) {}
