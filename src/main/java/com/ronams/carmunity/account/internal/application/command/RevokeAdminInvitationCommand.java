package com.ronams.carmunity.account.internal.application.command;

import java.util.UUID;

/**
 * 관리자 초대 회수 요청입니다.
 *
 * @param revokerPublicId 회수하는 최고 관리자 account public id입니다.
 * @param invitationId 회수할 초대 public id입니다.
 */
public record RevokeAdminInvitationCommand(UUID revokerPublicId, UUID invitationId) {}
