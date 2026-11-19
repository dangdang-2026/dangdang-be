package com.dangdang.domain.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SanctionCreateRequest(
        @Schema(description = "제한할 회원 번호", example = "12") @NotNull(message = "회원을 골라 주세요.") Long userId,
        @Schema(description = "사유 (신고 사유와 같은 말)", example = "MEDICAL_ADVICE")
        @NotBlank(message = "사유를 적어 주세요.") @Size(max = 50) String reason,
        @Schema(description = "며칠 (1~365). 안 보내면 운영자가 풀 때까지", example = "7")
        @Min(value = 1, message = "기간은 1~365일이에요.") @Max(value = 365, message = "기간은 1~365일이에요.") Integer days) {
}
