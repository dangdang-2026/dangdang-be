package com.dangdang.domain.card.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// 검수자 적기 (U-26 상세의 「검수자 · 검수일」)
public record CardReviewRequest(
        @Schema(example = "다현") @NotBlank @Size(max = 50) String reviewer,
        @Schema(example = "2026-11-19") @NotNull LocalDate reviewedOn) {
}
