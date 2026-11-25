package com.dangdang.domain.meal.dto.response;

import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.enums.MealSlot;

import java.time.Instant;
import java.util.List;

// 식사 하나 응답
public record MealResponse(
        Long mealId,
        MealSlot mealSlot,
        Instant startedAt,
        boolean startedOffline,
        String note,
        List<MealItemSummaryResponse> items,   // 먹은 음식 (추가한 순서, 지운 항목 빼고, 없으면 [])
        Instant measureSkippedAt,              // H-05 건너뛴 시각. null = 안 건너뜀 → 히어로가 「시간 지났어요」를 안 띄움
        Instant createdAt,
        Instant updatedAt) {

    // 엔티티 + 그 식사의 항목 -> 응답
    public static MealResponse from(Meal m, List<MealItemSummaryResponse> items) {
        return new MealResponse(
                m.getId(), m.getMealSlot(), m.getStartedAt(), m.isStartedOffline(), m.getNote(),
                items, m.getMeasureSkippedAt(), m.getCreatedAt(), m.getUpdatedAt());
    }
}
