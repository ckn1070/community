package com.ronams.carmunity.account.internal.application.command;

import java.util.UUID;

/**
 * 인증된 사용자의 비밀번호 변경 요청입니다.
 *
 * @param accountPublicId 인증된 account public id입니다.
 * @param currentPassword 현재 비밀번호 원문입니다.
 * @param newPassword 새 비밀번호 원문입니다.
 * @param newPasswordConfirmation 새 비밀번호 확인 원문입니다.
 */
public record ChangePasswordCommand(
    UUID accountPublicId,
    String currentPassword,
    String newPassword,
    String newPasswordConfirmation) {}
