package com.dangdang.domain.meal.entity;

import java.math.BigDecimal;

// 영양값 묶음 (g · kcal). 모르는 값은 null (0과 다름)
// 식사 항목의 직접 입력값(nutrition_override JSONB)과 응답의 먹은 영양값에 같이 씀
public record MealNutrition(
        BigDecimal kcal,
        BigDecimal carbG,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal sugarG) {

    // 하나도 안 적었는지
    public boolean isEmpty() {
        return kcal == null && carbG == null && proteinG == null && fatG == null && sugarG == null;
    }
}
