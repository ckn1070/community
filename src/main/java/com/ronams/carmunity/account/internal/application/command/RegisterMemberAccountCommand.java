package com.ronams.carmunity.account.internal.application.command;

/**
 * 일반 회원 자체 가입 요청입니다.
 *
 * @param email 정규화 전 사용자 입력 email입니다.
 * @param password 해시 전 사용자 입력 비밀번호입니다.
 * @param displayName 정규화 전 사용자 입력 표시 이름입니다.
 */
public record RegisterMemberAccountCommand(String email, String password, String displayName) {}
