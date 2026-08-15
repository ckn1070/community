package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.account.internal.application.result.RegisterMemberAccountResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 일반 회원 자체 가입 성공 응답입니다.
 *
 * <p>내부 DB id, password hash, access token, refresh token은 응답에 포함하지 않습니다.
 *
 * @param memberId 외부 API에 노출할 회원 식별자입니다.
 * @param email 정규화되어 저장된 email입니다.
 * @param displayName 공개 표시 이름입니다.
 * @param status 가입 직후 account 상태입니다.
 */
@Schema(description = "일반 회원 자체 가입 성공 응답")
public record RegisterMemberResponse(
    @Schema(description = "회원 public id") UUID memberId,
    @Schema(description = "정규화된 email", example = "user@example.com") String email,
    @Schema(description = "표시 이름", example = "Road Runner") String displayName,
    @Schema(description = "가입 직후 account 상태", example = "PENDING") String status) {

  /**
   * application 결과를 외부 응답으로 변환합니다.
   *
   * <p>내부 DB id와 인증 token은 application 결과에 있어도 이 응답 경계에서 노출하지 않습니다.
   */
  static RegisterMemberResponse from(RegisterMemberAccountResult result) {
    return new RegisterMemberResponse(
        result.accountPublicId(), result.email(), result.displayName(), result.status().name());
  }
}
