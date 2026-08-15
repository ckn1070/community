package com.ronams.carmunity.account.internal.application.service;

import com.ronams.carmunity.account.internal.domain.PasswordResetTokenHash;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 raw token을 저장 가능한 hash로 변환합니다.
 *
 * <p>raw token은 이메일 링크와 reset confirmation request에만 존재하고, persistence 경계에는 SHA-256 hash만 전달합니다.
 */
@Component
public class PasswordResetTokenHasher {

  /**
   * raw token을 SHA-256 lowercase hex로 변환합니다.
   *
   * @param rawToken 사용자가 제출한 원문 token입니다.
   * @return 저장과 조회에 사용할 token hash입니다.
   * @throws IllegalArgumentException raw token이 비어 있을 때 발생합니다.
   */
  public PasswordResetTokenHash hash(String rawToken) {
    return PasswordResetTokenHash.from(Sha256TokenHashing.hashRawToken(rawToken));
  }
}
