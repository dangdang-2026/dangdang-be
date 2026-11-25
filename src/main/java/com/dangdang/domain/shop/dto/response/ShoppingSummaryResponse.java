package com.dangdang.domain.shop.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 마이 › 쇼핑 메뉴 작은 글자 (M-01 v2): 「저장해 둔 상품 4개」 · 「최근 7일 · 3개」
public record ShoppingSummaryResponse(
        @Schema(description = "찜한 상품 수 (판매 중인 것, 찜한 상품 totalCount 와 같음)", example = "4") long likedCount,
        @Schema(description = "오늘 포함 최근 7일(한국 날짜) 본 상품 수 (판매 중인 것)", example = "3") long recentCount) {
}
