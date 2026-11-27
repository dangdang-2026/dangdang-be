package com.dangdang.domain.meal.dto.response;

import com.dangdang.domain.food.service.FoodStapleReader;
import com.dangdang.domain.meal.entity.MealItem;
import com.dangdang.domain.meal.entity.MealNutrition;
import com.dangdang.domain.meal.enums.NutritionSource;

import java.math.BigDecimal;
import java.time.Instant;

// 식사 안에 같이 보여 주는 음식 항목 (식사 상세·목록·오늘 화면·기록 통합 조회)
// 항목 API 응답(MealItemResponse)에서 식사 id·시각 칸을 뺀 모양
public record MealItemSummaryResponse(
        Long itemId,
        String foodCode,                 // 직접 적은 음식이면 null
        Long productId,                  // 상점 상품을 골랐으면 그 상품, 아니면 null
        String name,                     // 보이는 이름
        BigDecimal servings,             // 먹은 양 (인분)
        BigDecimal weightG,              // 직접 잰 먹은 양 (unit 단위), 없으면 null
        String unit,                     // g / ml
        NutritionSource nutritionSource, // DB 공공 값 / USER 직접 적은 값
        MealNutrition nutrition,         // 먹은 양 전체의 영양값 (모르는 값은 null)
        MealNutrition nutritionOverride, // 직접 적은 값만, 없으면 null
        Instant updatedAt,               // 이 항목을 마지막으로 고친 시각 (다른 기기에서 고친 「내가 잰 값」 구분용)
        boolean isStaple,                // 주식인지 (양 칩·식사 한 줄 맨 앞). 직접 적은 음식은 false
        String unitLabel) {              // servings를 부르는 말 (공기·그릇·접시·잔·줄·개·인분). 직접 적은 음식은 인분

    // isStaple·unitLabel은 음식 DB를 봐야 해서 부르는 쪽이 한 번에 읽어 넘김 (FoodStapleReader)
    public static MealItemSummaryResponse from(MealItem i, FoodStapleReader.Traits traits) {
        return new MealItemSummaryResponse(
                i.getId(), i.getFoodCode(), i.getProductId(), i.getName(), i.getServings(), i.getWeight(), i.getUnit(),
                i.getNutritionSource(), i.eatenNutrition(), i.getNutritionOverride(), i.getUpdatedAt(),
                traits.isStaple(), traits.unitLabel());
    }
}
