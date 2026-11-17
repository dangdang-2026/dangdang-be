package com.dangdang.domain.pregnancy.dto.response;

import com.dangdang.domain.postpartum.dto.response.PostpartumTestResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

// 산후 홈 (E-07 · E-10) — 히어로 카드 · 검사일 적기/다시 물어보기 · 다가오는 검사
public record PostpartumHomeResponse(
        @Schema(example = "2026-11-01") LocalDate deliveryDate,
        @Schema(description = "출산 뒤 며칠째 (출산일 = 1일째)", example = "9") long dayAfterDelivery,
        @Schema(description = "다시 물어볼 날 (없으면 null)", example = "2026-11-30") LocalDate askOn,
        @Schema(description = "오늘 다시 물어볼 차례인지 (그날·그 뒤, 등록한 검사가 없을 때)", example = "false") boolean askToday,
        @Schema(description = "오늘 이후 검사 (가까운 순)") List<PostpartumTestResponse> upcomingTests) {
}
