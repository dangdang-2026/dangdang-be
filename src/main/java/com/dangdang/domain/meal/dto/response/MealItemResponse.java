package com.dangdang.domain.meal.dto.response;

import com.dangdang.domain.meal.entity.MealItem;
import com.dangdang.domain.meal.entity.MealNutrition;
import com.dangdang.domain.meal.enums.NutritionSource;

import java.math.BigDecimal;
import java.time.Instant;

// 식사 항목 하나 응답
public record MealItemResponse(
        Long itemId,
        Long mealId,
        String foodCode,                 // 직접 적은 음식이면 null
        String name,                     // 보이는 이름
        BigDecimal servings,             // 먹은 양 (인분)
        BigDecimal weightG,              // 직접 잰 먹은 양 (unit 단위), 없으면 null
        String unit,                     // g / ml
        NutritionSource nutritionSource, // DB 공공 값 / USER 직접 적은 값
        MealNutrition nutrition,         // 먹은 양 전체의 영양값 (모르는 값은 null)
        Instant createdAt,
        Instant updatedAt) {

    public static MealItemResponse from(MealItem i) {
        return new MealItemResponse(
                i.getId(), i.getMeal().getId(), i.getFoodCode(), i.getName(),
                i.getServings(), i.getWeight(), i.getUnit(), i.getNutritionSource(),
                i.eatenNutrition(), i.getCreatedAt(), i.getUpdatedAt());
    }
}
