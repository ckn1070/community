package com.ronams.carmunity.shared.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * request validation 실패 시 클라이언트가 필드별 오류를 식별할 때 사용하는 표현입니다.
 *
 * @param field 클라이언트 요청 DTO의 필드 경로입니다.
 * @param code validation constraint 또는 binding 오류 코드입니다.
 * @param message 클라이언트가 입력을 수정할 때 참고할 수 있는 오류 설명입니다.
 */
@Schema(description = "request validation 필드 오류")
public record FieldErrorDetail(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {}
