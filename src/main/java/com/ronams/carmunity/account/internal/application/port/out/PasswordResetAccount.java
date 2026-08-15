package com.ronams.carmunity.account.internal.application.port.out;

import com.ronams.carmunity.account.internal.domain.AccountStatus;
import java.util.UUID;

/**
 * reset token 발급 판단에 필요한 account 조회 결과입니다.
 *
 * @param accountId 내부 account id입니다.
 * @param accountPublicId 로그와 운영 추적용 공개 id입니다.
 * @param recipientNormalized reset email을 보낼 현재 account email snapshot입니다.
 * @param status reset token 발급 가능 여부 판단에 사용할 account 상태입니다.
 */
public record PasswordResetAccount(
    Long accountId, UUID accountPublicId, String recipientNormalized, AccountStatus status) {}
