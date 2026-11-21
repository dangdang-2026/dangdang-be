package com.dangdang.domain.insulin.dto.response;

import com.dangdang.domain.insulin.entity.InsulinLog;
import com.dangdang.domain.insulin.enums.InsulinType;

import java.math.BigDecimal;
import java.time.Instant;

// 인슐린 기록 하나 응답 (등록·목록·수정)
public record InsulinLogResponse(
        Long insulinLogId,
        InsulinType insulinType,
        BigDecimal units,
        Instant takenAt,
        Instant createdAt,
        Instant updatedAt) {

    public static InsulinLogResponse from(InsulinLog log) {
        return new InsulinLogResponse(log.getId(), log.getInsulinType(), log.getUnits(), log.getTakenAt(),
                log.getCreatedAt(), log.getUpdatedAt());
    }
}
