package com.ronams.carmunity.account.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.account.internal.application.command.RegisterMemberAccountCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 일반 회원 자체 가입 요청 DTO입니다.
 *
 * <p>DTO는 필수값과 저장 한계에 가까운 형식만 빠르게 거르고, trim/lowercase 같은 정규화 후 검증은 domain 값 객체가 담당합니다.
 *
 * @param email 정규화 전 email 입력입니다.
 * @param password password policy 검증과 hash 대상 원문입니다.
 * @param displayName 정규화 전 표시 이름 입력입니다.
 */
@Schema(description = "일반 회원 자체 가입 요청")
public record RegisterMemberRequest(
    @Schema(description = "가입 email", example = "user@example.com", maxLength = 254)
        @NotBlank
        @Size(max = 254)
        String email,
    @Schema(description = "비밀번호. 최소 10자, 최대 128자입니다.", minLength = 10, maxLength = 128)
        @NotBlank
        @Size(min = 10, max = 128)
        String password,
    @Schema(description = "커뮤니티 표시 이름", example = "Road Runner", maxLength = 30)
        @NotBlank
        @Size(max = 30)
        String displayName) {

  /**
   * Web 요청 DTO를 application command로 변환합니다.
   *
   * @return Web framework annotation이 없는 유스케이스 입력입니다.
   */
  RegisterMemberAccountCommand toCommand() {
    return new RegisterMemberAccountCommand(email, password, displayName);
  }
}
