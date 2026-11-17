package com.dangdang.domain.mealplan.dto.response;

import com.dangdang.domain.mealplan.entity.MealPlan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

public record MealPlanResponse(
        @Schema(example = "601") Long id,
        Long careSiteId,
        LocalDate receivedOn,
        String mealGuide,
        String snackGuide,
        Instant updatedAt) {

    public static MealPlanResponse from(MealPlan p) {
        return new MealPlanResponse(p.getId(), p.getCareSiteId(), p.getReceivedOn(), p.getMealGuide(),
                p.getSnackGuide(), p.getUpdatedAt());
    }
}
