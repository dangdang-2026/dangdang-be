package com.dangdang.domain.record.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

// 기간 지우기 결과 (M-07). 이미 지운 기록은 안 셈
public record RecordDeleteResponse(
        @Schema(description = "지운 기간 시작 (한국 날짜, 포함)", example = "2026-11-01") LocalDate from,
        @Schema(description = "지운 기간 끝 (한국 날짜, 포함)", example = "2026-11-07") LocalDate to,
        @Schema(description = "지운 혈당 기록 수", example = "18") int deletedReadings,
        @Schema(description = "지운 식사 기록 수", example = "21") int deletedMeals,
        @Schema(description = "지운 인슐린 기록 수", example = "7") int deletedInsulinLogs) {
}
