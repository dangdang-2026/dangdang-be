package com.dangdang.domain.food.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// 같은 음식 지난 기록 한 페이지 (음식 검색과 같은 page·size 방식)
public record FoodHistoryPageResponse(
        List<FoodHistoryResponse> items,   // 결과가 없으면 []
        int page,                          // 0부터
        int size,
        long totalElements,                // 이 음식을 먹은 내 식사 수
        boolean hasNext) {

    // page = 식사 페이지 (순서·개수 정보만 씀), items = 그 식사들을 같은 순서로 바꾼 응답
    public static FoodHistoryPageResponse of(Page<?> page, List<FoodHistoryResponse> items) {
        return new FoodHistoryPageResponse(
                items, page.getNumber(), page.getSize(), page.getTotalElements(), page.hasNext());
    }
}
