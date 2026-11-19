package com.dangdang.domain.myfood.dto.response;

import com.dangdang.domain.myfood.entity.MyFood;
import com.dangdang.domain.meal.entity.MealNutrition;
import com.dangdang.domain.myfood.enums.UsualQuantity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MyFoodResponse(
        @Schema(example = "501") Long id,
        @Schema(example = "우리 집 김치볶음밥") String name,
        @Schema(example = "D101-017070000-0001") String similarFoodCode,
        @Schema(example = "ONE") UsualQuantity usualQuantity,
        @Schema(description = "보통 먹는 양 g (없으면 null)", example = "250") Integer usualGrams,
        MealNutrition nutritionOverride,
        Instant updatedAt) {

    public static MyFoodResponse from(MyFood f) {
        return new MyFoodResponse(f.getId(), f.getName(), f.getSimilarFoodCode(), f.getUsualQuantity(), f.getUsualGrams(),
                f.getNutritionOverride(), f.getUpdatedAt());
    }
}
