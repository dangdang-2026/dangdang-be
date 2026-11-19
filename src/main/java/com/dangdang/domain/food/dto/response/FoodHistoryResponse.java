package com.dangdang.domain.food.dto.response;

import com.dangdang.domain.meal.dto.response.MealItemSummaryResponse;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.reading.dto.response.ReadingResponse;

import java.time.Instant;
import java.util.List;

// 같은 음식 지난 기록 하나 = 그 음식을 먹은 식사 하나
// 한 식사에 같은 음식을 여러 번 적었어도 식사 1개로 묶고, 그 음식 항목들은 items에 모두 담음
public record FoodHistoryResponse(
        Long mealId,
        Instant startedAt,                    // 식사 시작 시각
        MealSlot mealSlot,
        List<MealItemSummaryResponse> items,  // 이 식사에서 이 음식을 먹은 항목들 (먹은 양·영양값, 추가한 순서)
        List<ReadingResponse> readings) {     // 이 식사에 연결된 혈당 (readings.meal_id로만, 잰 시각 순). 없으면 []

    public static FoodHistoryResponse of(Meal meal, List<MealItemSummaryResponse> items, List<ReadingResponse> readings) {
        return new FoodHistoryResponse(meal.getId(), meal.getStartedAt(), meal.getMealSlot(), items, readings);
    }
}
