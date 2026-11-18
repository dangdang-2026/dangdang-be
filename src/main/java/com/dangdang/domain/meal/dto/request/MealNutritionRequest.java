package com.dangdang.domain.meal.dto.request;

import com.dangdang.domain.meal.entity.MealNutrition;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

// 직접 적는 영양값 (적는 그때의 먹은 양 전체 기준, g · kcal). 모르는 칸은 안 보내면 됨
public record MealNutritionRequest(
        @DecimalMin(value = "0", message = "영양값은 0 이상이에요.")
        @DecimalMax(value = "9999.9", message = "영양값은 9999.9까지예요.")
        @Digits(integer = 4, fraction = 1, message = "영양값은 소수 첫째 자리까지예요.")
        BigDecimal kcal,

        @DecimalMin(value = "0", message = "영양값은 0 이상이에요.")
        @DecimalMax(value = "9999.9", message = "영양값은 9999.9까지예요.")
        @Digits(integer = 4, fraction = 1, message = "영양값은 소수 첫째 자리까지예요.")
        BigDecimal carbG,

        @DecimalMin(value = "0", message = "영양값은 0 이상이에요.")
        @DecimalMax(value = "9999.9", message = "영양값은 9999.9까지예요.")
        @Digits(integer = 4, fraction = 1, message = "영양값은 소수 첫째 자리까지예요.")
        BigDecimal proteinG,

        @DecimalMin(value = "0", message = "영양값은 0 이상이에요.")
        @DecimalMax(value = "9999.9", message = "영양값은 9999.9까지예요.")
        @Digits(integer = 4, fraction = 1, message = "영양값은 소수 첫째 자리까지예요.")
        BigDecimal fatG,

        @DecimalMin(value = "0", message = "영양값은 0 이상이에요.")
        @DecimalMax(value = "9999.9", message = "영양값은 9999.9까지예요.")
        @Digits(integer = 4, fraction = 1, message = "영양값은 소수 첫째 자리까지예요.")
        BigDecimal sugarG) {

    public MealNutrition toNutrition() {
        return new MealNutrition(kcal, carbG, proteinG, fatG, sugarG);
    }
}
