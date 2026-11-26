package com.dangdang.domain.shop.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 판매처로 간 기록 (M-22) 한 페이지. 날짜별 묶기는 프론트에서 (visitedAt 을 한국 날짜로)
public record ProductVisitListResponse(
        List<ProductVisitResponse> items,
        @Schema(description = "다음 페이지 커서 (없으면 null)") String nextCursor,
        boolean hasNext) {
}
