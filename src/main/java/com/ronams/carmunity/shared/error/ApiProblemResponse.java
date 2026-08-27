package com.ronams.carmunity.shared.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;

/** 실제 {@link org.springframework.http.ProblemDetail} JSON에 대응하는 OpenAPI 오류 schema입니다. */
@Schema(description = "모든 API 오류에 사용하는 Problem Details 응답")
public record ApiProblemResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) URI type,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String detail,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) URI instance,
    @Schema(description = "클라이언트가 분기할 안정적인 오류 코드", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
    @Schema(description = "request validation 실패일 때만 포함되는 필드 오류") List<FieldErrorDetail> errors) {}
