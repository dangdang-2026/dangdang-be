package com.dangdang.domain.meal.dto.response;

import com.dangdang.domain.food.service.FoodStapleReader;
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
        Long productId,                  // 상점 상품을 골랐으면 그 상품, 아니면 null
        String name,                     // 보이는 이름
        BigDecimal servings,             // 먹은 양 (인분)
        BigDecimal weightG,              // 직접 잰 먹은 양 (unit 단위), 없으면 null
        String unit,                     // g / ml
        NutritionSource nutritionSource, // DB 공공 값 / USER 직접 적은 값
        MealNutrition nutrition,         // 먹은 양 전체의 영양값 (모르는 값은 null)
        MealNutrition nutritionOverride, // 사용자가 직접 적은 값만 (어느 칸을 고쳤는지 표시용). 없으면 null
        Instant createdAt,
        Instant updatedAt,
        Instant mealUpdatedAt,           // 바뀐 뒤 식사의 수정 시각. 다음 영양값 직접 입력의 expectedUpdatedAt으로 씀
        boolean isStaple,                // 주식인지 (식사 응답 items[]와 같은 값). 직접 적은 음식은 false
        String unitLabel) {              // servings를 부르는 말 (공기·그릇·접시·잔·줄·개·인분). 직접 적은 음식은 인분

    public static MealItemResponse from(MealItem i, Instant mealUpdatedAt, FoodStapleReader.Traits traits) {
        return new MealItemResponse(
                i.getId(), i.getMeal().getId(), i.getFoodCode(), i.getProductId(), i.getName(),
                i.getServings(), i.getWeight(), i.getUnit(), i.getNutritionSource(),
                i.eatenNutrition(), i.getNutritionOverride(), i.getCreatedAt(), i.getUpdatedAt(), mealUpdatedAt,
                traits.isStaple(), traits.unitLabel());
    }
}
