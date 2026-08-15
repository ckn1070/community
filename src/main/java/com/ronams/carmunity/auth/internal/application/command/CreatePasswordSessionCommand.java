package com.ronams.carmunity.auth.internal.application.command;

import java.util.Objects;

/**
 * 비밀번호 session 생성 usecase 입력 모델입니다.
 *
 * @param email 정규화 전 사용자 입력 email입니다.
 * @param password 검증에만 사용할 원문 password입니다. 저장, 로그, 감사 metadata로 전달하지 않습니다.
 */
public record CreatePasswordSessionCommand(String email, String password) {

  public CreatePasswordSessionCommand {
    Objects.requireNonNull(email, "email must not be null");
    Objects.requireNonNull(password, "password must not be null");
  }
}
