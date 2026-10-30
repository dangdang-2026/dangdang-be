package com.dangdang.domain.criteria.dto.response;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.enums.MeasureSlot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CriteriaVersionResponse(
        Long criteriaVersionId,
        Integer postMealMinutes,
        List<MeasureSlot> measureSlots,
        Integer fastingTarget,
        Integer postMealTarget,
        String sourceNote,
        LocalDate effectiveFrom,
        Instant createdAt) {

    // 엔티티 -> 응답
    public static CriteriaVersionResponse from(CriteriaVersion c){
        return new CriteriaVersionResponse(
                c.getId(), c.getPostMealMinutes(), c.getMeasureSlots(),
                c.getFastingTarget(), c.getPostMealTarget(), c.getSourceNote(),
                c.getEffectiveFrom(), c.getCreatedAt());
    }
}
