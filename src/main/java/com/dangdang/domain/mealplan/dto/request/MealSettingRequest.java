package com.dangdang.domain.mealplan.dto.request;

import com.dangdang.domain.mealplan.enums.PortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record MealSettingRequest(
        @Schema(description = "양 적는 방식 SERVING(인분) · GRAM(그램)", example = "GRAM")
        @NotNull(message = "양 적는 방식을 골라 주세요.")
        PortionUnit portionUnit) {
}
