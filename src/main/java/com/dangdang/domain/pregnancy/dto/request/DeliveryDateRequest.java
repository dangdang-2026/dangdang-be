package com.dangdang.domain.pregnancy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DeliveryDateRequest(
        @Schema(description = "출산일 (오늘까지)", example = "2026-11-08")
        @NotNull(message = "출산일을 골라 주세요.")
        LocalDate deliveryDate) {
}
