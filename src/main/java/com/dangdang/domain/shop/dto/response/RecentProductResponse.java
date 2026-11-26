package com.dangdang.domain.shop.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// 최근 본 상품 하나 = 상품 카드 + 마지막으로 본 시각
public record RecentProductResponse(
        @Schema(description = "상품 카드 (목록과 같은 모양)") ProductSummaryResponse product,
        @Schema(description = "마지막으로 본 시각 (UTC)") Instant viewedAt) {
}
