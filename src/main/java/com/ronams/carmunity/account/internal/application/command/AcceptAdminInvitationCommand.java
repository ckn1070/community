package com.ronams.carmunity.account.internal.application.command;

import java.util.UUID;

/**
 * 관리자 초대 수락 요청입니다.
 *
 * @param invitationId path에서 받은 초대 public id입니다.
 * @param token 이메일 링크에서 전달된 raw invitation token입니다.
 * @param password 새 관리자 account가 사용할 비밀번호 원문입니다.
 * @param passwordConfirm 새 비밀번호 오타 방지를 위해 password와 같은 값이어야 하는 확인 입력입니다.
 * @param displayName 새 관리자 account의 표시 이름입니다.
 */
public record AcceptAdminInvitationCommand(
    UUID invitationId, String token, String password, String passwordConfirm, String displayName) {}
