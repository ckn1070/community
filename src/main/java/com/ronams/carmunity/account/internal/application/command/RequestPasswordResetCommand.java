package com.ronams.carmunity.account.internal.application.command;

/**
 * 비밀번호 재설정 요청 command입니다.
 *
 * @param email reset token 발송을 요청한 email입니다.
 */
public record RequestPasswordResetCommand(String email) {}
