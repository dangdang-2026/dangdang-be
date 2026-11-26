package com.dangdang.domain.food.dto.response;

import com.dangdang.domain.food.entity.Food;
import com.dangdang.domain.food.enums.FoodOrigin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

// 음식 하나. 영양값은 1인분 기준 (기준량당 값 × 1인분 양 ÷ 100, 소수 첫째 자리)
// 1인분 양을 모르면 servingG는 null이고, 영양값은 기준량(100g·100ml)당 그대로 줌
// 모르는 영양값은 null (0으로 채우지 않음)
public record FoodResponse(
        String foodCode,
        String name,                // 식약처 원래 이름 (볶음밥_김치). 매핑·AI 연결용
        String displayName,         // 화면에 보여 줄 이름 (김치 볶음밥)
        FoodOrigin origin,          // 어디 기준 값인지 (HOME 가정식 / RESTAURANT 외식 / MEAL_SERVICE 급식 / FRANCHISE 프랜차이즈). 같은 이름이 여러 줄일 때 구분용
        String maker,               // 업체명 (프랜차이즈만, 나머지 null)
        BigDecimal servingG,        // 1인분 양. 이름은 명세대로 servingG지만 단위는 servingUnit을 봐야 함
        String servingUnit,         // g / ml
        BigDecimal kcal,
        BigDecimal carbG,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal sugarG,
        LocalDate sourceVersion,
        boolean isStaple) {         // 주식(밥·면·빵 끼니)인지. 밥류·면·죽 분류와 빵 중 피자·버거·식빵·샌드위치 등 (케이크·도넛·쿠키는 false)

    private static final BigDecimal BASE = BigDecimal.valueOf(100);

    public static FoodResponse from(Food f) {
        BigDecimal serving = f.getServingAmount();
        return new FoodResponse(
                f.getFoodCode(),
                f.getName(),
                f.displayName(),
                FoodOrigin.fromCode(f.getOrigin()),
                f.getMaker(),
                serving,
                f.getBaseUnit(),
                perServing(f.getKcal(), serving),
                perServing(f.getCarbG(), serving),
                perServing(f.getProteinG(), serving),
                perServing(f.getFatG(), serving),
                perServing(f.getSugarG(), serving),
                f.getSourceVersion(),
                f.isStaple());
    }

    private static BigDecimal perServing(BigDecimal perBase, BigDecimal serving) {
        if (perBase == null) return null;
        BigDecimal value = serving == null ? perBase : perBase.multiply(serving).divide(BASE);
        return value.setScale(1, RoundingMode.HALF_UP);
    }
}
