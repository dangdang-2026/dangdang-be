package com.dangdang.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DueDateUpdateRequest(
        @Schema(description = "출산 예정일 (yyyy-MM-dd)", example = "2026-12-20")
        @NotNull(message = "출산 예정일을 골라 주세요.")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
        LocalDate dueDate) {}
