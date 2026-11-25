package com.dangdang.domain.pregnancy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 졸업 화면 (E-12, PP-02) — 평가·연속 기록 축하 없이 숫자만 「412번 기록 · 96일」
public record GraduationResponse(
        @Schema(description = "기록 수 (지우지 않은 혈당 기록)", example = "412") long keptMeasurements,
        @Schema(description = "함께한 주 (가입한 날부터, 최소 1) — 예전 화면용", example = "14") long weeksTogether,
        @Schema(description = "함께한 날 (가입한 한국 날짜 = 1일째)", example = "96") long daysTogether) {
}
