package com.dangdang.domain.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppealRequest(
        @Schema(description = "이의 내용 (1000자까지)", example = "약 이름을 쓴 건 제 경험을 나누려던 거예요.")
        @NotBlank(message = "이의 내용을 적어 주세요.") @Size(max = 1000, message = "1000자까지 적을 수 있어요.") String content) {
}
