package com.ronams.carmunity.account.internal.application.command;

/**
 * 비밀번호 재설정 완료 command입니다.
 *
 * @param token reset email로 전달된 raw token입니다.
 * @param newPassword 새 비밀번호 원문입니다.
 * @param newPasswordConfirmation 새 비밀번호 확인 원문입니다.
 */
public record ConfirmPasswordResetCommand(
    String token, String newPassword, String newPasswordConfirmation) {}
