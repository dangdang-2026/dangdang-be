package com.dangdang.domain.pregnancy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 졸업 화면 (E-12, PP-02) — 평가·연속 기록 축하 없이 숫자만
public record GraduationResponse(
        @Schema(description = "지킨 측정 (저장한 혈당 기록 수)", example = "212") long keptMeasurements,
        @Schema(description = "함께한 주 (가입한 날부터)", example = "14") long weeksTogether) {
}
