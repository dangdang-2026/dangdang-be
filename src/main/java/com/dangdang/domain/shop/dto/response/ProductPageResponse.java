package com.dangdang.domain.shop.dto.response;

import java.util.List;

// 상점 목록 한 페이지 (page 는 0부터)
public record ProductPageResponse(List<ProductSummaryResponse> items, int page, int size, boolean hasNext) {
}
