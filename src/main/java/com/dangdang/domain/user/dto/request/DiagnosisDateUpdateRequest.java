package com.dangdang.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DiagnosisDateUpdateRequest(
        @Schema(description = "임신성 당뇨 진단일 (yyyy-MM-dd, 2000-01-01부터 오늘(한국)까지 · 출산 예정일보다 뒤는 안 됨)", example = "2026-09-24")
        @NotNull(message = "진단일을 골라 주세요.")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
        LocalDate diagnosisDate) {}
