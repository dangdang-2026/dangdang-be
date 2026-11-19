package com.dangdang.domain.myfood.dto.request;

import com.dangdang.domain.meal.dto.request.MealNutritionRequest;
import com.dangdang.domain.myfood.enums.UsualQuantity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MyFoodCreateRequest(
        @Schema(example = "우리 집 김치볶음밥")
        @NotBlank(message = "음식 이름을 적어 주세요.") @Size(max = 100, message = "음식 이름은 100자까지 적을 수 있어요.")
        String name,
        @Schema(description = "비슷한 공공 음식 코드 (선택, GET /api/foods 의 code)", example = "D101-017070000-0001")
        @Size(max = 30, message = "음식 코드를 다시 확인해 주세요.")
        String similarFoodCode,
        @Schema(description = "자주 먹는 양 HALF · ONE · ONE_HALF (선택)", example = "ONE")
        UsualQuantity usualQuantity,
        @Schema(description = "보통 먹는 양 g (선택, 1~2000)", example = "250")
        @Min(value = 1, message = "그램은 1부터 2000까지 적을 수 있어요.")
        @Max(value = 2000, message = "그램은 1부터 2000까지 적을 수 있어요.")
        Integer usualGrams,
        @Schema(description = "내가 입력한 1인분 영양값 (선택, 보내면 한 칸 이상)")
        @Valid MealNutritionRequest nutritionOverride) {
}
