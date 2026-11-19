package com.dangdang.domain.myfood.dto.request;

import com.dangdang.domain.meal.dto.request.MealNutritionRequest;
import com.dangdang.domain.myfood.enums.UsualQuantity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

// 보낸 칸만 바뀐다. 칸을 비우려면 clear에 이름을 적는다 (예: "clear": ["nutritionOverride"])
public record MyFoodUpdateRequest(
        @Size(max = 100, message = "음식 이름은 100자까지 적을 수 있어요.")
        @Pattern(regexp = ".*\\S.*", message = "음식 이름을 적어 주세요.")
        String name,
        @Size(max = 30) String similarFoodCode,
        UsualQuantity usualQuantity,
        @Min(value = 1, message = "그램은 1부터 2000까지 적을 수 있어요.")
        @Max(value = 2000, message = "그램은 1부터 2000까지 적을 수 있어요.")
        Integer usualGrams,
        @Valid MealNutritionRequest nutritionOverride,
        @Schema(description = "비울 칸: similarFoodCode · usualQuantity · usualGrams · nutritionOverride", example = "[\"nutritionOverride\"]")
        List<@Pattern(regexp = "similarFoodCode|usualQuantity|usualGrams|nutritionOverride", message = "비울 수 없는 칸이에요.") String> clear) {

    public boolean isEmpty() {
        return name == null && similarFoodCode == null && usualQuantity == null && usualGrams == null
                && nutritionOverride == null
                && (clear == null || clear.isEmpty());
    }

    public boolean clears(String field) {
        return clear != null && clear.contains(field);
    }

    // 같은 칸에 값과 clear를 같이 보냈는지 (어느 쪽이 맞는지 알 수 없어서 400)
    public boolean hasValueAndClear() {
        return (similarFoodCode != null && clears("similarFoodCode"))
                || (usualQuantity != null && clears("usualQuantity"))
                || (usualGrams != null && clears("usualGrams"))
                || (nutritionOverride != null && clears("nutritionOverride"));
    }
}
