package com.dangdang.domain.shop.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 최근 본 상품 한 페이지 (오늘 포함 최근 7일(한국 날짜) · 판매 중). 다음 페이지는 nextCursor 를 그대로 cursor 로
public record RecentProductListResponse(
        List<RecentProductResponse> items,
        @Schema(description = "다음 페이지 커서 (없으면 null)") String nextCursor,
        boolean hasNext) {
}
