package com.dangdang.domain.pregnancy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record MeasurementPauseRequest(
        @Schema(description = "다른 상황이라 알림을 멈추면 true, 다시 켜면 false", example = "true")
        @NotNull(message = "알림을 멈출지 골라 주세요.")
        Boolean paused) {
}
