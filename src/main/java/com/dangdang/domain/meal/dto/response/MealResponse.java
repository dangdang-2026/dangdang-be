package com.dangdang.domain.meal.dto.response;

import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.enums.MealSlot;

import java.time.Instant;

// 식사 하나 응답
public record MealResponse(
        Long mealId,
        MealSlot mealSlot,
        Instant startedAt,
        boolean startedOffline,
        String note,
        Instant createdAt,
        Instant updatedAt) {

    // 엔티티 -> 응답
    public static MealResponse from(Meal m) {
        return new MealResponse(
                m.getId(), m.getMealSlot(), m.getStartedAt(), m.isStartedOffline(), m.getNote(),
                m.getCreatedAt(), m.getUpdatedAt());
    }
}
