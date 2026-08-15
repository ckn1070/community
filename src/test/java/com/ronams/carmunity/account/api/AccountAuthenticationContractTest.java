package com.ronams.carmunity.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * account 공개 인증 모델이 보안 판단 값을 기본값으로 숨기지 않는지 검증합니다.
 *
 * <p>`passwordChangeRequired`를 누락한 public 생성자가 생기면 초기 관리자 같은 강제 변경 대상이 일반 세션처럼 처리될 수 있습니다.
 */
class AccountAuthenticationContractTest {

  /** 로그인 조회 모델은 비밀번호 변경 필요 여부를 호출자가 반드시 명시해야 합니다. */
  @Test
  void passwordLoginAccountDoesNotExposeConstructorWithoutPasswordChangeRequired() {
    assertThat(PasswordLoginAccount.class.getConstructors())
        .singleElement()
        .satisfies(
            constructor ->
                assertThat(constructor.getParameterTypes())
                    .containsExactly(
                        UUID.class,
                        AccountAuthStatus.class,
                        String.class,
                        List.class,
                        boolean.class));
  }

  /** token subject 모델은 비밀번호 변경 필요 여부와 account 상태를 호출자가 반드시 명시해야 합니다. */
  @Test
  void accountAuthSubjectDoesNotExposeConstructorWithoutPasswordChangeRequired() {
    assertThat(AccountAuthSubject.class.getConstructors())
        .singleElement()
        .satisfies(
            constructor ->
                assertThat(constructor.getParameterTypes())
                    .containsExactly(
                        UUID.class, AccountAuthStatus.class, List.class, boolean.class));
  }

  /** 로그인 조회 모델에서 token subject로 변환할 때 비밀번호 변경 필요 상태를 잃지 않습니다. */
  @Test
  void passwordLoginAccountPropagatesPasswordChangeRequiredToAuthSubject() {
    PasswordLoginAccount account =
        new PasswordLoginAccount(
            UUID.fromString("0f3445fd-5f22-4f74-9a9d-1ff0c68a924a"),
            AccountAuthStatus.ACTIVE,
            "{argon2}stored-password-hash",
            List.of("ROLE_SUPER_ADMIN"),
            true);

    AccountAuthSubject subject = account.toAuthSubject();

    assertThat(subject.passwordChangeRequired()).isTrue();
  }
}
