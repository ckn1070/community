package com.ronams.carmunity.account.internal.application.command;

import java.util.UUID;

/**
 * 관리자 초대 생성 요청입니다.
 *
 * @param inviterPublicId 초대를 생성하는 관리자 account public id입니다.
 * @param email 정규화 전 초대 대상 email입니다.
 * @param roleCode 초대할 관리자 role code입니다.
 */
public record InviteAdminCommand(UUID inviterPublicId, String email, String roleCode) {}
