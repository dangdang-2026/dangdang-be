package com.dangdang.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatAskRequest(
        @Schema(description = "물어볼 말 (500자까지)", example = "지난주 공복 혈당 보여 줘")
        @NotBlank(message = "물어볼 말을 적어 주세요.")
        @Size(max = 500, message = "500자까지 적을 수 있어요.")
        String content) {
}
