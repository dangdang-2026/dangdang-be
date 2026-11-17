package com.dangdang.domain.pregnancy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RemindLaterRequest(
        @Schema(description = "다시 물어볼 날 (오늘이나 그 뒤)", example = "2026-11-30")
        @NotNull(message = "다시 물어볼 날을 골라 주세요.")
        LocalDate remindOn) {
}
