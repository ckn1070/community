package com.ronams.carmunity.account.internal.application.command;

/**
 * 최초 최고 관리자 bootstrap 요청입니다.
 *
 * @param email 정규화 전 운영자 입력 email입니다.
 * @param displayName 정규화 전 운영자 입력 표시 이름입니다.
 * @param password 해시 전 운영자 입력 초기 비밀번호입니다.
 */
public record BootstrapSuperAdminAccountCommand(
    String email, String displayName, String password) {}
