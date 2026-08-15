package com.ronams.carmunity.account.internal.application.port.in;

import com.ronams.carmunity.account.internal.application.AccountErrorCode;
import com.ronams.carmunity.account.internal.application.command.RegisterMemberAccountCommand;
import com.ronams.carmunity.account.internal.application.result.RegisterMemberAccountResult;
import com.ronams.carmunity.shared.error.ApiException;
import com.ronams.carmunity.shared.error.CommonErrorCode;

/** 일반 회원 자체 가입 유스케이스입니다. */
public interface RegisterMemberAccountUseCase {

  /**
   * 일반 회원 account, profile, password credential, member role, sign-up event를 생성합니다.
   *
   * @param command 정규화 전 사용자 입력입니다. email과 displayName은 domain 값 객체에서 정규화합니다.
   * @return 생성된 회원의 public id와 PENDING 상태를 포함한 가입 결과입니다.
   * @throws ApiException {@link CommonErrorCode#INVALID_REQUEST}, {@link
   *     AccountErrorCode#EMAIL_ALREADY_USED}, {@link AccountErrorCode#DISPLAY_NAME_ALREADY_USED},
   *     {@link AccountErrorCode#AUTH_ROLE_NOT_CONFIGURED} 상황에서 발생합니다.
   * @apiNote 로그인 token 발급과 이메일 인증 완료 처리는 이 유스케이스에서 수행하지 않습니다.
   */
  RegisterMemberAccountResult execute(RegisterMemberAccountCommand command);
}
