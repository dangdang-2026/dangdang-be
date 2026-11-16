package com.dangdang.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ChatFeedbackRequest(
        @Schema(description = "도움이 됐으면 true, 아니면 false", example = "true")
        @NotNull(message = "도움이 됐는지 골라 주세요.")
        Boolean helpful) {
}
