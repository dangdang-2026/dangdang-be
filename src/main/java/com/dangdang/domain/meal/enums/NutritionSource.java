package com.dangdang.domain.meal.enums;

// 식사 항목 영양값이 어디서 왔는지
public enum NutritionSource {
    DB,     // 식약처 공공 값 (고른 음식의 값을 복사해 둔 것)
    USER    // 사용자가 직접 적은 값 (직접 적은 음식이거나, 공공 값을 고친 것)
}
