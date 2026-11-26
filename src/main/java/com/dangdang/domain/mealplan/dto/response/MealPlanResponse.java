package com.dangdang.domain.mealplan.dto.response;

import com.dangdang.domain.mealplan.entity.MealPlan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

public record MealPlanResponse(
        @Schema(example = "601") Long id,
        @Schema(description = "받은 병원 번호. 지운 병원이면 null (눌러도 갈 곳이 없어서)", example = "12") Long careSiteId,
        @Schema(description = "받은 병원 이름. 병원을 지워도 남는다 (C-20). 병원을 안 골랐으면 null", example = "○○대학교병원")
        String careSiteName,
        @Schema(description = "받은 곳을 글로 적은 것 (병원 목록에 없는 곳). 없으면 null", example = "○○보건소 영양 교실")
        String placeName,
        LocalDate receivedOn,
        String mealGuide,
        String snackGuide,
        Instant updatedAt) {

    // careSiteId·careSiteName은 따로 받는다 — 지운 병원이면 서비스가 번호만 null로 넘김
    public static MealPlanResponse from(MealPlan p, Long careSiteId, String careSiteName) {
        return new MealPlanResponse(p.getId(), careSiteId, careSiteName, p.getPlaceName(), p.getReceivedOn(), p.getMealGuide(),
                p.getSnackGuide(), p.getUpdatedAt());
    }
}
