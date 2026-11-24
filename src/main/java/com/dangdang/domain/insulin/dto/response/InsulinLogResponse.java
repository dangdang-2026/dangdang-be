package com.dangdang.domain.insulin.dto.response;

import com.dangdang.domain.insulin.entity.InsulinLog;
import com.dangdang.domain.insulin.enums.InsulinType;
import com.dangdang.domain.meal.enums.MealSlot;

import java.math.BigDecimal;
import java.time.Instant;

// 인슐린 기록 하나 응답 (등록·목록·수정·지난 기록 불러오기 · 오늘 화면이 같이 씀)
public record InsulinLogResponse(
        Long insulinLogId,
        InsulinType insulinType,
        MealSlot mealSlot,       // 식사 때면 BREAKFAST·LUNCH·DINNER, 자기 전이면 null
        BigDecimal units,        // 6.0 처럼 소수 한 자리로 나감
        Instant takenAt,
        Instant editedAt,        // 한 번도 안 고쳤으면 null
        Instant createdAt,
        Instant updatedAt) {

    public static InsulinLogResponse from(InsulinLog log) {
        return new InsulinLogResponse(log.getId(), log.getInsulinType(), log.getMealSlot(), log.getUnits(),
                log.getTakenAt(), log.getEditedAt(), log.getCreatedAt(), log.getUpdatedAt());
    }
}
