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

    // careSiteId는 따로 받는다 — 지운 병원이면 서비스가 null로 넘김
    public static MealPlanResponse from(MealPlan p, Long careSiteId) {
        return new MealPlanResponse(p.getId(), careSiteId, p.getReceivedOn(), p.getMealGuide(),
                p.getSnackGuide(), p.getUpdatedAt());
    }
}
