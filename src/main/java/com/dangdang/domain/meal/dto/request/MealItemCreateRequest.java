package com.dangdang.domain.meal.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// 식사 항목 추가 요청
// 식약처 음식을 고르면 foodCode, 직접 적으면 name (둘 중 하나만)
public record MealItemCreateRequest(
        @Size(max = 30, message = "식품코드는 30자까지예요.")
        String foodCode,

        @Size(max = 100, message = "음식 이름은 100자까지예요.")
        String name,

        @DecimalMin(value = "0", inclusive = false, message = "먹은 양(인분)은 0보다 커야 해요.")
        @DecimalMax(value = "20", message = "먹은 양(인분)은 20인분까지예요.")
        @Digits(integer = 2, fraction = 2, message = "먹은 양(인분)은 소수 둘째 자리까지예요.")
        BigDecimal servings,         // 안 보내면 1인분

        @DecimalMin(value = "0", inclusive = false, message = "먹은 무게는 0보다 커야 해요.")
        @DecimalMax(value = "9999.9", message = "먹은 무게는 9999.9까지예요.")
        @Digits(integer = 4, fraction = 1, message = "먹은 무게는 소수 첫째 자리까지예요.")
        BigDecimal weightG,          // 직접 잰 무게 (음료는 ml). 보내면 servings 대신 씀

        @Valid
        MealNutritionRequest nutrition) {   // 직접 적은 음식의 영양값 (먹은 양 전체 기준). foodCode와 같이 못 보냄

    // 안 보낸 경우 1인분
    public BigDecimal servingsOrDefault() {
        return servings == null ? BigDecimal.ONE : servings;
    }

    // 앞뒤 공백 뺀 이름 (빈 이름은 안 보낸 것으로 봄)
    public String trimmedName() {
        return name == null || name.isBlank() ? null : name.trim();
    }

    public String trimmedFoodCode() {
        return foodCode == null || foodCode.isBlank() ? null : foodCode.trim();
    }
}
