package com.dangdang.domain.shop.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 상점 메인 묶음 하나 (「산모들이 먹고 기록한 간식」)
public record ProductCardListResponse(
        @Schema(description = "상품 카드 (목록과 같은 모양)") List<ProductSummaryResponse> items) {
}
