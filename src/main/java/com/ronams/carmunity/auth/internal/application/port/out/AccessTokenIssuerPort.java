package com.ronams.carmunity.auth.internal.application.port.out;

import com.ronams.carmunity.account.api.AccountAuthSubject;
import com.ronams.carmunity.auth.internal.application.result.IssuedAccessToken;
import com.ronams.carmunity.auth.internal.application.service.SessionAccess;

/**
 * application이 access token 발급 구현에 기대하는 능력입니다.
 *
 * <p>JWT 서명 알고리즘과 key 운용은 adapter 책임으로 숨기고, application은 발급 결과 계약만 사용합니다.
 */
public interface AccessTokenIssuerPort {

  /**
   * account token subject에 대한 access token을 발급합니다.
   *
   * @param subject token subject로 사용할 account 공개 정보입니다.
   * @param sessionAccess 보호 API 접근 범위를 결정할 session access입니다.
   * @return 클라이언트에 전달할 access token 표현입니다.
   */
  IssuedAccessToken issue(AccountAuthSubject subject, SessionAccess sessionAccess);
}
