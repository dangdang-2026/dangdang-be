package com.dangdang.domain.meal.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

// 영양값 묶음 (g · kcal). 모르는 값은 null (0과 다름)
// 식사 항목의 직접 입력값(nutrition_override JSONB)과 응답의 먹은 영양값에 같이 씀
// DB에 저장된 JSON에 모르는 칸이 있어도 읽기가 깨지지 않게 무시함
@JsonIgnoreProperties(ignoreUnknown = true)
public record MealNutrition(
        BigDecimal kcal,
        BigDecimal carbG,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal sugarG) {

    // 하나도 안 적었는지. JSON에 "empty" 칸으로 들어가지 않게 뺌
    @JsonIgnore
    public boolean isEmpty() {
        return kcal == null && carbG == null && proteinG == null && fatG == null && sugarG == null;
    }
}
