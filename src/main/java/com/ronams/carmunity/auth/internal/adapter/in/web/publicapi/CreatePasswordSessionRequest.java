package com.ronams.carmunity.auth.internal.adapter.in.web.publicapi;

import com.ronams.carmunity.auth.internal.application.command.CreatePasswordSessionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 session 생성 요청 DTO입니다.
 *
 * <p>DTO는 필수값과 저장 한계에 가까운 크기만 검증하고, trim/lowercase 정규화는 application usecase에서 수행합니다.
 *
 * @param email 정규화 전 email 입력입니다.
 * @param password 검증에만 사용할 원문 password입니다.
 */
@Schema(description = "비밀번호 로그인 요청")
public record CreatePasswordSessionRequest(
    @Schema(description = "로그인 email", maxLength = 254) @NotBlank @Size(max = 254) String email,
    @Schema(description = "비밀번호", minLength = 1, maxLength = 128) @NotBlank @Size(max = 128)
        String password) {

  /**
   * Web 요청 DTO를 application command로 변환합니다.
   *
   * @return Web framework annotation이 없는 유스케이스 입력입니다.
   */
  CreatePasswordSessionCommand toCommand() {
    return new CreatePasswordSessionCommand(email, password);
  }
}
