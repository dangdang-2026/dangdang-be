package com.dangdang.domain.shop.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 찜한 상품 (M-21) 한 페이지. 다음 페이지는 nextCursor 를 그대로 cursor 로
public record LikedProductListResponse(
        @Schema(description = "상품 카드 (liked 는 항상 true)") List<ProductSummaryResponse> items,
        @Schema(description = "다음 페이지 커서 (없으면 null)") String nextCursor,
        boolean hasNext,
        @Schema(description = "화면 위 「4개」 — 판매 중인 찜 전체 수 (목록과 같은 조건)", example = "4") long totalCount) {
}
