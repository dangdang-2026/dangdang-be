package com.dangdang.domain.food.enums;

// 음식 영양값을 어디 기준으로 쟀는지 (식약처 식품기원코드를 화면에 쓸 만큼 묶음)
// 같은 이름의 음식이 출처별로 여러 줄 있어서, 화면에서 "볶음밥_새우 · 가정식"처럼 구분할 때 씀
public enum FoodOrigin {
    HOME,          // 1 가정식
    RESTAURANT,    // 3 외식(분석) · 4 외식(재료량 기반 산출)
    MEAL_SERVICE,  // 5 초등학교 · 6 중고등학교 · 7 산업체 급식
    FRANCHISE;     // 2 외식(프랜차이즈 등 업체 제공)

    public static FoodOrigin fromCode(short code) {
        return switch (code) {
            case 1 -> HOME;
            case 2 -> FRANCHISE;
            case 3, 4 -> RESTAURANT;
            case 5, 6, 7 -> MEAL_SERVICE;
            // DB에 CHECK (origin BETWEEN 1 AND 7)를 걸어 둬서 여기 올 수 없음 (잘못된 데이터는 넣을 때 막힘)
            default -> throw new IllegalStateException("알 수 없는 식품기원코드: " + code);
        };
    }
}
