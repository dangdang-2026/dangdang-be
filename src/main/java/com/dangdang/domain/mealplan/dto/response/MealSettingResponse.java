package com.dangdang.domain.mealplan.dto.response;

import com.dangdang.domain.mealplan.enums.PortionUnit;
import io.swagger.v3.oas.annotations.media.Schema;

public record MealSettingResponse(
        @Schema(description = "양 적는 방식 (설정한 적 없으면 SERVING)", example = "SERVING") PortionUnit portionUnit) {
}
