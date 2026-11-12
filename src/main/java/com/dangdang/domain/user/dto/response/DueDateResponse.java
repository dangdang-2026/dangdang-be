package com.dangdang.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record DueDateResponse(
        @Schema(description = "저장된 출산 예정일", example = "2026-12-20") LocalDate dueDate) {}
