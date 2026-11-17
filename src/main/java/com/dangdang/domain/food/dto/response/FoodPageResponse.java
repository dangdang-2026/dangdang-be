package com.dangdang.domain.food.dto.response;

import com.dangdang.domain.food.entity.Food;
import org.springframework.data.domain.Page;

import java.util.List;

// 음식 검색 한 페이지
public record FoodPageResponse(
        List<FoodResponse> items,   // 결과가 없으면 []
        int page,                   // 0부터
        int size,
        long totalElements,         // 전체 검색 결과 수
        boolean hasNext) {

    public static FoodPageResponse from(Page<Food> page) {
        return new FoodPageResponse(
                page.getContent().stream().map(FoodResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.hasNext());
    }
}
